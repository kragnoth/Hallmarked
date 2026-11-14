package com.kragnoth.hallmarked.loot;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Grading for loot tables. Runs when a table finishes rolling: after the NeoForge global loot modifiers (which run in
 * no fixed order) and after LootJS's modifiers, so gear any of them added is graded as loot too. A
 * {@code hallmarked:grade_loot} modifier a pack adds (a custom source such as "boss") always runs before this and wins.
 */
public final class LootGrading {
    private LootGrading() {}

    /** Called from the LootTable mixin with the finished roll. */
    public static void finish(List<ItemStack> loot, LootContext ctx, @Nullable ResourceLocation table) {
        if (!HallmarkedConfig.GRADING.get() || !HallmarkedConfig.LOOT_TABLES.get()) return;
        if (table != null && isExcluded(table)) return; // tables built in code have no id: still graded
        // Some worldgen code fills containers on worker threads. Don't roll (or post events) off-thread;
        // those items are graded on pickup instead.
        var server = ctx.getLevel().getServer();
        if (server != null && !server.isSameThread()) return;
        grade(loot, ctx, Grading.SOURCE_LOOT);
        if (table != null) com.kragnoth.hallmarked.sets.Sets.inject(loot, table, ctx.getLuck(), ctx.getRandom(),
                ctx.getLevel().registryAccess(), context(ctx).player());
    }

    /** Grades every ungraded piece of equipment in the roll from {@code source}. */
    static void grade(List<ItemStack> loot, LootContext ctx, String source) {
        Grading.Context gctx = null;
        for (ItemStack stack : loot) {
            if (stack.isEmpty() || stack.has(HMRegistries.GRADE.get()) || !Grading.canGrade(stack)) continue;
            if (gctx == null) gctx = context(ctx);
            // Fanfare deferred to pickup: a roll can still be thrown away (Loot Integrations samples whole rolls
            // and keeps a few items, a full container drops the rest), and the player shouldn't hear about those.
            Grading.grade(stack, source, ctx.getLuck(), ctx.getRandom(), gctx, true);
        }
    }

    public static boolean isExcluded(ResourceLocation table) {
        String id = table.toString();
        for (String t : HallmarkedConfig.LOOT_EXCLUDED.get()) {
            if (t.endsWith("*") ? id.startsWith(t.substring(0, t.length() - 1)) : id.equals(t)) return true;
        }
        return false;
    }

    static Grading.Context context(LootContext ctx) {
        Player player = ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (player == null && ctx.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof Player p) player = p;
        if (player == null && ctx.getParamOrNull(LootContextParams.ATTACKING_ENTITY) instanceof Player p) player = p;
        return new Grading.Context(player, ctx.getLevel(), ctx.getParamOrNull(LootContextParams.ORIGIN), ctx.getQueriedLootTableId());
    }
}
