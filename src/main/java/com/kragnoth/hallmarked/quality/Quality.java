package com.kragnoth.hallmarked.quality;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * A quality: a named, coloured bundle of attribute modifiers per equipment {@link Category}.
 *
 * @param name     identity and translation key, e.g. {@code hallmarked.quality.sharp}
 * @param color    colour of the name in tooltips
 * @param weight   relative roll weight (default 1)
 * @param sections modifiers per category
 * @param source   original JSON, kept for client sync
 */
public record Quality(String name, ChatFormatting color, int weight, Map<Category, List<Mod>> sections, JsonObject source) implements Sectioned {

    public static final String LEGACY_PREFIX = "quality_equipment.quality.";
    public static final String PREFIX = "hallmarked.quality.";

    /** One modifier definition from a quality section. */
    public record Mod(String name, ResourceLocation attributeId, AttributeModifier.Operation operation, double value) {
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

    /** Maps names from the original mod's datapacks onto Hallmarked names so old packs and items keep working. */
    public static String normalizeName(String name) {
        return name.startsWith(LEGACY_PREFIX) ? PREFIX + name.substring(LEGACY_PREFIX.length()) : name;
    }

    public static Quality parse(ResourceLocation fileId, JsonObject json) {
        String name = json.has("name")
                ? normalizeName(GsonHelper.getAsString(json, "name"))
                : fileId.getNamespace() + ".quality." + fileId.getPath().replace('/', '.');

        ChatFormatting color = ChatFormatting.GRAY;
        String code = json.has("color_code") ? GsonHelper.getAsString(json, "color_code")
                : json.has("color") ? GsonHelper.getAsString(json, "color") : "";
        if (!code.isEmpty()) color = com.kragnoth.hallmarked.grade.Strict.color(code);
        com.kragnoth.hallmarked.grade.Strict.warnUnknownKeys(fileId, json,
                com.kragnoth.hallmarked.grade.Strict.withSections("name", "color", "color_code", "weight"), "Quality");

        int weight = Math.min(1_000_000, Math.max(0, GsonHelper.getAsInt(json, "weight", 1)));

        Map<Category, List<Mod>> sections = new EnumMap<>(Category.class);
        for (Category c : Category.values()) {
            if (!json.has(c.key())) continue;
            JsonObject section = GsonHelper.getAsJsonObject(json, c.key());
            JsonArray arr = GsonHelper.getAsJsonArray(section, "modifiers", new JsonArray());
            List<Mod> mods = new ArrayList<>();
            for (JsonElement e : arr) {
                JsonObject m = e.getAsJsonObject();
                com.kragnoth.hallmarked.grade.Strict.warnUnknownKeys(fileId, m, com.kragnoth.hallmarked.grade.Strict.MODIFIER_KEYS, "Quality modifier in");
                String attr = GsonHelper.getAsString(m, "attribute").toLowerCase(Locale.ROOT);
                if (attr.startsWith("quality_equipment:")) attr = "hallmarked:" + attr.substring("quality_equipment:".length());
                String modName = GsonHelper.getAsString(m, "name").toLowerCase(Locale.ROOT);
                // Modifier names become attribute-modifier ids; reject anything that isn't a valid id path.
                if (!ResourceLocation.isValidPath(modName)) throw new IllegalArgumentException("invalid modifier name '" + modName + "' (use a-z 0-9 _ - . /)");
                mods.add(new Mod(
                        modName,
                        ResourceLocation.parse(attr),
                        parseOperation(GsonHelper.getAsString(m, "operation", "multiply_total")),
                        GsonHelper.getAsDouble(m, "value")));
            }
            sections.put(c, List.copyOf(mods));
        }
        JsonObject source = json.deepCopy();
        source.addProperty("name", name); // so clients resolve the same identity
        return new Quality(name, color, weight, sections, source);
    }

    private static AttributeModifier.Operation parseOperation(String s) {
        return com.kragnoth.hallmarked.grade.Strict.operation(s);
    }
}
