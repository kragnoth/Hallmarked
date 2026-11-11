package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A grade of work: how many affixes an item carries and how strongly they roll.
 *
 * <pre>{@code
 * { "name": "hallmarked.grade.masterwork", "color": "yellow", "rank": 3,
 *   "affixes": { "min": 3, "max": 4 }, "max_prefixes": 2, "max_suffixes": 2, "flaws": 0,
 *   "roll": { "min": 0.25, "max": 0.85 }, "titled": true, "hidden": false,
 *   "luck_scaling": 2.0, "weights": { "crafted": 7.5, "loot": 19 } }
 * }</pre>
 *
 * @param rank        display/sort order (higher is better)
 * @param titled      gets a generated two-word title instead of a prefix/suffix name
 * @param hidden      no grade line in the tooltip (used for Common)
 * @param luckScaling how strongly Luck pushes this grade's weight up (negative pushes it down)
 * @param weights     base weight per roll source ("crafted", "loot", or any source a loot modifier names)
 * @param inlaySlots  how many inlays this grade of work accepts at the smithing table
 * @param inlayTint   if set, an inlaid item of this grade shows its (otherwise plain) name in this colour
 */
public record Grade(String name, ChatFormatting color, int rank, int minAffixes, int maxAffixes,
                    int maxPrefixes, int maxSuffixes, int flaws, float rollMin, float rollMax,
                    boolean titled, boolean hidden, double luckScaling, Map<String, Double> weights,
                    int inlaySlots, @org.jetbrains.annotations.Nullable net.minecraft.network.chat.TextColor inlayTint,
                    JsonObject source) {

    /** Weight for a roll source. Custom sources a grade doesn't list fall back to "loot", then "crafted". */
    public double weight(String source) {
        Double w = weights.get(source);
        if (w == null) w = weights.get("loot");
        if (w == null) w = weights.get("crafted");
        return w == null ? 0.0 : w;
    }

    public static Grade parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".grade." + id.getPath());
        ChatFormatting color = ChatFormatting.getByName(GsonHelper.getAsString(json, "color", "white"));
        JsonObject affixes = GsonHelper.getAsJsonObject(json, "affixes", new JsonObject());
        JsonObject roll = GsonHelper.getAsJsonObject(json, "roll", new JsonObject());
        JsonObject w = GsonHelper.getAsJsonObject(json, "weights", new JsonObject());
        Map<String, Double> weights = new HashMap<>();
        for (var e : w.entrySet()) weights.put(e.getKey(), Math.min(1.0e6, Math.max(0.0, e.getValue().getAsDouble())));

        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Grade(name, color == null ? ChatFormatting.WHITE : color,
                GsonHelper.getAsInt(json, "rank", 0),
                GsonHelper.getAsInt(affixes, "min", 0), GsonHelper.getAsInt(affixes, "max", 0),
                GsonHelper.getAsInt(json, "max_prefixes", 3), GsonHelper.getAsInt(json, "max_suffixes", 3),
                GsonHelper.getAsInt(json, "flaws", 0),
                GsonHelper.getAsFloat(roll, "min", 0f), GsonHelper.getAsFloat(roll, "max", 1f),
                GsonHelper.getAsBoolean(json, "titled", false), GsonHelper.getAsBoolean(json, "hidden", false),
                GsonHelper.getAsDouble(json, "luck_scaling", 0.0), Map.copyOf(weights),
                Math.max(0, GsonHelper.getAsInt(json, "inlay_slots", 0)),
                json.has("inlay_tint") ? net.minecraft.network.chat.TextColor.parseColor(GsonHelper.getAsString(json, "inlay_tint")).result().orElse(null) : null,
                source);
    }
}
