package com.kragnoth.hallmarked.loot;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Grades equipment as loot tables generate it (chests, mob drops, fishing, archaeology) using the
 * "loot" weights and the looting player's Luck. Found gear rolls better than crafted gear.
 */
public class GradeLootModifier extends LootModifier {
    public static final MapCodec<GradeLootModifier> CODEC = RecordCodecBuilder.mapCodec(
            inst -> codecStart(inst).apply(inst, GradeLootModifier::new));

    public GradeLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!HallmarkedConfig.GRADING.get()) return loot;
        for (ItemStack stack : loot) {
            if (stack.has(HMRegistries.GRADE.get()) || !Grading.canGrade(stack)) continue;
            Grading.grade(stack, Grading.SOURCE_LOOT, ctx.getLuck(), ctx.getRandom());
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
