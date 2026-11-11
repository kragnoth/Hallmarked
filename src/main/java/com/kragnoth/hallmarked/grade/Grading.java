package com.kragnoth.hallmarked.grade;

import com.kragnoth.hallmarked.api.event.GradeRollEvent;
import com.kragnoth.hallmarked.api.event.ItemGradedEvent;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

/** Rolls grades and affixes, and keeps the item's display name in step with them. */
public final class Grading {
    private Grading() {}

    public static final DataStore<Affix> AFFIXES = new DataStore<>("affixes", Affix::parse, Affix::name, Affix::source);
    public static final DataStore<Grade> GRADES = new DataStore<>("grades", Grade::parse, Grade::name, Grade::source);
    public static final DataStore<Fusion> FUSIONS = new DataStore<>("fusions", Fusion::parse, Fusion::name, Fusion::source);

    public static final String SOURCE_CRAFTED = "crafted";
    public static final String SOURCE_LOOT = "loot";

    @Nullable
    public static GradeData dataOf(ItemStack stack) {
        return stack.get(HMRegistries.GRADE.get());
    }

    @Nullable
    public static Grade gradeOf(ItemStack stack) {
        GradeData d = dataOf(stack);
        return d == null ? null : GRADES.get(d.grade());
    }

    public static boolean canGrade(ItemStack stack) {
        return !stack.isEmpty() && Equipment.categoryOf(stack) != null; // excluded tag is checked inside categoryOf
    }

    // ---- Grade selection -------------------------------------------------------------------

    /** Where and for whom a roll happens; any field may be null. */
    public record Context(@Nullable Player player, @Nullable Level level, @Nullable Vec3 pos) {
        public static final Context NONE = new Context(null, null, null);

        public static Context of(Player player) {
            return new Context(player, player.level(), player.position());
        }
    }

    /**
     * Weighted pick. Luck multiplies each grade's weight by (1 + luck * LuckFactor * luck_scaling), floored at 10%,
     * then {@link GradeRollEvent} listeners may adjust the weights.
     */
    @Nullable
    public static Grade pickGrade(ItemStack stack, String source, double luck, RandomSource random, Context ctx) {
        double factor = HallmarkedConfig.LUCK_FACTOR.get();
        List<Grade> grades = new ArrayList<>(GRADES.all());
        Map<Grade, Double> base = new LinkedHashMap<>();
        for (Grade g : grades) {
            double mult = Math.max(0.1, 1.0 + luck * factor * g.luckScaling());
            base.put(g, g.weight(source) * mult);
        }
        GradeRollEvent event = NeoForge.EVENT_BUS.post(
                new GradeRollEvent(stack.copy(), source, luck, ctx.player(), ctx.level(), ctx.pos(), base));
        double[] w = new double[grades.size()];
        double total = 0;
        for (int i = 0; i < grades.size(); i++) {
            w[i] = event.getWeight(grades.get(i).name());
            total += w[i];
        }
        if (total <= 0) return null;
        double pick = random.nextDouble() * total;
        for (int i = 0; i < grades.size(); i++) {
            pick -= w[i];
            if (pick < 0) return grades.get(i);
        }
        return grades.get(grades.size() - 1);
    }

    // ---- Affix rolling ---------------------------------------------------------------------

    private static List<Affix> pool(ItemStack stack, Affix.Type type) {
        List<Affix> out = new ArrayList<>();
        for (Affix a : AFFIXES.all()) {
            if (a.type() == type && a.weight() > 0 && Equipment.applied(a, stack) != null) out.add(a);
        }
        return out;
    }

    @Nullable
    private static Affix pickFrom(List<Affix> pool, Set<String> usedGroups, RandomSource random) {
        long total = 0;
        for (Affix a : pool) if (!usedGroups.contains(a.group())) total += a.weight();
        if (total <= 0) return null;
        long pick = (long) (random.nextDouble() * total);
        for (Affix a : pool) {
            if (usedGroups.contains(a.group())) continue;
            pick -= a.weight();
            if (pick < 0) return a;
        }
        return null;
    }

