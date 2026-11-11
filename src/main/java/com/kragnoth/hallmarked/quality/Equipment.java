package com.kragnoth.hallmarked.quality;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.compat.CuriosCompat;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import org.jetbrains.annotations.Nullable;

/**
 * Decides which equipment category an item belongs to.
 *
 * <p>Tags are authoritative: {@code hallmarked:equipment/<category>} (which pull in the vanilla and {@code c:}
 * tool/armor tags by default). Class checks are only a fallback for items a mod forgot to tag, e.g. a sword
 * from a careless port that extends {@code SwordItem} but sits in no tag. {@code hallmarked:equipment/excluded}
 * beats everything.
 */
public final class Equipment {
    private Equipment() {}

    public static final TagKey<Item> TOOL = tag("tool");
    public static final TagKey<Item> BOW = tag("bow");
    public static final TagKey<Item> SHIELD = tag("shield");
    public static final TagKey<Item> HELMET = tag("helmet");
    public static final TagKey<Item> CHESTPLATE = tag("chestplate");
    public static final TagKey<Item> LEGGINGS = tag("leggings");
    public static final TagKey<Item> BOOTS = tag("boots");
    public static final TagKey<Item> EXCLUDED = tag("excluded");
    /** Tool roles, used by an affix's "for" key. Tags first; untagged items fall back to their class. */
    public static final TagKey<Item> WEAPON = tag("weapon");
    public static final TagKey<Item> DIGGER = tag("digger");

    private static TagKey<Item> tag(String name) {
        return TagKey.create(Registries.ITEM, Hallmarked.id("equipment/" + name));
    }

    // Tetra's modular items don't extend the vanilla classes; matched by name so there's no hard dependency.
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

    /** Category per item (tags and classes are item-level). Cleared when tags reload. Concurrent: JEI indexes off-thread. */
    private static final java.util.Map<Item, java.util.Optional<Category>> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearCache() {
        CACHE.clear();
    }

    /** The broad category of the item, or null if it can't carry marks. Armor pieces resolve to ARMOR. */
    @Nullable
    public static Category categoryOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        return CACHE.computeIfAbsent(stack.getItem(), i -> java.util.Optional.ofNullable(compute(stack))).orElse(null);
    }

    @Nullable
    private static Category compute(ItemStack stack) {
        if (stack.is(EXCLUDED)) return null;
        if (CuriosCompat.isCurio(stack)) return Category.BAUBLE;

        // 1. Tags
        if (stack.is(TOOL)) return Category.TOOL;
        if (stack.is(BOW)) return Category.BOW;
        if (stack.is(SHIELD)) return Category.SHIELD;
        if (stack.is(HELMET) || stack.is(CHESTPLATE) || stack.is(LEGGINGS) || stack.is(BOOTS)) return Category.ARMOR;

        // 2. Fallback for untagged items
        Item item = stack.getItem();
        String cls = item.getClass().getName();
        if (item instanceof TieredItem || TETRA_TOOLS.contains(cls)) return Category.TOOL;
        if (item instanceof BowItem || item instanceof CrossbowItem || TETRA_BOWS.contains(cls)) return Category.BOW;
        if (item instanceof ShieldItem || TETRA_SHIELD.equals(cls)) return Category.SHIELD;
        if (item instanceof ArmorItem) return Category.ARMOR;
        return null;
    }

    /** Swords, axes, tridents, maces and anything in hallmarked:equipment/weapon. */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.is(WEAPON)) return true;
        Item item = stack.getItem();
        return item instanceof net.minecraft.world.item.SwordItem || item instanceof net.minecraft.world.item.AxeItem
                || item instanceof net.minecraft.world.item.TridentItem || item instanceof net.minecraft.world.item.MaceItem;
    }

    /** Pickaxes, shovels, hoes, axes and anything in hallmarked:equipment/digger. */
    public static boolean isDigger(ItemStack stack) {
        return stack.is(DIGGER) || stack.getItem() instanceof net.minecraft.world.item.DiggerItem;
    }

    /** For armor, the piece (HELMET..BOOTS): tag first, then ArmorItem type, then the item's equip slot. */
    @Nullable
    public static Category armorPiece(ItemStack stack) {
        if (stack.is(HELMET)) return Category.HELMET;
        if (stack.is(CHESTPLATE)) return Category.CHESTPLATE;
        if (stack.is(LEGGINGS)) return Category.LEGGINGS;
        if (stack.is(BOOTS)) return Category.BOOTS;
        if (stack.getItem() instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET -> Category.HELMET;
                case CHESTPLATE -> Category.CHESTPLATE;
                case LEGGINGS -> Category.LEGGINGS;
                default -> Category.BOOTS;
            };
        }
        if (stack.getItem() instanceof Equipable eq) {
            EquipmentSlot slot = eq.getEquipmentSlot();
            return switch (slot) {
                case HEAD -> Category.HELMET;
                case CHEST -> Category.CHESTPLATE;
                case LEGS -> Category.LEGGINGS;
                case FEET -> Category.BOOTS;
                default -> null;
            };
        }
        return null;
    }

    /**
     * The section of {@code q} (a quality or affix) that applies to {@code stack}, and the slot it applies in.
     * Returns null when it has nothing for this item.
     */
    @Nullable
    public static Applied applied(Sectioned q, ItemStack stack) {
        Category cat = categoryOf(stack);
        if (cat == null) return null;
        if (cat == Category.ARMOR) {
            Category piece = armorPiece(stack);
            if (piece == null) return null;
            // A piece-specific section (e.g. "boots") overrides the generic "armor" one for that piece.
            if (q.has(piece)) return new Applied(piece, piece.slot(), "");
            if (q.has(Category.ARMOR)) return new Applied(Category.ARMOR, piece.slot(), "_" + piece.key());
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
