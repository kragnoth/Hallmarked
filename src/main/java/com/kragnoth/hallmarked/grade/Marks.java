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

    public enum Origin { QUALITY, AFFIX }

    /**
     * @param slot  slot group for item modifiers; null means a bauble (applied to the player while worn)
     * @param label translation key of the quality or affix this came from
     * @param min   affix range low end (equal to max for qualities)
     * @param max   affix range high end
     */
    public record Entry(Holder<Attribute> attribute, AttributeModifier modifier, @Nullable EquipmentSlotGroup slot,
                        Origin origin, String label, double min, double max) {}

    public static List<Entry> of(ItemStack stack) {
        List<Entry> out = new ArrayList<>();

        Quality q = Reforging.qualityOf(stack);
        if (q != null) {
            Equipment.Applied ap = Equipment.applied(q, stack);
            if (ap != null) {
                for (Quality.Mod m : q.section(ap.section())) {
                    m.attribute().ifPresent(attr -> out.add(new Entry(attr,
                            new AttributeModifier(Hallmarked.id(m.name() + ap.idSuffix()), m.value(), m.operation()),
                            ap.slot(), Origin.QUALITY, q.name(), m.value(), m.value())));
                }
            }
        }

        GradeData d = Grading.dataOf(stack);
        if (d != null) {
            for (int i = 0; i < d.affixes().size(); i++) {
                GradeData.Rolled r = d.affixes().get(i);
                Affix a = Grading.AFFIXES.get(r.affix());
                if (a == null) continue;
                Equipment.Applied ap = Equipment.applied(a, stack);
                if (ap == null) continue;
                final int idx = i;
                for (Affix.Mod m : a.section(ap.section())) {
                    m.attribute().ifPresent(attr -> out.add(new Entry(attr,
                            new AttributeModifier(Hallmarked.id("affix/" + idx + "/" + m.name() + ap.idSuffix()), m.value(r.roll()), m.operation()),
                            ap.slot(), Origin.AFFIX, a.name(), m.min(), m.max())));
                }
            }
        }
        return out;
    }

    /** True if the stack is worn as a bauble rather than in an equipment slot. */
    public static boolean isBauble(ItemStack stack) {
        return Equipment.categoryOf(stack) == Category.BAUBLE;
    }
}
