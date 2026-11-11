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

    /** Number of entries in each title word list (lang keys hallmarked.title.a.N / hallmarked.title.b.N). */
    public static final int TITLE_A = 40, TITLE_B = 40;

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
        int a = g.titled() ? random.nextInt(TITLE_A) : -1;
        int b = g.titled() ? random.nextInt(TITLE_B) : -1;
        stack.set(HMRegistries.GRADE.get(), new GradeData(g.name(), affixes, a, b));
        refreshName(stack);
        return true;
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

        if (g.titled() && d.titleA() >= 0 && d.titleB() >= 0) {
            return Component.translatable("hallmarked.name.title",
                    Component.translatable("hallmarked.title.a." + d.titleA()),
                    Component.translatable("hallmarked.title.b." + d.titleB())).withStyle(g.color());
        }

        Affix prefix = null, suffix = null;
        for (GradeData.Rolled r : d.affixes()) {
            Affix a = AFFIXES.get(r.affix());
            if (a == null) continue;
            if (prefix == null && (a.type() == Affix.Type.PREFIX || a.type() == Affix.Type.FLAW)) prefix = a;
            if (suffix == null && a.type() == Affix.Type.SUFFIX) suffix = a;
        }
        if (prefix == null && suffix == null) return null;

        MutableComponent name = Component.translatable(stack.getItem().getDescriptionId());
        if (prefix != null) name = Component.translatable("hallmarked.name.prefixed", Component.translatable(prefix.name()), name);
        if (suffix != null) name = Component.translatable("hallmarked.name.suffixed", name, Component.translatable(suffix.name()));
        return name.withStyle(g.color());
    }

    /** Keeps ITEM_NAME in step with the grade and the current base item (e.g. after a netherite upgrade). */
    public static void refreshName(ItemStack stack) {
        Component want = desiredName(stack);
        if (want == null) return;
        if (!want.equals(stack.get(DataComponents.ITEM_NAME))) stack.set(DataComponents.ITEM_NAME, want);
    }
}
