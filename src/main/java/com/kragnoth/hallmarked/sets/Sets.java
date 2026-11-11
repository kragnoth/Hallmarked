package com.kragnoth.hallmarked.sets;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.compat.CuriosCompat;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Durability;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.proc.Proc;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Named gear sets: making pieces, counting what's worn, and applying the bonuses. */
public final class Sets {
    private Sets() {}

    public static final DataStore<GearSet> STORE = new DataStore<>("sets", GearSet::parse, GearSet::name, GearSet::source);
    public static final String SET_GRADE = "hallmarked.grade.set";
    private static final String BONUS_PREFIX = "set/";

    @Nullable
    public static SetPiece pieceOf(ItemStack stack) {
        return stack.get(HMRegistries.SET_PIECE.get());
    }

    @Nullable
    public static GearSet setOf(ItemStack stack) {
        SetPiece p = pieceOf(stack);
        return p == null ? null : STORE.get(p.set());
    }

    @Nullable
    public static GearSet.Piece defOf(ItemStack stack) {
        SetPiece p = pieceOf(stack);
        GearSet set = p == null ? null : STORE.get(p.set());
        return set == null ? null : set.piece(p.piece());
    }

    // ---- Making pieces --------------------------------------------------------------------------

    /** A fresh piece, or null if its base item isn't registered. */
    @Nullable
    public static ItemStack create(GearSet set, GearSet.Piece piece, @Nullable RegistryAccess access) {
        var item = BuiltInRegistries.ITEM.getOptional(piece.item()).orElse(null);
        if (item == null) return null;
        ItemStack stack = new ItemStack(item);
        dress(stack, set, piece, access);
        return stack;
    }

    /** Turns a stack of the piece's base item into the piece. */
    public static void dress(ItemStack stack, GearSet set, GearSet.Piece piece, @Nullable RegistryAccess access) {
        stack.set(HMRegistries.SET_PIECE.get(), new SetPiece(set.name(), piece.key()));
        stack.set(HMRegistries.GRADE.get(), new GradeData(SET_GRADE, List.of(), "", ""));
        stack.set(HMRegistries.ROLLED.get(), true);
        stack.remove(HMRegistries.UNAPPRAISED.get());
        stack.remove(HMRegistries.UNIQUE.get());
        if (piece.dye() != null) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(piece.dye(), false));
        if (piece.baseColor() != null) stack.set(DataComponents.BASE_COLOR, piece.baseColor());
        if (piece.glint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        if (piece.trimPattern() != null && piece.trimMaterial() != null) {
            if (access == null && ServerLifecycleHooks.getCurrentServer() != null) access = ServerLifecycleHooks.getCurrentServer().registryAccess();
            if (access != null) {
                var pattern = access.registryOrThrow(Registries.TRIM_PATTERN).getHolder(ResourceKey.create(Registries.TRIM_PATTERN, piece.trimPattern()));
                var material = access.registryOrThrow(Registries.TRIM_MATERIAL).getHolder(ResourceKey.create(Registries.TRIM_MATERIAL, piece.trimMaterial()));
                if (pattern.isPresent() && material.isPresent()) {
                    stack.set(DataComponents.TRIM, new ArmorTrim(material.get(), pattern.get(), false));
                }
            }
        }
        Grading.refreshName(stack);
        Durability.apply(stack);
    }

    /** When gear is graded from a source, it may turn out to be a set piece whose base item it already is. */
    public static boolean tryRoll(ItemStack stack, String source, RandomSource random) {
        if (Grading.GRADES.get(SET_GRADE) == null) return false;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (GearSet set : STORE.all()) {
            double c = set.chance(source);
            if (c <= 0) continue;
            for (GearSet.Piece piece : set.pieces()) {
                if (piece.item().equals(id) && random.nextDouble() < c) {
                    dress(stack, set, piece, null);
                    return true;
                }
            }
        }
        return false;
    }

    /** Any piece of any set whose base item falls in one of the categories (for a Mountebank's parcels). */
    @Nullable
    public static ItemStack randomPiece(@Nullable RegistryAccess access, RandomSource random, Set<Category> categories) {
        if (Grading.GRADES.get(SET_GRADE) == null) return null;
        List<ItemStack> options = new ArrayList<>();
        for (GearSet set : STORE.all()) {
            for (GearSet.Piece piece : set.pieces()) {
                ItemStack s = create(set, piece, access);
                if (s == null) continue;
                Category c = Equipment.categoryOf(s);
                if (c != null && categories.contains(c)) options.add(s);
            }
        }
        return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
    }

