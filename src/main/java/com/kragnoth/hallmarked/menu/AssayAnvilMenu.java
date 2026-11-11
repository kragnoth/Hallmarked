package com.kragnoth.hallmarked.menu;

import com.kragnoth.hallmarked.block.AssayAnvilBlockEntity;
import com.kragnoth.hallmarked.api.event.ReforgeEvent;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class AssayAnvilMenu extends AbstractContainerMenu {
    public static final int EQUIP_X = 56, EQUIP_Y = 23;
    public static final int MAT_X = 56, MAT_Y = 49;

    private final ContainerLevelAccess access;
    private final IItemHandler items;
    /** [0] = 1 when the server says reforging is possible. Server-authoritative because the config is common-side. */
    private final ContainerData data;
    private final Level level;

    /** Client constructor. */
    public AssayAnvilMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new ItemStackHandler(2) {
            @Override public int getSlotLimit(int slot) { return slot == 0 ? 1 : 64; }
        }, new SimpleContainerData(1));
    }

    /** Server constructor. */
    public AssayAnvilMenu(int id, Inventory inv, AssayAnvilBlockEntity be) {
        this(id, inv, be.getBlockPos(), be.items(), new SimpleContainerData(1));
    }

    private AssayAnvilMenu(int id, Inventory inv, BlockPos pos, IItemHandler items, ContainerData data) {
        super(HMRegistries.ASSAY_ANVIL_MENU.get(), id);
        this.level = inv.player.level();
        this.items = items;
        this.data = data;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        addSlot(new SlotItemHandler(items, AssayAnvilBlockEntity.SLOT_EQUIPMENT, EQUIP_X, EQUIP_Y));
        addSlot(new SlotItemHandler(items, AssayAnvilBlockEntity.SLOT_MATERIAL, MAT_X, MAT_Y));

        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col, 8 + col * 18, 142));

        addDataSlots(data);
    }

    public boolean canReforge() {
        return data.get(0) == 1;
    }

    public ItemStack equipment() {
        return items.getStackInSlot(AssayAnvilBlockEntity.SLOT_EQUIPMENT);
    }

    @Override
    public void broadcastChanges() {
        // Recompute availability on the server before syncing it to the client.
        if (!level.isClientSide) {
            data.set(0, Reforging.canReforge(level, items.getStackInSlot(0), items.getStackInSlot(1)) ? 1 : 0);
        }
        super.broadcastChanges();
    }

    /** Server side: validate, consume one material, roll a new quality. */
    public void tryReforge(Player player) {
        access.execute((level, p) -> {
            ItemStack equip = items.getStackInSlot(0);
            ItemStack mat = items.getStackInSlot(1);
            int cost = Reforging.materialCost(level, equip, mat);
            if (cost <= 0 || mat.getCount() < cost) return;

            Quality rolled = Reforging.roll(equip, level.getRandom());
            if (rolled == null) return;
            ReforgeEvent event = NeoForge.EVENT_BUS.post(new ReforgeEvent(player, equip.copy(), mat.copy(), cost, rolled));
            if (event.isCanceled() || event.getQuality() == null) return;

            ItemStack result = equip.copy();
            result.set(HMRegistries.QUALITY.get(), event.getQuality().name());
            if (event.getCost() > 0) items.extractItem(1, Math.min(event.getCost(), mat.getCount()), false);
            setEquipment(result);
            level.playSound(null, p, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            broadcastChanges();
        });
    }

    private void setEquipment(ItemStack stack) {
        if (items instanceof ItemStackHandler h) h.setStackInSlot(0, stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, HMRegistries.ASSAY_ANVIL.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = 2, playerEnd = slots.size();

        if (index < playerStart) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) return ItemStack.EMPTY;
        } else {
            // Equipment first (if the slot is empty), otherwise treat it as material.
            boolean moved = Reforging.isEligible(stack) && moveItemStackTo(stack, 0, 1, false);
            if (!moved && !moveItemStackTo(stack, 1, 2, false)) {
                int hotbarStart = playerEnd - 9;
                if (index < hotbarStart) {
                    if (!moveItemStackTo(stack, hotbarStart, playerEnd, false)) return ItemStack.EMPTY;
                } else if (!moveItemStackTo(stack, playerStart, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
