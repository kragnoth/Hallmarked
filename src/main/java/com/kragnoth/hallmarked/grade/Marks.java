package com.kragnoth.hallmarked.grade;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Every attribute modifier an item carries from its quality and its affixes, in one place, so item
 * modifiers, bauble modifiers and tooltips all agree.
 */
public final class Marks {
    private Marks() {}

    public enum Origin { QUALITY, AFFIX, INLAY, UNIQUE, SET }

    /**
     * @param slot        slot group the modifier is applied in; null means a bauble (applied to the player while worn)
     * @param displaySlot slot group shown in the tooltip heading (HAND for bows, which apply in either hand)
     * @param shadow      a second copy for the other hand; applied, but not listed twice in tooltips
     * @param label       translation key of the quality or affix this came from
     * @param min         affix range low end (equal to max for qualities)
     * @param max         affix range high end
     * @param index       index into the item's rolled affixes (-1 for quality modifiers)
     * @param converted   a quality stat re-aimed to suit the tool's role (see {@link com.kragnoth.hallmarked.quality.Conversion})
     */
    public record Entry(Holder<Attribute> attribute, AttributeModifier modifier, @Nullable EquipmentSlotGroup slot,
                        @Nullable EquipmentSlotGroup displaySlot, boolean shadow,
                        Origin origin, String label, double min, double max, int index, boolean converted) {}

    /**
     * Modifier ids are qualified by slot group ("hallmarked:keen_attack_damage/mainhand"). Vanilla replaces a modifier
     * with the same id on the same attribute, so two items sharing a mark (e.g. a bow in each hand) would otherwise
     * overwrite each other, and unequipping one would strip the other's bonus.
     */
    public static List<Entry> of(ItemStack stack) {
        List<Entry> out = new ArrayList<>();

        Quality q = Reforging.qualityOf(stack);
        if (q != null) {
            Equipment.Applied ap = Equipment.applied(q, stack);
            if (ap != null) {
                String role = com.kragnoth.hallmarked.quality.Conversion.roleOf(stack);
                for (Quality.Mod m : q.section(ap.section())) {
                    var conv = com.kragnoth.hallmarked.quality.Conversion.find(role, m.attributeId(), m.value());
                    if (conv != null) {
                        double v = m.value() * conv.scale();
                        var op = conv.operation() != null ? conv.operation() : m.operation();
                        emit(out, conv.target(), m.name() + ap.idSuffix(), v, op, ap.slot(), Origin.QUALITY, q.name(), v, v, -1, true);
                        continue;
                    }
                    m.attribute().ifPresent(attr -> emit(out, attr, m.name() + ap.idSuffix(), m.value(), m.operation(),
                            ap.slot(), Origin.QUALITY, q.name(), m.value(), m.value(), -1, false));
                }
            }
        }

        GradeData d = Grading.isUnappraised(stack) ? null : Grading.dataOf(stack);
        if (d != null) {
            for (int i = 0; i < d.affixes().size(); i++) {
                GradeData.Rolled r = d.affixes().get(i);
                Affix a = Grading.AFFIXES.get(r.affix());
                if (a == null) continue;
                Equipment.Applied ap = Equipment.applied(a, stack);
                if (ap == null) continue;
                final int idx = i;
                for (Affix.Mod m : a.section(ap.section())) {
                    m.attribute().ifPresent(attr -> emit(out, attr, "affix/" + idx + "/" + m.name() + ap.idSuffix(),
                            m.value(r.roll()), m.operation(), ap.slot(), Origin.AFFIX, a.name(), m.min(), m.max(), idx, false));
                }
            }
        }
        Unique unique = Grading.isUnappraised(stack) ? null : Grading.uniqueOf(stack);
        if (unique != null) {
            Equipment.Applied ap = Equipment.applied(unique, stack);
            if (ap != null) {
                for (Affix.Mod m : unique.section(ap.section())) {
                    m.attribute().ifPresent(attr -> emit(out, attr, "unique/" + m.name() + ap.idSuffix(),
                            m.min(), m.operation(), ap.slot(), Origin.UNIQUE, unique.name(), m.min(), m.max(), -1, false));
                }
            }
        }

        var setPiece = com.kragnoth.hallmarked.sets.Sets.defOf(stack);
        if (setPiece != null) {
            Equipment.Applied ap = Equipment.applied(setPiece, stack);
            if (ap != null) {
                for (Affix.Mod m : setPiece.modifiers()) {
                    m.attribute().ifPresent(attr -> emit(out, attr, "setpiece/" + m.name() + ap.idSuffix(),
                            m.min(), m.operation(), ap.slot(), Origin.SET, setPiece.name(), m.min(), m.max(), -1, false));
                }
            }
        }

        for (com.kragnoth.hallmarked.inlay.Inlay inlay : com.kragnoth.hallmarked.inlay.Inlays.on(stack)) {
            emitInlay(out, stack, inlay, "inlay/", -1);
        }
        com.kragnoth.hallmarked.inlay.Inlay sealed = com.kragnoth.hallmarked.inlay.Inlays.sealed(stack);
        // Distinct id prefix so a sealed copy of an inlay stacks with the regular one instead of replacing it.
        if (sealed != null) emitInlay(out, stack, sealed, "sealed/", SEALED_INDEX);
        return out;
    }

    /** {@link Entry#index()} value marking the sealed inlay's modifiers. */
    public static final int SEALED_INDEX = -2;

    private static void emitInlay(List<Entry> out, ItemStack stack, com.kragnoth.hallmarked.inlay.Inlay inlay, String prefix, int index) {
        Equipment.Applied ap = Equipment.applied(inlay, stack);
        if (ap == null) return;
        for (Affix.Mod m : inlay.section(ap.section())) {
            m.attribute().ifPresent(attr -> emit(out, attr, prefix + m.name() + ap.idSuffix(),
                    m.min(), m.operation(), ap.slot(), Origin.INLAY, inlay.name(), m.min(), m.max(), index, false));
        }
    }

    private static void emit(List<Entry> out, Holder<Attribute> attr, String path, double amount, AttributeModifier.Operation op,
                             @Nullable EquipmentSlotGroup group, Origin origin, String label, double min, double max, int index,
                             boolean converted) {
        if (group == null) { // bauble: the player-side sync qualifies the id with the curio slot
            out.add(new Entry(attr, new AttributeModifier(Hallmarked.id(path), amount, op), null, null, false, origin, label, min, max, index, converted));
        } else if (group == EquipmentSlotGroup.HAND) {
            out.add(new Entry(attr, new AttributeModifier(Hallmarked.id(path + "/mainhand"), amount, op),
                    EquipmentSlotGroup.MAINHAND, EquipmentSlotGroup.HAND, false, origin, label, min, max, index, converted));
            out.add(new Entry(attr, new AttributeModifier(Hallmarked.id(path + "/offhand"), amount, op),
                    EquipmentSlotGroup.OFFHAND, EquipmentSlotGroup.HAND, true, origin, label, min, max, index, converted));
        } else {
            out.add(new Entry(attr, new AttributeModifier(Hallmarked.id(path + "/" + group.getSerializedName()), amount, op),
                    group, group, false, origin, label, min, max, index, converted));
        }
    }

    /** True if the stack is worn as a bauble rather than in an equipment slot. */
    public static boolean isBauble(ItemStack stack) {
        return Equipment.categoryOf(stack) == Category.BAUBLE;
    }
}
