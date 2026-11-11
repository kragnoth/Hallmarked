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
                      List<Bonus> bonuses, List<TableEntry> tables, JsonObject source) {

    /**
     * @param baseStats replaces the item's own base stats (keys: armor, toughness, knockback_resistance,
     *                  attack_damage, attack_speed; values as vanilla stores them, e.g. a netherite sword is
     *                  attack_damage 7, attack_speed -2.4), so chainmail can carry netherite protection
     * @param durability replaces the item's own max durability (before the Set grade's multiplier)
     * @param fireResistant survives fire and lava as an item, like netherite
     */
    public record Piece(String key, String name, ResourceLocation item, @Nullable Integer dye,
                        @Nullable ResourceLocation trimPattern, @Nullable ResourceLocation trimMaterial,
                        @Nullable DyeColor baseColor, boolean glint, List<Affix.Mod> modifiers,
                        Map<String, Double> baseStats, @Nullable Integer durability, boolean fireResistant) implements Sectioned {
        @Override public boolean has(Category c) { return true; }
        public List<Affix.Mod> section(Category c) { return modifiers; }
    }

    /**
     * @param perPiece the modifiers are multiplied by the number of pieces worn (once at least {@code pieces} are worn);
     *                 procs on a per-piece bonus don't scale
     */
    public record Bonus(int pieces, boolean perPiece, List<Affix.Mod> modifiers, List<Proc> procs, List<Effect> effects) {}

    /** A status effect kept on the wearer while the bonus is active (fire resistance for lava diving, and so on). */
    public record Effect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {}

    /**
     * Extra chance for a piece to turn up in specific loot tables, on top of base items rolled there.
     * {@code tables} use a trailing {@code *} as a prefix match; {@code pieces} limits which pieces fit the place
     * (empty: any).
     */
    public record TableEntry(List<String> tables, double chance, List<String> pieces) {
        public boolean matches(ResourceLocation table) {
            String id = table.toString();
            for (String t : tables) if (t.endsWith("*") ? id.startsWith(t.substring(0, t.length() - 1)) : id.equals(t)) return true;
            return false;
        }
    }

    public double chance(String source) {
        return chance.getOrDefault(source, 0.0);
    }

    @Nullable
    public Piece piece(String key) {
        for (Piece p : pieces) if (p.key.equals(key)) return p;
        return null;
    }

    public static GearSet parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "color", "chance", "pieces", "bonuses", "tables"), "Set");
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
            Strict.warnUnknownKeys(id, p, Set.of("key", "name", "item", "dye", "trim", "base_color", "glint", "modifiers",
                    "base_stats", "durability", "fire_resistant"), "Set piece in");
            Map<String, Double> baseStats = new java.util.LinkedHashMap<>();
            for (var bs : GsonHelper.getAsJsonObject(p, "base_stats", new JsonObject()).entrySet()) {
                if (!Set.of("armor", "toughness", "knockback_resistance", "attack_damage", "attack_speed").contains(bs.getKey()))
                    throw new IllegalArgumentException("unknown base stat '" + bs.getKey() + "'");
                baseStats.put(bs.getKey(), bs.getValue().getAsDouble());
            }
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
                    GsonHelper.getAsBoolean(p, "glint", false), mods(id, p, name + "_" + key), Map.copyOf(baseStats),
                    p.has("durability") ? Math.max(1, GsonHelper.getAsInt(p, "durability")) : null,
                    GsonHelper.getAsBoolean(p, "fire_resistant", false)));
        }
        if (pieces.isEmpty()) throw new IllegalArgumentException("a set needs at least one piece");
        List<Bonus> bonuses = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "bonuses", new JsonArray())) {
            JsonObject b = e.getAsJsonObject();
            Strict.warnUnknownKeys(id, b, Set.of("pieces", "per_piece", "modifiers", "procs", "effects"), "Set bonus in");
            List<Proc> procs = new ArrayList<>();
            for (JsonElement pe : GsonHelper.getAsJsonArray(b, "procs", new JsonArray())) {
                try {
                    procs.add(Proc.parse(id, pe.getAsJsonObject()));
                } catch (IllegalArgumentException ex) {
                    // a proc whose mod is missing: the rest of the bonus still applies
                }
            }
            List<Effect> effects = new ArrayList<>();
            for (JsonElement ee : GsonHelper.getAsJsonArray(b, "effects", new JsonArray())) {
                JsonObject eo = ee.getAsJsonObject();
                Strict.warnUnknownKeys(id, eo, Set.of("effect", "amplifier"), "Set effect in");
                net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getHolder(ResourceLocation.parse(GsonHelper.getAsString(eo, "effect")))
                        .ifPresent(h -> effects.add(new Effect(h, Math.max(0, Math.min(9, GsonHelper.getAsInt(eo, "amplifier", 0))))));
            }
            bonuses.add(new Bonus(Math.max(1, GsonHelper.getAsInt(b, "pieces", 1)), GsonHelper.getAsBoolean(b, "per_piece", false),
                    mods(id, b, name), List.copyOf(procs), List.copyOf(effects)));
        }
        List<TableEntry> tables = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "tables", new JsonArray())) {
            JsonObject t = e.getAsJsonObject();
            Strict.warnUnknownKeys(id, t, Set.of("tables", "chance", "pieces"), "Set table entry in");
            List<String> ids = new ArrayList<>(), keys = new ArrayList<>();
            for (JsonElement x : GsonHelper.getAsJsonArray(t, "tables")) ids.add(x.getAsString());
            for (JsonElement x : GsonHelper.getAsJsonArray(t, "pieces", new JsonArray())) keys.add(x.getAsString());
            tables.add(new TableEntry(List.copyOf(ids), Math.max(0, Math.min(1, GsonHelper.getAsDouble(t, "chance"))), List.copyOf(keys)));
        }
        bonuses.sort((a, b) -> Integer.compare(a.pieces, b.pieces));
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new GearSet(name, color, Map.copyOf(chance), List.copyOf(pieces), List.copyOf(bonuses), List.copyOf(tables), source);
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
