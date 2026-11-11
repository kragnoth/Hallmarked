package com.kragnoth.hallmarked.sets;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.Strict;
import com.kragnoth.hallmarked.proc.Proc;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Sectioned;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.DyeColor;

/**
 * A named set of gear ({@code data/<ns>/sets/*.json}): each piece is an ordinary item dressed in the set's look
 * (dye, armor trim, shield colour) with its own modifiers, and wearing several pieces unlocks bonuses.
 *
 * <pre>{@code
 * { "color": "gold", "chance": { "loot": 0.004, "mob": 0.001 },
 *   "pieces": [
 *     { "key": "helm", "item": "minecraft:iron_helmet", "dye": "#3b2a5a",
 *       "trim": { "pattern": "minecraft:ward", "material": "minecraft:gold" }, "base_color": "blue", "glint": false,
 *       "modifiers": [ { "name": "x_helm_armor", "attribute": "minecraft:generic.armor", "operation": "addition", "value": 1 } ] } ],
 *   "bonuses": [ { "pieces": 2, "modifiers": [ ... ], "procs": [ ... ] } ] }
 * }</pre>
 * A modifier whose attribute isn't registered (its mod isn't installed) is skipped, not an error, so one set can
 * carry Iron's Spellbooks, Lodestone and Apothic stats and still work in a pack with none of them.
 * {@code chance} is per roll source: when an item matching a piece's base item is graded from that source, it may
 * become that piece instead.
 */
public record GearSet(String name, ChatFormatting color, Map<String, Double> chance, List<Piece> pieces,
                      List<Bonus> bonuses, JsonObject source) {

    public record Piece(String key, String name, ResourceLocation item, @Nullable Integer dye,
                        @Nullable ResourceLocation trimPattern, @Nullable ResourceLocation trimMaterial,
                        @Nullable DyeColor baseColor, boolean glint, List<Affix.Mod> modifiers) implements Sectioned {
        @Override public boolean has(Category c) { return true; }
        public List<Affix.Mod> section(Category c) { return modifiers; }
    }

    public record Bonus(int pieces, List<Affix.Mod> modifiers, List<Proc> procs) {}

    public double chance(String source) {
        return chance.getOrDefault(source, 0.0);
    }

    @Nullable
    public Piece piece(String key) {
        for (Piece p : pieces) if (p.key.equals(key)) return p;
        return null;
    }

    public static GearSet parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "color", "chance", "pieces", "bonuses"), "Set");
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".set." + id.getPath().replace('/', '.'));
        ChatFormatting color = ChatFormatting.getByName(GsonHelper.getAsString(json, "color", "green"));
        if (color == null || !color.isColor()) color = ChatFormatting.GREEN;
        Map<String, Double> chance = new HashMap<>();
        for (var e : GsonHelper.getAsJsonObject(json, "chance", new JsonObject()).entrySet()) {
            chance.put(e.getKey(), Math.max(0, Math.min(1, e.getValue().getAsDouble())));
        }
        List<Piece> pieces = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "pieces")) {
            JsonObject p = e.getAsJsonObject();
            Strict.warnUnknownKeys(id, p, Set.of("key", "name", "item", "dye", "trim", "base_color", "glint", "modifiers"), "Set piece in");
            String key = GsonHelper.getAsString(p, "key");
            Integer dye = p.has("dye") ? Integer.parseInt(GsonHelper.getAsString(p, "dye").replace("#", ""), 16) : null;
            ResourceLocation pattern = null, material = null;
            if (p.has("trim")) {
                JsonObject t = GsonHelper.getAsJsonObject(p, "trim");
                pattern = ResourceLocation.parse(GsonHelper.getAsString(t, "pattern"));
                material = ResourceLocation.parse(GsonHelper.getAsString(t, "material"));
            }
            DyeColor base = p.has("base_color") ? DyeColor.byName(GsonHelper.getAsString(p, "base_color").toLowerCase(Locale.ROOT), null) : null;
            pieces.add(new Piece(key, GsonHelper.getAsString(p, "name", name + "." + key),
                    ResourceLocation.parse(GsonHelper.getAsString(p, "item")), dye, pattern, material, base,
                    GsonHelper.getAsBoolean(p, "glint", false), mods(id, p, name + "_" + key)));
        }
        if (pieces.isEmpty()) throw new IllegalArgumentException("a set needs at least one piece");
        List<Bonus> bonuses = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "bonuses", new JsonArray())) {
            JsonObject b = e.getAsJsonObject();
            Strict.warnUnknownKeys(id, b, Set.of("pieces", "modifiers", "procs"), "Set bonus in");
            List<Proc> procs = new ArrayList<>();
            for (JsonElement pe : GsonHelper.getAsJsonArray(b, "procs", new JsonArray())) {
                try {
                    procs.add(Proc.parse(id, pe.getAsJsonObject()));
                } catch (IllegalArgumentException ex) {
                    // a proc whose mod is missing: the rest of the bonus still applies
                }
            }
            bonuses.add(new Bonus(Math.max(1, GsonHelper.getAsInt(b, "pieces")), mods(id, b, name), List.copyOf(procs)));
        }
        bonuses.sort((a, b) -> Integer.compare(a.pieces, b.pieces));
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new GearSet(name, color, Map.copyOf(chance), List.copyOf(pieces), List.copyOf(bonuses), source);
    }

    /** Fixed-value modifiers; attributes from mods that aren't installed are dropped quietly. */
    private static List<Affix.Mod> mods(ResourceLocation id, JsonObject json, String fallbackPrefix) {
        List<Affix.Mod> out = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "modifiers", new JsonArray())) {
            JsonObject m = e.getAsJsonObject();
            Strict.warnUnknownKeys(id, m, Strict.MODIFIER_KEYS, "Set modifier in");
            String modName = GsonHelper.getAsString(m, "name").toLowerCase(Locale.ROOT);
            if (!ResourceLocation.isValidPath(modName)) throw new IllegalArgumentException("invalid modifier name '" + modName + "'");
            double v = GsonHelper.getAsDouble(m, "value");
            Affix.Mod mod = new Affix.Mod(modName, ResourceLocation.parse(GsonHelper.getAsString(m, "attribute").toLowerCase(Locale.ROOT)),
                    Strict.operation(GsonHelper.getAsString(m, "operation", "addition")), v, v);
            if (mod.attribute().isPresent()) out.add(mod);
        }
        return List.copyOf(out);
    }
}
