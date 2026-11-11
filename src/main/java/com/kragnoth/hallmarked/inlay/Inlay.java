package com.kragnoth.hallmarked.inlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Sectioned;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * A fixed, hand-applied enhancement: Smith's Stamp + equipment + material at a smithing table.
 *
 * <pre>{@code
 * { "name": "hallmarked.inlay.quartz_honed", "group": "edge",
 *   "template": { "item": "hallmarked:smiths_stamp" },          // optional, defaults to the Smith's Stamp
 *   "material": { "tag": "c:gems/quartz" },
 *   "tool": { "modifiers": [ { "name": "quartz_honed_damage", "attribute": "minecraft:generic.attack_damage",
 *                              "operation": "multiply_total", "value": 0.06 } ] },
 *   "enchantments": { "minecraft:fortune": 1 } }                 // virtual levels, optional
 * }</pre>
 * Sections are the same as qualities/affixes and decide which equipment accepts the inlay. An inlay that only grants
 * virtual enchantments still needs its section(s), e.g. {@code "tool": { "modifiers": [] }}.
 */
public record Inlay(String name, String group, Ingredient template, Ingredient material,
                    Map<Category, List<Affix.Mod>> sections, Map<ResourceLocation, Integer> enchantments,
                    JsonObject source) implements Sectioned {

    public static final ResourceLocation DEFAULT_TEMPLATE = ResourceLocation.fromNamespaceAndPath("hallmarked", "smiths_stamp");

    @Override
    public boolean has(Category c) {
        return sections.containsKey(c);
    }

    public List<Affix.Mod> section(Category c) {
        return sections.getOrDefault(c, List.of());
    }

    public static Inlay parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".inlay." + id.getPath().replace('/', '.'));
        String group = GsonHelper.getAsString(json, "group", name);

        JsonElement tmpl = json.has("template") ? json.get("template") : itemJson(DEFAULT_TEMPLATE.toString());
        Ingredient template = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, tmpl).getOrThrow(IllegalArgumentException::new);
        Ingredient material = Ingredient.CODEC_NONEMPTY.parse(JsonOps.INSTANCE, json.get("material")).getOrThrow(IllegalArgumentException::new);

        Map<Category, List<Affix.Mod>> sections = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            if (!json.has(c.key())) continue;
            JsonArray arr = GsonHelper.getAsJsonArray(GsonHelper.getAsJsonObject(json, c.key()), "modifiers", new JsonArray());
            List<Affix.Mod> mods = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                String modName = GsonHelper.getAsString(m, "name").toLowerCase(Locale.ROOT);
                if (!ResourceLocation.isValidPath(modName)) throw new IllegalArgumentException("invalid modifier name '" + modName + "'");
                double v = GsonHelper.getAsDouble(m, "value");
                mods.add(new Affix.Mod(modName, ResourceLocation.parse(GsonHelper.getAsString(m, "attribute").toLowerCase(Locale.ROOT)),
                        Affix.parseOperation(GsonHelper.getAsString(m, "operation", "multiply_total")), v, v));
            }
            sections.put(c, List.copyOf(mods));
        }
        if (sections.isEmpty()) throw new IllegalArgumentException("an inlay needs at least one equipment section");

        Map<ResourceLocation, Integer> ench = new LinkedHashMap<>();
        if (json.has("enchantments")) {
            for (var e : GsonHelper.getAsJsonObject(json, "enchantments").entrySet()) {
                ench.put(ResourceLocation.parse(e.getKey()), Math.max(1, Math.min(10, e.getValue().getAsInt())));
            }
        }

        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Inlay(name, group, template, material, sections, Map.copyOf(ench), source);
    }

    private static JsonObject itemJson(String id) {
        JsonObject o = new JsonObject();
        o.addProperty("item", id);
        return o;
    }

    /** Copper: the colour of an item name that carries inlays, and of the tooltip section. */
    public static final int COLOR = 0xC87533;
}