    public static List<GradeData.Rolled> rollAffixes(ItemStack stack, Grade grade, RandomSource random) {
        List<GradeData.Rolled> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        float span = grade.rollMax() - grade.rollMin();

        if (grade.flaws() > 0) {
            List<Affix> flaws = pool(stack, Affix.Type.FLAW);
            for (int i = 0; i < grade.flaws(); i++) {
                Affix a = pickFrom(flaws, used, random);
                if (a == null) break;
                used.add(a.group());
                out.add(new GradeData.Rolled(a.name(), grade.rollMin() + random.nextFloat() * span));
            }
            return out;
        }

        int count = grade.minAffixes() + (grade.maxAffixes() > grade.minAffixes()
                ? random.nextInt(grade.maxAffixes() - grade.minAffixes() + 1) : 0);
        List<Affix> prefixes = pool(stack, Affix.Type.PREFIX);
        List<Affix> suffixes = pool(stack, Affix.Type.SUFFIX);
        int p = 0, s = 0;
        for (int i = 0; i < count; i++) {
            boolean canP = p < grade.maxPrefixes() && pickFrom(prefixes, used, random) != null;
            boolean canS = s < grade.maxSuffixes() && pickFrom(suffixes, used, random) != null;
            if (!canP && !canS) break;
            boolean takePrefix = canP && (!canS || random.nextBoolean());
            Affix a = pickFrom(takePrefix ? prefixes : suffixes, used, random);
            if (a == null) break;
            used.add(a.group());
            if (takePrefix) p++; else s++;
            out.add(new GradeData.Rolled(a.name(), grade.rollMin() + random.nextFloat() * span));
        }
        // Prefixes first, then suffixes: reads naturally in the tooltip and name.
        out.sort((x, y) -> Integer.compare(typeOrder(x), typeOrder(y)));
        return out;
    }

    private static int typeOrder(GradeData.Rolled r) {
        Affix a = AFFIXES.get(r.affix());
        return a == null ? 3 : a.type().ordinal();
    }

    /** Rolls and stamps a grade (plus affixes and title) onto the stack. */
    public static boolean grade(ItemStack stack, String source, double luck, RandomSource random, Context ctx) {
        Grade g = pickGrade(stack, source, luck, random, ctx);
        if (g == null) return false;
        GradeData rolled = rollData(stack, g, random);
        ItemGradedEvent event = NeoForge.EVENT_BUS.post(new ItemGradedEvent(stack.copy(), source, ctx.player(), ctx.level(), rolled));
        GradeData result = event.getResult() != null ? event.getResult() : rolled; // never leave an item ungraded (would re-roll forever)
        stack.set(HMRegistries.GRADE.get(), result);
        refreshName(stack);
        return true;
    }

    /** Rolls affixes and title words for a given grade without stamping anything. */
    public static GradeData rollData(ItemStack stack, Grade g, RandomSource random) {
        List<GradeData.Rolled> affixes = rollAffixes(stack, g, random);
        String a = g.titled() ? TitleWords.randomFirst(random) : "";
        String b = g.titled() ? TitleWords.randomSecond(random) : "";
        return new GradeData(g.name(), affixes, a, b);
    }

    /** Looks up a grade by full name or short id ("masterwork" -> "hallmarked.grade.masterwork"). */
    @Nullable
    public static Grade findGrade(String nameOrId) {
        Grade g = GRADES.get(nameOrId);
        if (g != null) return g;
        g = GRADES.get("hallmarked.grade." + nameOrId);
        if (g != null) return g;
        for (Grade x : GRADES.all()) if (x.name().endsWith("." + nameOrId)) return x;
        return null;
    }

    // ---- Fusion ----------------------------------------------------------------------------

    /** A fused pair: indices into {@link GradeData#affixes()}. */
    public record Fused(Fusion fusion, int first, int second) {}

    /**
     * Pairs up rolled affixes that match a fusion. Each affix joins at most one fusion; higher priority
     * fusions claim first, then data order. Derived on demand, never stored, so fusion data can change freely.
     */
    public static List<Fused> fusions(GradeData d) {
        List<Fused> out = new ArrayList<>();
        if (d == null || d.affixes().size() < 2) return out;
        List<Fusion> all = new ArrayList<>(FUSIONS.all());
        all.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        boolean[] used = new boolean[d.affixes().size()];
        for (Fusion f : all) {
            int a = indexOf(d, f.parts().get(0), used);
            int b = indexOf(d, f.parts().get(1), used);
            if (a < 0 || b < 0 || a == b) continue;
            used[a] = true;
            used[b] = true;
            out.add(new Fused(f, Math.min(a, b), Math.max(a, b)));
        }
        return out;
    }

    private static int indexOf(GradeData d, String affix, boolean[] used) {
        for (int i = 0; i < d.affixes().size(); i++) {
            if (!used[i] && d.affixes().get(i).affix().equals(affix)) return i;
        }
        return -1;
    }

    /** Fused pair this affix index belongs to, if any. */
    @Nullable
    public static Fused fusedAt(List<Fused> fused, int index) {
        for (Fused f : fused) if (f.first() == index || f.second() == index) return f;
        return null;
    }

    // ---- Names -----------------------------------------------------------------------------

