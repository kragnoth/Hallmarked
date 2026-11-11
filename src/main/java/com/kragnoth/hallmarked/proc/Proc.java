package com.kragnoth.hallmarked.proc;

import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.Strict;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A proc attached to an affix:
 * <pre>{@code { "type": "bleed", "chance": { "min": 0.08, "max": 0.2 }, "power": { "min": 0.5, "max": 1.5 }, "duration": 80 } }</pre>
 * {@code chance} and {@code power} scale with the affix's 0..1 roll; plain numbers are allowed too.
 */
public record Proc(ProcType type, double chanceMin, double chanceMax, double powerMin, double powerMax, int duration) {

    public double chance(float roll) { return chanceMin + (chanceMax - chanceMin) * roll; }
    public double power(float roll) { return powerMin + (powerMax - powerMin) * roll; }

    public static Proc parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("type", "chance", "power", "duration"), "Proc in");
        String typeId = GsonHelper.getAsString(json, "type");
        ProcType type = ProcType.byId(typeId);
        if (type == null) throw new IllegalArgumentException("unknown proc type '" + typeId + "'");
        if (!type.available()) throw new IllegalArgumentException("proc '" + typeId + "' needs a mod that isn't installed");
        double[] c = range(json, "chance", 1.0), p = range(json, "power", 0.0);
        return new Proc(type, clamp01(c[0]), clamp01(c[1]), p[0], p[1], Math.max(1, Math.min(20 * 60, GsonHelper.getAsInt(json, "duration", 60))));
    }

    private static double[] range(JsonObject json, String key, double def) {
        if (!json.has(key)) return new double[]{def, def};
        if (json.get(key).isJsonPrimitive()) {
            double v = json.get(key).getAsDouble();
            return new double[]{v, v};
        }
        JsonObject o = json.getAsJsonObject(key);
        return new double[]{GsonHelper.getAsDouble(o, "min"), GsonHelper.getAsDouble(o, "max")};
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
