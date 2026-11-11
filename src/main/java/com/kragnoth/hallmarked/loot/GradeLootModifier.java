package com.kragnoth.hallmarked.loot;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/**
 * Grades equipment as loot tables generate it, using the weights for {@code source} and the looting
 * player's Luck. The default instance uses "loot" for everything; packs can add more instances with
 * conditions (e.g. {@code neoforge:loot_table_id}) and their own source, such as "boss" or "deep".
 * The first modifier to grade an item wins, so list specific ones before the generic one.
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
        // Some worldgen code fills containers on worker threads. Don't roll (or post events) off-thread;
        // those items are graded on pickup instead.
        var server = ctx.getLevel().getServer();
        if (server != null && !server.isSameThread()) return loot;
        for (ItemStack stack : loot) {
            if (stack.has(HMRegistries.GRADE.get()) || !Grading.canGrade(stack)) continue;
            Grading.grade(stack, source, ctx.getLuck(), ctx.getRandom(), context(ctx));
        }
        if (Grading.SOURCE_LOOT.equals(source) && ctx.getQueriedLootTableId() != null) {
            com.kragnoth.hallmarked.sets.Sets.inject(loot, ctx.getQueriedLootTableId(), ctx.getLuck(), ctx.getRandom(),
                    ctx.getLevel().registryAccess());
        }
        return loot;
    }

    private static Grading.Context context(LootContext ctx) {
        Player player = ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (player == null && ctx.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player p) player = p;
        if (player == null && ctx.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof Player p) player = p;
        return new Grading.Context(player, ctx.getLevel(), ctx.getParamOrNull(LootContextParams.ORIGIN), ctx.getQueriedLootTableId());
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
