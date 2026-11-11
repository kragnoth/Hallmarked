package com.kragnoth.hallmarked.inlay;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;

@EventBusSubscriber(modid = Hallmarked.MODID)
public final class InlayEvents {
    private InlayEvents() {}

    /**
     * Virtual enchantment levels: added to whatever the item really has, never written to it, gone with the inlay.
     * Both sides compute this (mining speed, tooltips), so inlay data is synced.
     */
    @SubscribeEvent
    public static void onEnchantLevel(GetEnchantmentLevelEvent event) {
        ItemStack stack = event.getStack();
        if (!stack.has(HMRegistries.INLAYS.get()) && !stack.has(HMRegistries.SEALED_INLAY.get())) return;
        List<Inlay> all = new java.util.ArrayList<>(Inlays.on(stack));
        Inlay sealed = Inlays.sealed(stack);
        if (sealed != null) all.add(sealed);
        for (Inlay inlay : all) {
            for (var e : inlay.enchantments().entrySet()) {
                ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, e.getKey());
                if (!event.isTargetting(key)) continue;
                event.getHolder(key).ifPresent(h -> {
                    var ench = event.getEnchantments();
                    ench.set(h, ench.getLevel(h) + e.getValue());
                });
            }
        }
    }

    /** A grindstone with one inlaid item (and nothing else) scrapes the inlays off, leaving enchantments alone. */
    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem(), bottom = event.getBottomItem();
        ItemStack target = !top.isEmpty() && bottom.isEmpty() ? top : top.isEmpty() && !bottom.isEmpty() ? bottom : ItemStack.EMPTY;
        if (target.isEmpty() || target.getOrDefault(HMRegistries.INLAYS.get(), List.of()).isEmpty()) return;
        event.setOutput(Inlays.strip(target));
        event.setXp(0);
    }
}
