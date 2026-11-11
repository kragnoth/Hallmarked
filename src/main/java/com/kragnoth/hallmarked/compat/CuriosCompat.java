package com.kragnoth.hallmarked.compat;

import com.kragnoth.hallmarked.Hallmarked;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import top.theillusivec4.curios.api.CuriosApi;

/** Optional Curios integration. Every Curios class reference is behind {@link Hallmarked#curiosLoaded()}. */
public final class CuriosCompat {
    private CuriosCompat() {}

    /** True if the item sits in any {@code curios:*} item tag. */
    public static boolean isCurio(ItemStack stack) {
        if (!Hallmarked.curiosLoaded()) return false;
        return stack.getTags().anyMatch(t -> t.location().getNamespace().equals("curios"));
    }

    /** Stacks currently worn in Curios slots, in slot order (index = position in the list). */
    public static List<ItemStack> equipped(LivingEntity entity) {
        if (!Hallmarked.curiosLoaded()) return List.of();
        return Impl.equipped(entity);
    }

    private static final class Impl {
        static List<ItemStack> equipped(LivingEntity entity) {
            List<ItemStack> out = new ArrayList<>();
            CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
                IItemHandler h = inv.getEquippedCurios();
                for (int i = 0; i < h.getSlots(); i++) out.add(h.getStackInSlot(i));
            });
            return out;
        }
    }
}
