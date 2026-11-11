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
        if (!stack.has(HMRegistries.INLAYS.get()) && !stack.has(HMRegistries.SEALED_INLAY.get()) && !stack.has(HMRegistries.UNIQUE.get())) return;
        List<java.util.Map<net.minecraft.resources.ResourceLocation, Integer>> sources = new java.util.ArrayList<>();
        for (Inlay inlay : Inlays.on(stack)) sources.add(inlay.enchantments());
        Inlay sealed = Inlays.sealed(stack);
        if (sealed != null) sources.add(sealed.enchantments());
        var unique = com.kragnoth.hallmarked.grade.Grading.isUnappraised(stack) ? null : com.kragnoth.hallmarked.grade.Grading.uniqueOf(stack);
        if (unique != null) sources.add(unique.enchantments());
        for (var enchants : sources) {
            for (var e : enchants.entrySet()) {
                ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, e.getKey());
                if (!event.isTargetting(key)) continue;
                event.getHolder(key).ifPresent(h -> {
                    var ench = event.getEnchantments();
                    ench.set(h, ench.getLevel(h) + e.getValue());
                });
            }
        }
    }

    /**
     * A grindstone with a single piece of equipment, in order:
     * 1. it has regular inlays: scrape them off (enchantments untouched);
     * 2. it has enchantments: vanilla disenchants as usual;
     * 3. otherwise (vanilla would output nothing): salvage it into Smith's Scrap, by grade.
     */
    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem(), bottom = event.getBottomItem();
        ItemStack target = !top.isEmpty() && bottom.isEmpty() ? top : top.isEmpty() && !bottom.isEmpty() ? bottom : ItemStack.EMPTY;
        if (target.isEmpty() || target.getCount() != 1) return;
        if (!target.getOrDefault(HMRegistries.INLAYS.get(), List.of()).isEmpty()) {
            event.setOutput(Inlays.strip(target));
            event.setXp(0);
            return;
        }
        if (net.minecraft.world.item.enchantment.EnchantmentHelper.hasAnyEnchantments(target)) return;
        if (com.kragnoth.hallmarked.quality.Equipment.categoryOf(target) == null) return;
        var grade = com.kragnoth.hallmarked.grade.Grading.gradeOf(target);
        if (grade == null) grade = com.kragnoth.hallmarked.grade.Grading.findGrade("common");
        if (grade == null || grade.salvage() <= 0) return;
        event.setOutput(new ItemStack(HMRegistries.SMITHS_SCRAP.get(), grade.salvage()));
        event.setXp(grade.rank());
    }
}
