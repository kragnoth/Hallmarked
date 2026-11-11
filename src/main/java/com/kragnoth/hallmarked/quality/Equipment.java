package com.kragnoth.hallmarked.quality;

import com.kragnoth.hallmarked.compat.CuriosCompat;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import org.jetbrains.annotations.Nullable;

/** Decides which equipment category an item belongs to. */
public final class Equipment {
    private Equipment() {}

    // Tetra's modular items don't extend the vanilla classes, so match them by class name (no hard dependency).
    private static final Set<String> TETRA_TOOLS = Set.of(
            "se.mickelus.tetra.items.modular.ModularItem",
            "se.mickelus.tetra.items.modular.impl.ModularSingleHeadedItem",
            "se.mickelus.tetra.items.modular.impl.ModularDoubleHeadedItem",
            "se.mickelus.tetra.items.modular.impl.ModularBladedItem",
            "se.mickelus.tetra.items.modular.impl.dynamic.DynamicModularItem");
    private static final Set<String> TETRA_BOWS = Set.of(
            "se.mickelus.tetra.items.modular.impl.crossbow.ModularCrossbowItem",
            "se.mickelus.tetra.items.modular.impl.bow.ModularBowItem");
    private static final String TETRA_SHIELD = "se.mickelus.tetra.items.modular.impl.shield.ModularShieldItem";

    /** The broad category of the item, or null if it can't carry a quality. Piece-specific armor resolves to ARMOR. */
    @Nullable
    public static Category categoryOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (CuriosCompat.isCurio(stack)) return Category.BAUBLE;
        Item item = stack.getItem();
        String cls = item.getClass().getName();
        if (item instanceof TieredItem || TETRA_TOOLS.contains(cls)) return Category.TOOL;
        if (item instanceof BowItem || item instanceof CrossbowItem || TETRA_BOWS.contains(cls)) return Category.BOW;
        if (item instanceof ShieldItem || TETRA_SHIELD.equals(cls)) return Category.SHIELD;
        if (item instanceof ArmorItem) return Category.ARMOR;
        return null;
    }

    /** For armor, the piece-specific category (HELMET..BOOTS); null otherwise. */
    @Nullable
    public static Category armorPiece(ItemStack stack) {
        if (!(stack.getItem() instanceof ArmorItem armor)) return null;
        return switch (armor.getType()) {
            case HELMET -> Category.HELMET;
            case CHESTPLATE -> Category.CHESTPLATE;
            case LEGGINGS -> Category.LEGGINGS;
            default -> Category.BOOTS; // BOOTS and BODY (animal armor) both land here
        };
    }

    /**
     * The section of {@code q} (a quality or affix) that applies to {@code stack}, and the slot it applies in.
     * Returns null when the quality has nothing for this item.
     */
    @Nullable
    public static Applied applied(Sectioned q, ItemStack stack) {
        Category cat = categoryOf(stack);
        if (cat == null) return null;
        if (cat == Category.ARMOR) {
            Category piece = armorPiece(stack);
            if (piece == null) return null;
            if (q.has(Category.ARMOR)) return new Applied(Category.ARMOR, piece.slot(), "_" + piece.key());
            if (q.has(piece)) return new Applied(piece, piece.slot(), "");
            return null;
        }
        return q.has(cat) ? new Applied(cat, cat.slot(), "") : null;
    }

    /**
     * @param section  which JSON section to read
     * @param slot     slot group the modifiers apply in (null for baubles)
     * @param idSuffix appended to modifier ids so generic armor modifiers stack across pieces
     */
    public record Applied(Category section, @Nullable EquipmentSlotGroup slot, String idSuffix) {}
}
