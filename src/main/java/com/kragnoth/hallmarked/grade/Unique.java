package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Sectioned;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

/**
 * A hand-authored named item: fixed stats, optional virtual enchantments, a lore line. Found gear of a matching base
 * item has a per-source chance to become this unique instead of rolling a grade.
 *
 * <pre>{@code
 * { "name": "hallmarked.unique.lamplighters_hook", "lore": "hallmarked.unique.lamplighters_hook.lore",
 *   "items": { "item": "minecraft:iron_sword" }, "chance": { "loot": 0.01, "mob": 0.002 },
 *   "tool": { "modifiers": [ { "name": "lamplighter_damage", "attribute": "minecraft:generic.attack_damage",
 *                              "operation": "multiply_total", "value": 0.12 } ] },
 *   "enchantments": { "minecraft:looting": 1 } }
 * }</pre>
 */
public record Unique(String name, @Nullable String lore, Ingredient items, Map<String, Double> chance,
                     Map<Category, List<Affix.Mod>> sections, Map<ResourceLocation, Integer> enchantments,
                     JsonObject source) implements Sectioned {

    @Override
    public boolean has(Category c) {
        return sections.containsKey(c);
    }

    public List<Affix.Mod> section(Category c) {
        return sections.getOrDefault(c, List.of());
    }

    public double chance(String source) {
        return chance.getOrDefault(source, 0.0);
    }

    public static Unique parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".unique." + id.getPath().replace('/', '.'));
        Strict.warnUnknownKeys(id, json, Strict.withSections("name", "lore", "items", "chance", "enchantments"), "Unique");
        String lore = json.has("lore") ? GsonHelper.getAsString(json, "lore") : null;
        Ingredient items = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, json.get("items")).getOrThrow(IllegalArgumentException::new);

        Map<String, Double> chance = new HashMap<>();
        for (var e : GsonHelper.getAsJsonObject(json, "chance", new JsonObject()).entrySet()) {
            chance.put(e.getKey(), Math.max(0.0, Math.min(1.0, e.getValue().getAsDouble())));
        }

        Map<Category, List<Affix.Mod>> sections = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            if (!json.has(c.key())) continue;
            JsonArray arr = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(json, c.key()), "modifiers", new JsonArray());
            List<Affix.Mod> mods = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                Strict.warnUnknownKeys(id, m, Strict.MODIFIER_KEYS, "Unique modifier in");
                String modName = GsonHelper.getAsString(m, "name").toLowerCase(Locale.ROOT);
                if (!ResourceLocation.isValidPath(modName)) throw new IllegalArgumentException("invalid modifier name '" + modName + "'");
                double v = GsonHelper.getAsDouble(m, "value");
                mods.add(new Affix.Mod(modName, ResourceLocation.parse(GsonHelper.getAsString(m, "attribute").toLowerCase(Locale.ROOT)),
                        Strict.operation(GsonHelper.getAsString(m, "operation", "multiply_total")), v, v));
            }
            sections.put(c, List.copyOf(mods));
        }
        if (sections.isEmpty()) throw new IllegalArgumentException("a unique needs at least one equipment section");

        Map<ResourceLocation, Integer> ench = new LinkedHashMap<>();
        for (var e : GsonHelper.getAsJsonObject(json, "enchantments", new JsonObject()).entrySet()) {
            ench.put(ResourceLocation.parse(e.getKey()), Math.max(1, Math.min(10, e.getValue().getAsInt())));
        }

        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Unique(name, lore, items, Map.copyOf(chance), sections, Map.copyOf(ench), source);
    }
}
