package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Sectioned;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A rollable affix. Prefixes and suffixes appear on Journeyman-and-better work; flaws only on Shoddy work.
 * Each modifier has a min..max range; the item's stored roll (0..1) picks the value inside it.
 *
 * <pre>{@code
 * { "name": "hallmarked.affix.honed", "type": "prefix", "weight": 10, "group": "melee_damage",
 *   "tool": { "modifiers": [ { "name": "honed_attack_damage", "attribute": "minecraft:generic.attack_damage",
 *                              "operation": "multiply_total", "min": 0.03, "max": 0.12 } ] } }
 * }</pre>
 */
public record Affix(String name, Type type, int weight, String group, Map<Category, List<Mod>> sections,
                    List<com.kragnoth.hallmarked.proc.Proc> procs, JsonObject source)
        implements Sectioned {

    /** FORBIDDEN affixes never roll normally; only the Ashen Altar grants them. */
    public enum Type { PREFIX, SUFFIX, FLAW, FORBIDDEN }

    public record Mod(String name, ResourceLocation attributeId, AttributeModifier.Operation operation, double min, double max) {
        public double value(float roll) {
            return min + (max - min) * roll;
        }

        public Optional<Holder.Reference<Attribute>> attribute() {
            return BuiltInRegistries.ATTRIBUTE.getHolder(attributeId);
        }
    }

    @Override
    public boolean has(Category c) {
        return sections.containsKey(c);
    }

    public List<Mod> section(Category c) {
        return sections.getOrDefault(c, List.of());
    }

    public static Affix parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".affix." + id.getPath().replace('/', '.'));
        Type type = Type.valueOf(GsonHelper.getAsString(json, "type", "prefix").toUpperCase(Locale.ROOT));
        int weight = Math.min(1_000_000, Math.max(0, GsonHelper.getAsInt(json, "weight", 10)));
        String group = GsonHelper.getAsString(json, "group", name);
        Strict.warnUnknownKeys(id, json, Strict.withSections("name", "type", "weight", "group", "procs", "requires_mods"), "Affix");

        Map<Category, List<Mod>> sections = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            if (!json.has(c.key())) continue;
            JsonArray arr = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(json, c.key()), "modifiers", new JsonArray());
            List<Mod> mods = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                Strict.warnUnknownKeys(id, m, Strict.MODIFIER_KEYS, "Affix modifier in");
                double min, max;
                if (m.has("value")) {
                    min = max = GsonHelper.getAsDouble(m, "value");
                } else {
                    min = GsonHelper.getAsDouble(m, "min");
                    max = GsonHelper.getAsDouble(m, "max");
                }
                String modName = GsonHelper.getAsString(m, "name").toLowerCase(Locale.ROOT);
                if (!ResourceLocation.isValidPath(modName)) throw new IllegalArgumentException("invalid modifier name '" + modName + "' (use a-z 0-9 _ - . /)");
                mods.add(new Mod(modName,
                        ResourceLocation.parse(GsonHelper.getAsString(m, "attribute").toLowerCase(Locale.ROOT)),
                        parseOperation(GsonHelper.getAsString(m, "operation", "multiply_total")), min, max));
            }
            sections.put(c, List.copyOf(mods));
        }
        List<com.kragnoth.hallmarked.proc.Proc> procs = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "procs", new JsonArray())) {
            procs.add(com.kragnoth.hallmarked.proc.Proc.parse(id, e.getAsJsonObject()));
        }
        // An affix whose attribute belongs to a mod that isn't installed can't load (so it never rolls).
        for (List<Mod> mods : sections.values()) {
            for (Mod m : mods) {
                if (m.attribute().isEmpty()) throw new IllegalArgumentException("unknown attribute " + m.attributeId());
            }
        }
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Affix(name, type, weight, group, sections, List.copyOf(procs), source);
    }

    public static AttributeModifier.Operation parseOperation(String s) {
        return Strict.operation(s);
    }
}
