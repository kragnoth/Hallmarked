package com.kragnoth.hallmarked.quality;

import java.util.Locale;
import net.minecraft.world.entity.EquipmentSlotGroup;

/** Sections a quality JSON may define. */
public enum Category {
    TOOL(EquipmentSlotGroup.MAINHAND),
    BOW(EquipmentSlotGroup.HAND),
    SHIELD(EquipmentSlotGroup.OFFHAND),
    ARMOR(null),
    HELMET(EquipmentSlotGroup.HEAD),
    CHESTPLATE(EquipmentSlotGroup.CHEST),
    LEGGINGS(EquipmentSlotGroup.LEGS),
    BOOTS(EquipmentSlotGroup.FEET),
    BAUBLE(null);

    private final EquipmentSlotGroup slot;

    Category(EquipmentSlotGroup slot) {
        this.slot = slot;
    }

    /** Slot group the section's modifiers apply to; null for ARMOR (resolved per piece) and BAUBLE (player-applied). */
    public EquipmentSlotGroup slot() {
        return slot;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
