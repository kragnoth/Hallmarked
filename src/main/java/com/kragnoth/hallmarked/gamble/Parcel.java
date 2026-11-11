package com.kragnoth.hallmarked.gamble;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Strict;
import com.kragnoth.hallmarked.quality.Category;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;

/**
 * A sealed lot a Mountebank sells ({@code data/<ns>/parcels/*.json}). Server-only: the parcel item carries its name.
 *
 * <pre>{@code
 * { "level": 1, "weight": 10, "color": "gray", "price": { "min": 3, "max": 6 },
 *   "second_cost": { "item": "minecraft:gold_ingot", "count": 1 },
 *   "categories": ["tool", "bow", "shield", "armor", "bauble"],
 *   "luck": -1.0, "quality_chance": 0.5, "curse_chance": 0.2, "exceptional_chance": 0.05, "set_chance": 0.0,
 *   "max_uses": 4, "xp": 2, "currency": "minecraft:emerald", "always": false }
 * }</pre>
 * {@code always}: offered by every Mountebank at its level (one such parcel per level is guaranteed a slot).
 * {@code currency}: what the price is paid in (default emerald; the Sworn Parcel takes Mountebank's Tokens).
 * {@code level} is the villager level (1 novice .. 5 master) that offers it; {@code categories} narrows what can be
 * inside (default: anything). Contents come from the {@code hallmarked:gamble/pool} item tag and roll with the grades'
 * {@code "gamble"} weights plus {@code luck}.
 */
public record Parcel(String name, int level, int weight, ChatFormatting color, int priceMin, int priceMax,
                     @Nullable Item secondItem, int secondCount, Set<Category> categories, double luck,
                     double qualityChance, double curseChance, double exceptionalChance, double setChance,
                     int maxUses, int xp, Item currency, boolean always, JsonObject source) {

    public static final DataStore<Parcel> STORE = new DataStore<>("parcels", Parcel::parse, Parcel::name, Parcel::source);

    public static Parcel parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "level", "weight", "color", "price", "second_cost", "categories",
                "luck", "quality_chance", "curse_chance", "exceptional_chance", "set_chance", "max_uses", "xp", "currency", "always"), "Parcel");
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".parcel." + id.getPath().replace('/', '.'));
        ChatFormatting color = ChatFormatting.getByName(GsonHelper.getAsString(json, "color", "gray"));
        if (color == null || !color.isColor()) color = ChatFormatting.GRAY;
        JsonObject price = GsonHelper.getAsJsonObject(json, "price");
        int min = Math.max(1, Math.min(64, GsonHelper.getAsInt(price, "min"))), max = Math.max(min, Math.min(64, GsonHelper.getAsInt(price, "max")));
        Item second = null;
        int secondCount = 0;
        if (json.has("second_cost")) {
            JsonObject sc = GsonHelper.getAsJsonObject(json, "second_cost");
            ResourceLocation itemId = ResourceLocation.parse(GsonHelper.getAsString(sc, "item"));
            second = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() -> new IllegalArgumentException("unknown item " + itemId));
            if (second == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("second_cost item can't be air");
            secondCount = Math.max(1, Math.min(64, GsonHelper.getAsInt(sc, "count", 1)));
        }
        Set<Category> cats = EnumSet.noneOf(Category.class);
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "categories", new JsonArray())) {
            cats.add(Category.valueOf(e.getAsString().toUpperCase(Locale.ROOT)));
        }
        if (cats.isEmpty()) cats = EnumSet.of(Category.TOOL, Category.BOW, Category.SHIELD, Category.ARMOR, Category.BAUBLE);
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Parcel(name, Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "level", 1))),
                Math.max(0, GsonHelper.getAsInt(json, "weight", 10)), color, min, max, second, secondCount, Set.copyOf(cats),
                GsonHelper.getAsDouble(json, "luck", 0), p(json, "quality_chance", 0.5), p(json, "curse_chance", 0.2),
                p(json, "exceptional_chance", 0.05), p(json, "set_chance", 0),
                Math.max(1, Math.min(64, GsonHelper.getAsInt(json, "max_uses", 4))), Math.max(0, GsonHelper.getAsInt(json, "xp", 2)),
                currency(json), GsonHelper.getAsBoolean(json, "always", false), source);
    }

    private static Item currency(JsonObject json) {
        if (!json.has("currency")) return net.minecraft.world.item.Items.EMERALD;
        ResourceLocation id = ResourceLocation.parse(GsonHelper.getAsString(json, "currency"));
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("unknown currency " + id));
        if (item == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("currency can't be air (the parcel would be free)");
        return item;
    }

    private static double p(JsonObject json, String key, double def) {
        return Math.max(0, Math.min(1, GsonHelper.getAsDouble(json, key, def)));
    }
}
