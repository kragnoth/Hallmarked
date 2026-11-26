package com.kragnoth.hallmarked.gamble;

import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A Mountebank's sealed parcel. Use it to find out what you paid for. */
public class ParcelItem extends Item {
    public ParcelItem() {
        super(new Properties().stacksTo(16));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(held);
        String name = held.get(HMRegistries.PARCEL.get());
        Parcel parcel = name == null ? null : Parcel.STORE.get(name);
        if (parcel == null) {
            sp.displayClientMessage(Component.translatable("hallmarked.gamble.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(held);
        }
        Gamble.Result result = Gamble.open(parcel, sp);
        if (result == null || result.stack().isEmpty()) {
            sp.displayClientMessage(Component.translatable("hallmarked.gamble.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(held); // parcel not consumed
        }
        // Read the name before the stack leaves our hands.
        Component itemName = result.stack().getHoverName();
        held.consume(1, player);
        // Drop the reward just above the player so it falls onto them and is picked up normally. This avoids
        // Inventory.add, which can park the item in the hand slot that vanilla clears right after use() returns.
        ItemEntity drop = new ItemEntity(level, player.getX(), player.getY() + 2.2, player.getZ(), result.stack());
        drop.setDeltaMovement(0, 0, 0);
        drop.setTarget(player.getUUID()); // only the opener can pick it up
        level.addFreshEntity(drop);
        Gamble.announce(sp, result, itemName);
        return InteractionResultHolder.consume(held);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("hallmarked.parcel.lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        lines.add(Component.translatable("hallmarked.parcel.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
