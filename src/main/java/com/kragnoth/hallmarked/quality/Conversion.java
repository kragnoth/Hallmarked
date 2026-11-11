package com.kragnoth.hallmarked.quality;

import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Strict;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Re-aims a quality stat that doesn't suit the tool it landed on ({@code data/<ns>/quality_conversions/*.json}).
 * Qualities are bundles written for "tools" in general; on a pure weapon or a pure digger, part of the bundle would
 * otherwise do nothing. Instead it becomes the matching stat for that role, so a Divine shovel is a Divine shovel.
 *
 * <pre>{@code
 * { "role": "weapon", "from": "minecraft:player.block_break_speed", "to": "minecraft:generic.attack_damage",
 *   "scale": 0.75, "operation": "multiply_total", "convert_negative": false }
 * }</pre>
 * Only tools that are a weapon or a digger but not both are affected (axes and unreadable tools keep the bundle as is).
 * {@code operation} defaults to the original modifier's. Negative values are left alone by default, so a penalty that
 * didn't bite before never starts biting.
 */
public record Conversion(String name, String role, ResourceLocation from, ResourceLocation to, double scale,
                         @Nullable AttributeModifier.Operation operation, boolean convertNegative, JsonObject source) {

    public static final DataStore<Conversion> STORE =
            new DataStore<>("quality_conversions", Conversion::parse, Conversion::name, Conversion::source);

    public static Conversion parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, java.util.Set.of("name", "role", "from", "to", "scale", "operation", "convert_negative"), "Quality conversion");
        String name = GsonHelper.getAsString(json, "name", id.toString());
        String role = GsonHelper.getAsString(json, "role").toLowerCase(Locale.ROOT);
        if (!role.equals("weapon") && !role.equals("digger")) throw new IllegalArgumentException("role must be weapon or digger");
        ResourceLocation from = ResourceLocation.parse(GsonHelper.getAsString(json, "from"));
        ResourceLocation to = ResourceLocation.parse(GsonHelper.getAsString(json, "to"));
        if (BuiltInRegistries.ATTRIBUTE.getHolder(to).isEmpty()) throw new IllegalArgumentException("unknown attribute " + to);
        AttributeModifier.Operation op = json.has("operation") ? Strict.operation(GsonHelper.getAsString(json, "operation")) : null;
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Conversion(name, role, from, to, GsonHelper.getAsDouble(json, "scale", 1.0), op,
                GsonHelper.getAsBoolean(json, "convert_negative", false), source);
    }

    public Holder<Attribute> target() {
        return BuiltInRegistries.ATTRIBUTE.getHolder(to).orElseThrow();
    }

    /** "weapon", "digger", or null when the item isn't exactly one of them (axes, bows, armor, unknown tools). */
    @Nullable
    public static String roleOf(ItemStack stack) {
        if (Equipment.categoryOf(stack) != Category.TOOL) return null;
        boolean weapon = Equipment.isWeapon(stack), digger = Equipment.isDigger(stack);
        if (weapon == digger) return null;
        return weapon ? "weapon" : "digger";
    }

    @Nullable
    public static Conversion find(@Nullable String role, ResourceLocation attribute, double value) {
        if (role == null) return null;
        for (Conversion c : STORE.all()) {
            if (c.role.equals(role) && c.from.equals(attribute) && (value > 0 || c.convertNegative)) return c;
        }
        return null;
    }
}
