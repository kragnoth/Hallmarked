package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a roll happens shapes its odds ({@code data/<ns>/regions/*.json}). Every matching region applies; luck adds,
 * multipliers multiply. Server-only.
 *
 * <pre>{@code
 * { "name": "hallmarked.region.frontier", "dimension": "minecraft:overworld",
 *   "min_distance": 3000, "max_distance": 10000, "min_y": -64, "max_y": 0,
 *   "sources": ["loot", "mob"], "luck": 0.75, "grade_multipliers": { "hallmarked.grade.gearwrought": 1.5 } }
 * }</pre>
 * Every condition is optional. {@code min_distance}/{@code max_distance} are horizontal blocks from world spawn.
 * {@code sources} defaults to loot and mob, so crafting somewhere special doesn't help.
 */
public record Region(String name, @Nullable ResourceLocation dimension, double minDistance, double maxDistance,
                     int minY, int maxY, List<String> sources, double luck, Map<String, Double> gradeMultipliers,
                     JsonObject source) {

    public boolean matches(String rollSource, @Nullable Level level, @Nullable Vec3 pos) {
        if (!sources.contains(rollSource) || level == null || pos == null) return false;
        if (dimension != null && !level.dimension().location().equals(dimension)) return false;
        if (pos.y < minY || pos.y > maxY) return false;
        if (minDistance > 0 || maxDistance < Double.MAX_VALUE) {
            BlockPos spawn = level.getSharedSpawnPos();
            double dx = pos.x - spawn.getX(), dz = pos.z - spawn.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < minDistance || dist > maxDistance) return false;
        }
        return true;
    }

    public static Region parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "dimension", "min_distance", "max_distance", "min_y", "max_y",
                "sources", "luck", "grade_multipliers", "enabled"), "Region");
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".region." + id.getPath());
        ResourceLocation dim = json.has("dimension") ? ResourceLocation.parse(GsonHelper.getAsString(json, "dimension")) : null;
        List<String> sources;
        if (json.has("sources")) {
            sources = new java.util.ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "sources")) sources.add(e.getAsString());
        } else {
            sources = List.of(Grading.SOURCE_LOOT, Grading.SOURCE_MOB);
        }
        Map<String, Double> mult = new HashMap<>();
        for (var e : GsonHelper.getAsJsonObject(json, "grade_multipliers", new JsonObject()).entrySet()) {
            mult.put(e.getKey(), Math.max(0.0, Math.min(100.0, e.getValue().getAsDouble())));
        }
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Region(name, dim,
                GsonHelper.getAsDouble(json, "min_distance", 0), GsonHelper.getAsDouble(json, "max_distance", Double.MAX_VALUE),
                GsonHelper.getAsInt(json, "min_y", Integer.MIN_VALUE), GsonHelper.getAsInt(json, "max_y", Integer.MAX_VALUE),
                List.copyOf(sources), Math.max(-10, Math.min(10, GsonHelper.getAsDouble(json, "luck", 0))), Map.copyOf(mult), source);
    }
}
