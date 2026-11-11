package com.kragnoth.hallmarked.gamble;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.kragnoth.hallmarked.sets.Sets;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Trading set pieces in at a Mountebank's Coffer: sneak and right-click the coffer with a piece in your main hand.
 * Every {@code TradeInPieces} pieces (1 by default) pays out a Mountebank's Token; two tokens buy a Sworn Parcel.
 * It's the coffer, not the villager, so a Mountebank wandering off costs you nothing, and right-clicking him always
 * just opens his trades.
 */
@EventBusSubscriber(modid = Hallmarked.MODID)
public final class TradeIn {
    private TradeIn() {}

    private static final String TALLY = "hallmarked_trade_in";

    // Sneaking with an item in hand makes vanilla skip the block's own use action, so this hooks the event instead.
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getEntity().isShiftKeyDown()) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(HMRegistries.MOUNTEBANK_COFFER.get())) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (Sets.pieceOf(held) == null) {
            sell(event, held);
            return;
        }
        // Both sides, so the client doesn't also try to use or place the held item.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Component name = held.getHoverName();
        held.shrink(1);
        int need = HallmarkedConfig.TRADE_IN.get();
        var data = player.getPersistentData();
        int tally = data.getInt(TALLY) + 1;
        var pos = event.getPos();
        if (tally >= need) {
            tally -= need;
            ItemStack token = new ItemStack(HMRegistries.MOUNTEBANK_TOKEN.get());
            if (!player.getInventory().add(token)) player.drop(token, false);
            player.displayClientMessage(Component.translatable("hallmarked.trade_in.token", name).withStyle(ChatFormatting.GREEN), true);
            player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 1.2f);
            player.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        } else {
            player.displayClientMessage(Component.translatable("hallmarked.trade_in.taken", name, tally, need).withStyle(ChatFormatting.GRAY), true);
            player.level().playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        data.putInt(TALLY, tally);
    }

    /**
     * Fencing graded gear for emeralds: the grade's {@code sell} value, +1 when its quality is all upside, scaled
     * down to half for a worn-out piece. Ungraded items and grades worth 0 are refused (the event passes through).
     */
    private static void sell(PlayerInteractEvent.RightClickBlock event, ItemStack held) {
        var grade = com.kragnoth.hallmarked.grade.Grading.gradeOf(held);
        if (grade == null || grade.sell() <= 0) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        // Only found gear: crafted gear (a stack of wooden swords) would be an emerald printer.
        if (!held.getOrDefault(HMRegistries.FOUND.get(), false)) {
            player.displayClientMessage(Component.translatable("hallmarked.coffer.crafted").withStyle(ChatFormatting.GRAY), true);
            player.level().playSound(null, event.getPos(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 0.8f);
            return;
        }

        int value = grade.sell();
        var quality = com.kragnoth.hallmarked.quality.Reforging.qualityOf(held);
        if (quality != null) {
            boolean allUp = true, any = false;
            for (var e : com.kragnoth.hallmarked.grade.Marks.of(held)) {
                if (e.origin() != com.kragnoth.hallmarked.grade.Marks.Origin.QUALITY) continue;
                any = true;
                if (e.modifier().amount() < 0) allUp = false;
            }
            if (any && allUp) value += 1;
        }
        if (held.isDamageableItem() && held.getMaxDamage() > 0) {
            double left = 1.0 - (double) held.getDamageValue() / held.getMaxDamage();
            value = Math.max(1, (int) Math.round(value * (0.5 + 0.5 * left)));
        }
        Component name = held.getHoverName();
        held.shrink(1);
        ItemStack pay = new ItemStack(net.minecraft.world.item.Items.EMERALD, value);
        if (!player.getInventory().add(pay)) player.drop(pay, false);
        player.displayClientMessage(Component.translatable("hallmarked.coffer.sold", name, value).withStyle(ChatFormatting.GREEN), true);
        player.level().playSound(null, event.getPos(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8f, 1.3f);
        player.level().playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** Keep the tally through death and returning from the End. */
    @SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        int tally = event.getOriginal().getPersistentData().getInt(TALLY);
        if (tally > 0) event.getEntity().getPersistentData().putInt(TALLY, tally);
    }
}
