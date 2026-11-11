package com.kragnoth.hallmarked.grade;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
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
        return !stack.isEmpty() && Equipment.categoryOf(stack) != null && !Reforging.isBlacklisted(stack);
    }

    // ---- Grade selection -------------------------------------------------------------------

    /** Weighted pick. Luck multiplies each grade's weight by (1 + luck * LuckFactor * luck_scaling), floored at 10%. */
    @Nullable
    public static Grade pickGrade(String source, double luck, RandomSource random) {
        double factor = HallmarkedConfig.LUCK_FACTOR.get();
        List<Grade> grades = new ArrayList<>(GRADES.all());
        double[] w = new double[grades.size()];
        double total = 0;
        for (int i = 0; i < grades.size(); i++) {
            Grade g = grades.get(i);
            double mult = Math.max(0.1, 1.0 + luck * factor * g.luckScaling());
            w[i] = g.weight(source) * mult;
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
        int total = 0;
        for (Affix a : pool) if (!usedGroups.contains(a.group())) total += a.weight();
        if (total <= 0) return null;
        int pick = random.nextInt(total);
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
    public static boolean grade(ItemStack stack, String source, double luck, RandomSource random) {
        Grade g = pickGrade(source, luck, random);
        if (g == null) return false;
        List<GradeData.Rolled> affixes = rollAffixes(stack, g, random);
        String a = g.titled() ? TitleWords.randomFirst(random) : "";
        String b = g.titled() ? TitleWords.randomSecond(random) : "";
        stack.set(HMRegistries.GRADE.get(), new GradeData(g.name(), affixes, a, b));
        refreshName(stack);
        return true;
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
        if (d == null) return null;
        Grade g = GRADES.get(d.grade());
        if (g == null) return null;

        List<Fused> fused = fusions(d);

        if (g.titled() && d.titled()) {
            // A fusion's title noun replaces the second word, so the name says what the piece does.
            Component second = Component.literal(d.titleSecond());
            for (Fused f : fused) {
                if (f.fusion().title() != null) {
                    second = Component.translatable(f.fusion().title());
                    break;
                }
            }
            return Component.translatable("hallmarked.name.title",
                    Component.literal(d.titleFirst()), second).withStyle(g.color());
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
        if (prefix == null && suffix == null) return null;

        MutableComponent name = Component.translatable(stack.getItem().getDescriptionId());
        if (prefix != null) name = Component.translatable("hallmarked.name.prefixed", Component.translatable(prefix), name);
        if (suffix != null) name = Component.translatable("hallmarked.name.suffixed", name, Component.translatable(suffix));
        return name.withStyle(g.color());
    }

    /** Keeps ITEM_NAME in step with the grade and the current base item (e.g. after a netherite upgrade). */
    public static void refreshName(ItemStack stack) {
        Component want = desiredName(stack);
        if (want == null) return;
        if (!want.equals(stack.get(DataComponents.ITEM_NAME))) stack.set(DataComponents.ITEM_NAME, want);
    }
}
