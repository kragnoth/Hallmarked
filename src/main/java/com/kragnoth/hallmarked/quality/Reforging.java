package com.kragnoth.hallmarked.quality;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/** Rolling, validation and item-state helpers. */
public final class Reforging {
    private Reforging() {}

    @Nullable
    public static Quality qualityOf(ItemStack stack) {
        return Qualities.get(stack.get(HMRegistries.QUALITY.get()));
    }

    public static boolean isBlacklisted(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        for (String s : HallmarkedConfig.BLACKLIST.get()) {
            if (s.trim().equals(id)) return true;
        }
        return false;
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
        int total = 0;
        for (Quality q : pool) total += q.weight();
        if (total <= 0) return null;
        int pick = random.nextInt(total);
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

    /** Material check: config override pair, the item's own repair material, or the global fallback. */
    public static boolean isValidMaterial(ItemStack equipment, ItemStack material) {
        if (equipment.isEmpty() || material.isEmpty()) return false;
        String eq = BuiltInRegistries.ITEM.getKey(equipment.getItem()).toString();
        String mat = BuiltInRegistries.ITEM.getKey(material.getItem()).toString();
        for (String pair : HallmarkedConfig.MATERIAL_OVERRIDES.get()) {
            String[] kv = pair.split(",");
            if (kv.length == 2 && kv[0].trim().equals(eq) && kv[1].trim().equals(mat)) return true;
        }
        if (equipment.getItem().isValidRepairItem(equipment, material)) return true;
        ResourceLocation fallback = ResourceLocation.tryParse(HallmarkedConfig.FALLBACK_MATERIAL.get().trim());
        return fallback != null && fallback.toString().equals(mat);
    }

    public static boolean canReforge(ItemStack equipment, ItemStack material) {
        return isEligible(equipment) && isValidMaterial(equipment, material);
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
