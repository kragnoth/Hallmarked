package com.kragnoth.hallmarked.tempering;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Occult tempering: a risk roll that may raise a piece's grade, gift a forbidden affix, or mar it, then locks it. */
public final class Tempering {
    private Tempering() {}

    /** Server-only: outcomes never need to reach the client. */
    public static final DataStore<TemperOutcome> OUTCOMES =
            new DataStore<>("tempering", TemperOutcome::parse, TemperOutcome::name, TemperOutcome::source);
    public static final TagKey<Item> CATALYSTS = TagKey.create(Registries.ITEM, Hallmarked.id("tempering_catalysts"));

    public static boolean isTempered(ItemStack stack) {
        return stack.getOrDefault(HMRegistries.TEMPERED.get(), false);
    }

    /** Graded, appraised, not a unique, and not already tempered. */
    public static boolean canTemper(ItemStack stack) {
        return Equipment.categoryOf(stack) != null && Grading.dataOf(stack) != null && !isTempered(stack)
                && !Grading.isUnappraised(stack) && Grading.uniqueOf(stack) == null;
    }

    /** Applies one weighted outcome in place and returns it (null if no outcomes are loaded). */
    @Nullable
    public static TemperOutcome temper(ItemStack stack, RandomSource random) {
        TemperOutcome outcome = pick(random);
        if (outcome == null) return null;
        GradeData d = Grading.dataOf(stack);
        TemperOutcome.Effect effect = outcome.effect();

        if (effect == TemperOutcome.Effect.ASCEND) {
            Grade next = nextGrade(Grading.gradeOf(stack));
            if (next != null) {
                stack.set(HMRegistries.GRADE.get(), Grading.rollData(stack, next, random));
            } else {
                effect = TemperOutcome.Effect.FORBIDDEN; // already at the top: the altar gives something else
            }
        }
        if (effect == TemperOutcome.Effect.FORBIDDEN || effect == TemperOutcome.Effect.FLAW) {
            Affix.Type type = effect == TemperOutcome.Effect.FORBIDDEN ? Affix.Type.FORBIDDEN : Affix.Type.FLAW;
            Affix a = pickAffix(stack, type, random);
            GradeData cur = Grading.dataOf(stack);
            if (a != null && cur != null) {
                List<GradeData.Rolled> list = new ArrayList<>(cur.affixes());
                list.add(new GradeData.Rolled(a.name(), random.nextFloat()));
                stack.set(HMRegistries.GRADE.get(), new GradeData(cur.grade(), list, cur.titleFirst(), cur.titleSecond()));
            }
        }
        stack.set(HMRegistries.TEMPERED.get(), true);
        Grading.refreshName(stack);
        return outcome;
    }

    @Nullable
    private static TemperOutcome pick(RandomSource random) {
        long total = 0;
        for (TemperOutcome o : OUTCOMES.all()) total += o.weight();
        if (total <= 0) return null;
        long pick = (long) (random.nextDouble() * total);
        for (TemperOutcome o : OUTCOMES.all()) {
            pick -= o.weight();
            if (pick < 0) return o;
        }
        return null;
    }

    /** Lowest-ranked grade above this one that can actually roll (skips weight-0 grades such as Unique). */
    @Nullable
    private static Grade nextGrade(@Nullable Grade current) {
        if (current == null) return null;
        Grade best = null;
        for (Grade g : Grading.GRADES.all()) {
            if (g.rank() <= current.rank() || g.weights().values().stream().allMatch(w -> w <= 0)) continue;
            if (best == null || g.rank() < best.rank()) best = g;
        }
        return best;
    }

    @Nullable
    private static Affix pickAffix(ItemStack stack, Affix.Type type, RandomSource random) {
        List<Affix> pool = new ArrayList<>();
        long total = 0;
        for (Affix a : Grading.AFFIXES.all()) {
            if (a.type() == type && a.weight() > 0 && Equipment.applied(a, stack) != null && a.fits(stack)) {
                pool.add(a);
                total += a.weight();
            }
        }
        if (total <= 0) return null;
        long pick = (long) (random.nextDouble() * total);
        for (Affix a : pool) {
            pick -= a.weight();
            if (pick < 0) return a;
        }
        return null;
    }
}
