package com.kragnoth.hallmarked.grade;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Grade affects how long a piece lasts: its max durability is the item's own default times the grade's
 * {@code durability} multiplier. Recomputed from the item's default every time, so it never compounds and follows
 * the grade through tempering, reforging and commands. Items without a default max durability (or whose durability
 * another mod manages, like Tetra's modular items) are left alone.
 */
public final class Durability {
    private Durability() {}

    public static void apply(ItemStack stack) {
        Integer base = stack.getItem().components().get(DataComponents.MAX_DAMAGE);
        if (base == null || base <= 0 || !stack.has(DataComponents.MAX_DAMAGE)) return;
        if (stack.getItem().getClass().getName().startsWith("se.mickelus.tetra")) return;
        Grade g = Grading.gradeOf(stack);
        double mult = g == null ? 1.0 : g.durability();
        int desired = Math.max(1, (int) Math.round(base * mult));
        Integer current = stack.get(DataComponents.MAX_DAMAGE);
        if (current != null && current == desired) return;
        stack.set(DataComponents.MAX_DAMAGE, desired); // equal to the default: the patch entry is simply dropped
        if (stack.getDamageValue() >= desired) stack.setDamageValue(desired - 1);
    }
}
