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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handing set pieces to a Mountebank: right-click him holding one. He keeps a tally per player, and every
 * {@code TradeInPieces} pieces (1 by default) pays out a Mountebank's Token; two tokens buy a Sworn Parcel.
 */
@EventBusSubscriber(modid = Hallmarked.MODID)
public final class TradeIn {
    private TradeIn() {}

    private static final String TALLY = "hallmarked_trade_in";

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getTarget() instanceof Villager v) || v.getVillagerData().getProfession() != HMRegistries.MOUNTEBANK.get()) return;
        ItemStack held = event.getEntity().getMainHandItem();
        if (Sets.pieceOf(held) == null) return;
        // Both sides: otherwise the client opens the trading screen.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Component name = held.getHoverName();
        held.shrink(1);
        int need = HallmarkedConfig.TRADE_IN.get();
        var data = player.getPersistentData();
        int tally = data.getInt(TALLY) + 1;
        if (tally >= need) {
            tally -= need;
            ItemStack token = new ItemStack(HMRegistries.MOUNTEBANK_TOKEN.get());
            if (!player.getInventory().add(token)) player.drop(token, false);
            player.displayClientMessage(Component.translatable("hallmarked.trade_in.token").withStyle(ChatFormatting.GREEN), true);
            v.playSound(SoundEvents.VILLAGER_YES, 1.0f, 1.0f);
            player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6f, 1.2f);
        } else {
            player.displayClientMessage(Component.translatable("hallmarked.trade_in.taken", name, tally, need).withStyle(ChatFormatting.GRAY), true);
            v.playSound(SoundEvents.VILLAGER_TRADE, 1.0f, 1.0f);
        }
        data.putInt(TALLY, tally);
    }

    /** Keep the tally through death and returning from the End. */
    @SubscribeEvent
    public static void onClone(net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone event) {
        int tally = event.getOriginal().getPersistentData().getInt(TALLY);
        if (tally > 0) event.getEntity().getPersistentData().putInt(TALLY, tally);
    }
}
