package com.kragnoth.hallmarked.loot;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * A custom grade source for some loot tables: grades their equipment with the weights for {@code source} and the
 * looting player's Luck. Plain loot needs no modifier (see {@link LootGrading}, which runs after every modifier),
 * so these always win over it, in any order. Two of these matching the same table: the first to run wins, and
 * NeoForge doesn't keep the listed order, so keep their conditions from overlapping.
 *
 * <pre>{@code
 * { "type": "hallmarked:grade_loot", "source": "boss",
 *   "conditions": [ { "condition": "neoforge:loot_table_id", "loot_table_id": "minecraft:entities/wither" } ] }
 * }</pre>
 */
public class GradeLootModifier extends LootModifier {
    public static final MapCodec<GradeLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst)
            .and(Codec.STRING.optionalFieldOf("source", Grading.SOURCE_LOOT).forGetter(m -> m.source))
            .apply(inst, GradeLootModifier::new));

    private final String source;

    public GradeLootModifier(LootItemCondition[] conditions, String source) {
        super(conditions);
        this.source = source;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        if (!HallmarkedConfig.GRADING.get()) return loot;
        var table = ctx.getQueriedLootTableId();
        if (table != null && LootGrading.isExcluded(table)) return loot;
        var server = ctx.getLevel().getServer();
        if (server != null && !server.isSameThread()) return loot; // graded on pickup instead
        LootGrading.grade(loot, ctx, source);
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
