package com.kragnoth.hallmarked.gamble;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Opening a Mountebank's parcel: everything about the contents is decided in that moment. */
public final class Gamble {
    private Gamble() {}

    public static final String SOURCE = "gamble";
    /** Base items a parcel can hold. Vanilla gear by default, so craft-only modded gear never turns up in one. */
    public static final TagKey<Item> POOL = TagKey.create(Registries.ITEM, Hallmarked.id("gamble/pool"));

    public enum Outcome { PLAIN, CURSED, EXCEPTIONAL, CURSED_EXCEPTIONAL }

    public record Result(ItemStack stack, Outcome outcome) {}

    @Nullable
    public static Result open(Parcel parcel, ServerPlayer player) {
        RandomSource random = player.getRandom();
        ItemStack stack = null;

        if (parcel.setChance() > 0 && random.nextDouble() < parcel.setChance()) {
            stack = com.kragnoth.hallmarked.sets.Sets.randomPiece(player.serverLevel().registryAccess(), random, parcel.categories(), player);
            if (stack != null) return new Result(stack, Outcome.PLAIN);
        }

        List<Item> pool = new ArrayList<>();
        for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(POOL)) {
            ItemStack probe = new ItemStack(h.value());
            Category c = Equipment.categoryOf(probe);
            if (c != null && parcel.categories().contains(c)) pool.add(h.value());
        }
        if (pool.isEmpty()) return null;
        stack = new ItemStack(pool.get(random.nextInt(pool.size())));

        // Regions don't apply (no level/pos): the odds are the parcel's, wherever it's opened.
        Grading.grade(stack, SOURCE, parcel.luck(), random, new Grading.Context(player, null, null));
        stack.remove(HMRegistries.UNAPPRAISED.get()); // the parcel was the mystery

        boolean cursed = random.nextDouble() < parcel.curseChance() && addCurse(stack, random);
        boolean exceptional = random.nextDouble() < parcel.exceptionalChance() && makeExceptional(stack, random);

        if (Reforging.isEligible(stack)) {
            if (random.nextDouble() < parcel.qualityChance()) Reforging.reforge(stack, random);
            stack.set(HMRegistries.ROLLED.get(), true);
        }
        Grading.refreshName(stack);
        com.kragnoth.hallmarked.grade.Durability.apply(stack);
        Outcome outcome = cursed && exceptional ? Outcome.CURSED_EXCEPTIONAL : cursed ? Outcome.CURSED
                : exceptional ? Outcome.EXCEPTIONAL : Outcome.PLAIN;
        return new Result(stack, outcome);
    }

    private static boolean addCurse(ItemStack stack, RandomSource random) {
        GradeData d = Grading.dataOf(stack);
        if (d == null) return false;
        List<Affix> curses = new ArrayList<>(Grading.pool(stack, Affix.Type.CURSE));
        curses.removeIf(c -> d.affixes().stream().anyMatch(r -> {
            Affix a = Grading.AFFIXES.get(r.affix());
            return a != null && a.group().equals(c.group());
        }));
        int total = 0;
        for (Affix a : curses) total += a.weight();
        if (total <= 0) return false;
        int pick = random.nextInt(total);
        Affix chosen = curses.get(curses.size() - 1);
        for (Affix a : curses) {
            pick -= a.weight();
            if (pick < 0) { chosen = a; break; }
        }
        List<GradeData.Rolled> list = new ArrayList<>();
        list.add(new GradeData.Rolled(chosen.name(), random.nextFloat())); // first, so the name leads with it
        list.addAll(d.affixes());
        stack.set(HMRegistries.GRADE.get(), new GradeData(d.grade(), list, d.titleFirst(), d.titleSecond()));
        return true;
    }

    /** At least Journeyman, and every prefix and suffix rolls 15-35 % past its normal maximum. */
    private static boolean makeExceptional(ItemStack stack, RandomSource random) {
        GradeData d = Grading.dataOf(stack);
        if (d == null) return false;
        Grade g = Grading.findGrade(d.grade());
        Grade journeyman = Grading.findGrade("hallmarked.grade.journeyman");
        if (g != null && journeyman != null && g.rank() < journeyman.rank() && !d.grade().equals(Grading.UNIQUE_GRADE)) {
            GradeData fresh = Grading.rollData(stack, journeyman, random);
            List<GradeData.Rolled> keep = new ArrayList<>(fresh.affixes());
            for (GradeData.Rolled r : d.affixes()) {
                Affix a = Grading.AFFIXES.get(r.affix());
                if (a != null && a.type() == Affix.Type.CURSE) keep.add(0, r);
            }
            d = new GradeData(fresh.grade(), keep, fresh.titleFirst(), fresh.titleSecond());
        }
        List<GradeData.Rolled> out = new ArrayList<>();
        boolean any = false;
        for (GradeData.Rolled r : d.affixes()) {
            Affix a = Grading.AFFIXES.get(r.affix());
            if (a == null || a.type().drawback()) { out.add(r); continue; }
            out.add(new GradeData.Rolled(r.affix(), Math.max(1f, r.roll()) + 0.15f + random.nextFloat() * 0.2f));
            any = true;
        }
        stack.set(HMRegistries.GRADE.get(), new GradeData(d.grade(), out, d.titleFirst(), d.titleSecond()));
        return any;
    }

    /** An affix roll past 1.0 only comes from an exceptional parcel. */
    public static boolean isExceptional(ItemStack stack) {
        GradeData d = Grading.dataOf(stack);
        if (d == null) return false;
        for (GradeData.Rolled r : d.affixes()) if (r.roll() > 1.0001f) return true;
        return false;
    }

    static void announce(ServerPlayer player, Result r) {
        Component name = r.stack().getHoverName();
        Component msg = switch (r.outcome()) {
            case PLAIN -> Component.translatable("hallmarked.gamble.opened", name);
            case CURSED -> Component.translatable("hallmarked.gamble.cursed", name).withStyle(ChatFormatting.DARK_RED);
            case EXCEPTIONAL -> Component.translatable("hallmarked.gamble.exceptional", name).withStyle(ChatFormatting.GOLD);
            case CURSED_EXCEPTIONAL -> Component.translatable("hallmarked.gamble.both", name).withStyle(ChatFormatting.DARK_PURPLE);
        };
        player.displayClientMessage(msg, true);
        var sound = switch (r.outcome()) {
            case PLAIN -> SoundEvents.BUNDLE_DROP_CONTENTS;
            case CURSED -> SoundEvents.WITCH_CELEBRATE;
            case EXCEPTIONAL -> SoundEvents.PLAYER_LEVELUP;
            case CURSED_EXCEPTIONAL -> SoundEvents.ELDER_GUARDIAN_CURSE;
        };
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8f, 1.0f);
    }
}
