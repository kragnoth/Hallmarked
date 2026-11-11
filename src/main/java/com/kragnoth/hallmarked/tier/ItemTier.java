package com.kragnoth.hallmarked.tier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Strict;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;

/**
 * Material tiers cap how good a base item can roll ({@code data/<ns>/item_tiers/*.json}), Diablo item-level style:
 * a wooden sword shouldn't routinely turn up Gearwrought. Server-only.
 *
 * <pre>{@code
 * { "items": ["#hallmarked:tier/crude"], "tool_tiers": ["wood"], "armor_materials": ["minecraft:leather"],
 *   "max_grade": "hallmarked.grade.journeyman", "max_roll": 0.6, "overflow_grade": "hallmarked.grade.common",
 *   "blocked_qualities": ["hallmarked.quality.ascended"], "priority": 0 }
 * }</pre>
 * An item matches through {@code items} (ids or #tags) first, then, for untagged gear, its vanilla tool tier
 * ({@code wood}, {@code stone}, {@code iron}, {@code gold}, {@code diamond}, {@code netherite}) or armor material id.
 * The first match by priority (high first) wins. Gear in no tier is uncapped. A roll above {@code max_grade} becomes
 * {@code overflow_grade} (Common by default), so a better material always means better odds.
 */
public record ItemTier(String name, List<String> items, Set<String> toolTiers, Set<ResourceLocation> armorMaterials,
                       String maxGrade, float maxRoll, String overflowGrade, Set<String> blockedQualities, int priority, JsonObject source) {

    public static final DataStore<ItemTier> STORE = new DataStore<>("item_tiers", ItemTier::parse, ItemTier::name, ItemTier::source);

    public static ItemTier parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "items", "tool_tiers", "armor_materials", "max_grade", "max_roll",
                "overflow_grade", "blocked_qualities", "priority"), "Item tier");
        String name = GsonHelper.getAsString(json, "name", id.toString());
        List<String> items = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "items", new com.google.gson.JsonArray())) items.add(e.getAsString());
        Set<String> toolTiers = new java.util.HashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "tool_tiers", new com.google.gson.JsonArray()))
            toolTiers.add(e.getAsString().toLowerCase(Locale.ROOT));
        Set<ResourceLocation> armor = new java.util.HashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "armor_materials", new com.google.gson.JsonArray()))
            armor.add(ResourceLocation.parse(e.getAsString()));
        Set<String> blocked = new java.util.HashSet<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "blocked_qualities", new com.google.gson.JsonArray())) blocked.add(e.getAsString());
        String maxGrade = GsonHelper.getAsString(json, "max_grade");
        float maxRoll = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(json, "max_roll", 1f)));
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new ItemTier(name, List.copyOf(items), Set.copyOf(toolTiers), Set.copyOf(armor), maxGrade, maxRoll,
                GsonHelper.getAsString(json, "overflow_grade", "hallmarked.grade.common"),
                Set.copyOf(blocked), GsonHelper.getAsInt(json, "priority", 0), source);
    }

    public boolean matches(ItemStack stack) {
        Item item = stack.getItem();
        for (String s : items) {
            if (s.startsWith("#")) {
                if (stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(s.substring(1))))) return true;
            } else if (BuiltInRegistries.ITEM.getKey(item).toString().equals(s)) {
                return true;
            }
        }
        if (!toolTiers.isEmpty() && item instanceof TieredItem t && t.getTier() instanceof Tiers vanilla
                && toolTiers.contains(vanilla.name().toLowerCase(Locale.ROOT))) return true;
        if (!armorMaterials.isEmpty() && item instanceof ArmorItem a) {
            var key = a.getMaterial().unwrapKey();
            if (key.isPresent() && armorMaterials.contains(key.get().location())) return true;
        }
        return false;
    }

    @Nullable
    public static ItemTier of(ItemStack stack) {
        ItemTier best = null;
        for (ItemTier t : STORE.all()) {
            if ((best == null || t.priority > best.priority) && t.matches(stack)) best = t;
        }
        return best;
    }
}
