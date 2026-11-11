package com.kragnoth.hallmarked.gamble;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A plain 9x3 chest screen (the client uses the vanilla one) whose coffer slots refuse anything that can't go inside a
 * container item: other coffers, shulker boxes and (usually) backpacks. A broken coffer carries its contents as item
 * data, so nesting would let one stack hold coffers of coffers until the item is too big to save or send.
 */
public class CofferMenu extends ChestMenu {
    public CofferMenu(int id, Inventory inv, Container coffer) {
        super(MenuType.GENERIC_9x3, id, inv, coffer, 3);
        for (int i = 0; i < coffer.getContainerSize(); i++) {
            Slot old = slots.get(i);
            Slot slot = new Slot(coffer, old.getContainerSlot(), old.x, old.y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return fits(stack);
                }
            };
            slot.index = i;
            slots.set(i, slot);
        }
    }

    public static boolean fits(ItemStack stack) {
        return stack.getItem().canFitInsideContainerItems();
    }
}
