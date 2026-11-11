package com.kragnoth.hallmarked.inlay;

import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.List;
import com.kragnoth.hallmarked.Hallmarked;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Loaded inlays plus the rules for applying them. */
public final class Inlays {
    private Inlays() {}

    public static final DataStore<Inlay> STORE = new DataStore<>("inlays", Inlay::parse, Inlay::name, Inlay::source);

    /** Templates that apply a sealed (bonus) inlay. Data-driven; holds the Gearwright's Seal by default. */
    public static final TagKey<Item> SEAL_TEMPLATES = TagKey.create(Registries.ITEM, Hallmarked.id("seal_templates"));

    /** Regular inlays on the stack, in the order applied (unknown names skipped). */
    public static List<Inlay> on(ItemStack stack) {
        List<String> names = stack.getOrDefault(HMRegistries.INLAYS.get(), List.of());
        List<Inlay> out = new ArrayList<>(names.size());
        for (String n : names) {
            Inlay i = STORE.get(n);
            if (i != null) out.add(i);
        }
        return out;
    }

    /** The sealed inlay, if any. */
    @Nullable
    public static Inlay sealed(ItemStack stack) {
        return STORE.get(stack.get(HMRegistries.SEALED_INLAY.get()));
    }

    public static boolean isSeal(ItemStack stack) {
        return stack.is(SEAL_TEMPLATES);
    }

    /** Inlay slots from the item's grade; ungraded items count as Common. */
    public static int slots(ItemStack stack) {
        Grade g = Grading.gradeOf(stack);
        if (g == null) g = Grading.findGrade("common");
        return g == null ? 0 : g.inlaySlots();
    }

    public static int used(ItemStack stack) {
        return stack.getOrDefault(HMRegistries.INLAYS.get(), List.of()).size();
    }

    /**
     * The inlay that template + material would apply to {@code equipment}, or null if none fits: the equipment
     * must accept it (section), have a free slot, and not already carry an inlay from the same group.
     */
    @Nullable
    public static Inlay find(ItemStack template, ItemStack equipment, ItemStack material) {
        if (equipment.isEmpty() || Equipment.categoryOf(equipment) == null) return null;
        if (equipment.getOrDefault(HMRegistries.TEMPERED.get(), false)) return null; // tempered pieces are locked
        if (isSeal(template)) return findSealed(equipment, material);
        if (used(equipment) >= slots(equipment)) return null;
        List<Inlay> present = on(equipment);
        for (Inlay inlay : STORE.all()) {
            if (!inlay.template().test(template) || !inlay.material().test(material)) continue;
            if (Equipment.applied(inlay, equipment) == null) continue;
            if (present.stream().anyMatch(p -> p.group().equals(inlay.group()))) continue;
            return inlay;
        }
        return null;
    }

    /**
     * Seal: any inlay whose material matches and which fits the equipment; ignores free slots and group limits
     * (so Lapis + sealed Lapis = +2 Looting). One per item.
     */
    @Nullable
    private static Inlay findSealed(ItemStack equipment, ItemStack material) {
        if (equipment.has(HMRegistries.SEALED_INLAY.get())) return null;
        for (Inlay inlay : STORE.all()) {
            if (inlay.material().test(material) && Equipment.applied(inlay, equipment) != null) return inlay;
        }
        return null;
    }

    public static boolean isTemplate(ItemStack stack) {
        if (isSeal(stack)) return true;
        for (Inlay i : STORE.all()) if (i.template().test(stack)) return true;
        return false;
    }

    public static boolean isMaterial(ItemStack stack) {
        for (Inlay i : STORE.all()) if (i.material().test(stack)) return true;
        return false;
    }

    /** Returns a copy of {@code equipment} with {@code inlay} added (and its name tint refreshed). */
    public static ItemStack apply(ItemStack template, ItemStack equipment, Inlay inlay) {
        ItemStack out = equipment.copyWithCount(1);
        if (isSeal(template)) {
            out.set(HMRegistries.SEALED_INLAY.get(), inlay.name());
            Grading.refreshName(out);
            return out;
        }
        List<String> names = new ArrayList<>(out.getOrDefault(HMRegistries.INLAYS.get(), List.of()));
        names.add(inlay.name());
        out.set(HMRegistries.INLAYS.get(), List.copyOf(names));
        Grading.refreshName(out);
        return out;
    }

    /** Returns a copy with every regular inlay removed (grindstone). The sealed inlay is permanent. */
    public static ItemStack strip(ItemStack equipment) {
        ItemStack out = equipment.copy();
        out.remove(HMRegistries.INLAYS.get());
        Grading.refreshName(out);
        return out;
    }
}
