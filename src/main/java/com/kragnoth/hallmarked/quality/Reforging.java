package com.kragnoth.hallmarked.quality;

import com.kragnoth.hallmarked.recipe.ReforgeMaterialRecipe;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Rolling, validation and item-state helpers. */
public final class Reforging {
    private Reforging() {}

    @Nullable
    public static Quality qualityOf(ItemStack stack) {
        return Qualities.get(stack.get(HMRegistries.QUALITY.get()));
    }

    /** Excluded via the {@code hallmarked:equipment/excluded} tag (synced, so client and server always agree). */
    public static boolean isBlacklisted(ItemStack stack) {
        return stack.is(Equipment.EXCLUDED);
    }

    /** Every quality that could be rolled for this item, honouring the armor pooling rules. */
    public static List<Quality> pool(ItemStack stack) {
        Category cat = Equipment.categoryOf(stack);
        List<Quality> out = new ArrayList<>();
        if (cat == null) return out;
        if (cat == Category.ARMOR) {
            Category piece = Equipment.armorPiece(stack);
            for (Quality q : Qualities.all()) if (piece != null && q.has(piece)) out.add(q);
            for (Quality q : Qualities.all()) if (q.has(Category.ARMOR)) out.add(q);
        } else {
            for (Quality q : Qualities.all()) if (q.has(cat)) out.add(q);
        }
        return out;
    }

    public static boolean isEligible(ItemStack stack) {
        return !stack.isEmpty() && !isBlacklisted(stack) && !pool(stack).isEmpty();
    }

    @Nullable
    public static Quality roll(ItemStack stack, RandomSource random) {
        List<Quality> pool = pool(stack);
        long total = 0;
        for (Quality q : pool) total += q.weight();
        if (total <= 0) return null;
        long pick = (long) (random.nextDouble() * total);
        for (Quality q : pool) {
            pick -= q.weight();
            if (pick < 0) return q;
        }
        return null;
    }

    /** Rolls and stamps a new quality. Returns false if nothing could be rolled. */
    public static boolean reforge(ItemStack stack, RandomSource random) {
        Quality q = roll(stack, random);
        if (q == null) return false;
        stack.set(HMRegistries.QUALITY.get(), q.name());
        return true;
    }

    /**
     * Materials consumed to reforge {@code equipment} with {@code material}, or 0 if it isn't a valid pairing.
     * The item's own repair material always works (cost 1); otherwise {@code hallmarked:reforge_material}
     * recipes decide, preferring recipes that name the equipment over catch-all ones.
     */
    public static int materialCost(Level level, ItemStack equipment, ItemStack material) {
        if (equipment.isEmpty() || material.isEmpty() || !isEligible(equipment)) return 0;
        if (equipment.getItem().isValidRepairItem(equipment, material)) return 1;
        ReforgeMaterialRecipe.Input in = new ReforgeMaterialRecipe.Input(equipment, material);
        ReforgeMaterialRecipe best = null;
        for (RecipeHolder<ReforgeMaterialRecipe> h : level.getRecipeManager().getAllRecipesFor(HMRegistries.REFORGE_MATERIAL_TYPE.get())) {
            ReforgeMaterialRecipe r = h.value();
            if (!r.matches(in, level)) continue;
            if (best == null || (best.isCatchAll() && !r.isCatchAll())) best = r;
        }
        return best == null ? 0 : best.cost();
    }

    /** True if the anvil can act now: valid pairing and enough material in the slot. */
    public static boolean canReforge(Level level, ItemStack equipment, ItemStack material) {
        int cost = materialCost(level, equipment, material);
        return cost > 0 && material.getCount() >= cost;
    }

    /**
     * Converts state written by the original Quality Equipment mod ({@code custom_data.Reforge} /
     * {@code custom_data.Autoreforge}) into Hallmarked components. Returns true if anything changed.
     */
    public static boolean migrateLegacy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return false;
        CompoundTag tag = data.copyTag();
        if (!tag.contains("Reforge") && !tag.contains("Autoreforge")) return false;

        String legacy = tag.getString("Reforge");
        if (!legacy.isEmpty() && !stack.has(HMRegistries.QUALITY.get())) {
            Quality q = Qualities.get(legacy);
            if (q != null) stack.set(HMRegistries.QUALITY.get(), q.name());
        }
        if (tag.getBoolean("Autoreforge")) stack.set(HMRegistries.ROLLED.get(), true);

        tag.remove("Reforge");
        tag.remove("Autoreforge");
        if (tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return true;
    }
}