    /**
     * The name a graded item should carry, or null to leave the vanilla name alone.
     * Titled grades: "Ashen Vow". Otherwise: "[Prefix] Item [Suffix]" from the first prefix/flaw and first suffix.
     */
    @Nullable
    public static Component desiredName(ItemStack stack) {
        GradeData d = dataOf(stack);
        Grade g = d == null ? null : GRADES.get(d.grade());
        if (g == null) return tintedName(stack, findGrade("common"));

        List<Fused> fused = fusions(d);

        if (g.titled() && d.titled()) {
            // A fusion's title noun replaces the second word, so the name says what the piece does.
            // Words are passed as plain Strings, not Component.literal: literal args collapse to strings on save,
            // so a Component would never compare equal after a reload and the name would be rewritten every scan.
            Object second = d.titleSecond();
            for (Fused f : fused) {
                if (f.fusion().title() != null) {
                    second = Component.translatable(f.fusion().title());
                    break;
                }
            }
            return Component.translatable("hallmarked.name.title", d.titleFirst(), second).withStyle(g.color());
        }

        // Display units in roll order: a fusion stands in for both its parts.
        String prefix = null, suffix = null;
        for (int i = 0; i < d.affixes().size(); i++) {
            Fused f = fusedAt(fused, i);
            String key;
            Affix.Type type;
            if (f != null) {
                if (f.first() != i) continue; // count each fusion once
                key = f.fusion().name();
                type = f.fusion().type();
            } else {
                Affix a = AFFIXES.get(d.affixes().get(i).affix());
                if (a == null) continue;
                key = a.name();
                type = a.type();
            }
            if (prefix == null && (type == Affix.Type.PREFIX || type == Affix.Type.FLAW)) prefix = key;
            else if (suffix == null && type == Affix.Type.SUFFIX) suffix = key;
        }
        if (prefix == null && suffix == null) return tintedName(stack, g);

        MutableComponent name = baseName(stack).copy();
        if (prefix != null) name = Component.translatable("hallmarked.name.prefixed", Component.translatable(prefix), name);
        if (suffix != null) name = Component.translatable("hallmarked.name.suffixed", name, Component.translatable(suffix));
        return name.withStyle(g.color());
    }

    /** Plain-named items with inlays get their grade's inlay tint (Common: copper), so homebrew work stands out. */
    @Nullable
    private static Component tintedName(ItemStack stack, @Nullable Grade g) {
        if (g == null || g.inlayTint() == null) return null;
        if (stack.getOrDefault(HMRegistries.INLAYS.get(), List.of()).isEmpty() && !stack.has(HMRegistries.SEALED_INLAY.get())) return null;
        return Component.translatable("hallmarked.name.tinted", baseName(stack))
                .withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(g.inlayTint()));
    }

    /** Keeps ITEM_NAME in step with the grade and the current base item (e.g. after a netherite upgrade). */
    public static void refreshName(ItemStack stack) {
        captureBaseName(stack);
        Component want = desiredName(stack);
        Component current = stack.get(DataComponents.ITEM_NAME);
        if (want == null) {
            // Grade gone or no longer named (e.g. data changed on /reload): drop a name we generated, keep anyone else's.
            if (isOurName(current)) {
                Component base = stack.get(HMRegistries.BASE_NAME.get());
                stack.set(DataComponents.ITEM_NAME, base != null ? base : stack.getItem().components().get(DataComponents.ITEM_NAME));
                stack.remove(HMRegistries.BASE_NAME.get());
            }
            return;
        }
        if (!want.equals(current)) stack.set(DataComponents.ITEM_NAME, want);
    }

    /**
     * The name to build on: a non-Hallmarked item_name another mod or loot table gave this stack, else the item's
     * own default name (which follows the item, e.g. through a netherite upgrade).
     */
    public static Component baseName(ItemStack stack) {
        Component base = stack.get(HMRegistries.BASE_NAME.get());
        if (base != null) return base;
        Component proto = stack.getItem().components().get(DataComponents.ITEM_NAME);
        return proto != null ? proto : Component.translatable(stack.getItem().getDescriptionId());
    }

    /** Before our first rename, stash any item_name that was patched onto this stack by someone else. */
    private static void captureBaseName(ItemStack stack) {
        if (stack.has(HMRegistries.BASE_NAME.get())) return;
        var patched = stack.getComponentsPatch().get(DataComponents.ITEM_NAME);
        if (patched == null || patched.isEmpty()) return;
        Component c = patched.get();
        if (!isOurName(c)) stack.set(HMRegistries.BASE_NAME.get(), c);
    }

    private static boolean isOurName(@Nullable Component c) {
        return c != null && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                && t.getKey().startsWith("hallmarked.name.");
    }
}