    // ---- Counting what's worn -------------------------------------------------------------------

    /** Piece keys worn per set, counting each piece only where it belongs (armor in its slot, a weapon in hand...). */
    public static Map<String, Set<String>> worn(LivingEntity entity) {
        Map<String, Set<String>> out = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) count(out, entity.getItemBySlot(slot), slot);
        if (entity instanceof Player && Hallmarked.curiosLoaded()) {
            for (ItemStack s : CuriosCompat.equipped(entity)) count(out, s, null);
        }
        return out;
    }

    private static void count(Map<String, Set<String>> out, ItemStack stack, @Nullable EquipmentSlot slot) {
        if (stack.isEmpty()) return;
        SetPiece p = pieceOf(stack);
        GearSet.Piece def = defOf(stack);
        if (p == null || def == null) return;
        Equipment.Applied ap = Equipment.applied(def, stack);
        if (ap == null) return;
        boolean proper = ap.slot() == null ? slot == null : slot != null && ap.slot().test(slot);
        if (proper) out.computeIfAbsent(p.set(), k -> new HashSet<>()).add(p.piece());
    }

    /** Active bonuses, in set order: every bonus whose piece count is met. */
    public static Map<GearSet, List<GearSet.Bonus>> active(LivingEntity entity) {
        Map<GearSet, List<GearSet.Bonus>> out = new LinkedHashMap<>();
        for (var e : worn(entity).entrySet()) {
            GearSet set = STORE.get(e.getKey());
            if (set == null) continue;
            int n = e.getValue().size();
            List<GearSet.Bonus> on = new ArrayList<>();
            for (GearSet.Bonus b : set.bonuses()) if (n >= b.pieces()) on.add(b);
            if (!on.isEmpty()) out.put(set, on);
        }
        return out;
    }

    /** Procs from full-enough sets; they fire at their listed (max) values. */
    public static List<Proc> activeProcs(LivingEntity entity) {
        List<Proc> out = new ArrayList<>();
        if (!(entity instanceof Player)) return out;
        for (var bonuses : active(entity).values()) for (GearSet.Bonus b : bonuses) out.addAll(b.procs());
        return out;
    }

    // ---- Applying bonuses -----------------------------------------------------------------------

    private record Desired(Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, AttributeModifier mod) {}

    /**
     * Stateless, like baubles: every Hallmarked modifier whose id starts with "set/" is compared with what the worn
     * sets want right now; extras are removed, missing or changed ones are (re)added.
     */
    public static void syncBonuses(Player player) {
        Map<ResourceLocation, Desired> desired = new HashMap<>();
        for (var e : active(player).entrySet()) {
            String setPath = e.getKey().name().replace(':', '.').toLowerCase(java.util.Locale.ROOT);
            for (GearSet.Bonus b : e.getValue()) {
                for (Affix.Mod m : b.modifiers()) {
                    var attr = m.attribute();
                    if (attr.isEmpty()) continue;
                    ResourceLocation id = Hallmarked.id(BONUS_PREFIX + setPath + "/" + b.pieces() + "/" + m.name());
                    desired.put(id, new Desired(attr.get(), new AttributeModifier(id, m.min(), m.operation())));
                }
            }
        }
        for (var attrHolder : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance inst = player.getAttribute(attrHolder);
            if (inst == null) continue;
            for (AttributeModifier mod : List.copyOf(inst.getModifiers())) {
                ResourceLocation id = mod.id();
                if (!id.getNamespace().equals(Hallmarked.MODID) || !id.getPath().startsWith(BONUS_PREFIX)) continue;
                Desired d = desired.get(id);
                if (d == null || !d.attr().equals(attrHolder) || d.mod().amount() != mod.amount() || d.mod().operation() != mod.operation()) {
                    inst.removeModifier(id);
                }
            }
        }
        for (var d : desired.entrySet()) {
            AttributeInstance inst = player.getAttribute(d.getValue().attr());
            if (inst != null && inst.getModifier(d.getKey()) == null) inst.addTransientModifier(d.getValue().mod());
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
