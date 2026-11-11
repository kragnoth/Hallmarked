package com.kragnoth.hallmarked.tempering;

import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.Strict;
import java.util.Locale;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * One possible result of tempering at the Ashen Altar ({@code data/<ns>/tempering/*.json}).
 * Every outcome locks the item as Tempered: no further tempering, reforging or inlays.
 *
 * <pre>{@code { "name": "hallmarked.tempering.ascend", "effect": "ascend", "weight": 25 } }</pre>
 * Effects: {@code ascend} (next grade up, affixes re-rolled), {@code forbidden} (adds a forbidden affix beyond the
 * normal limits), {@code flaw} (adds a flaw), {@code nothing} (just locked).
 */
public record TemperOutcome(String name, Effect effect, int weight, JsonObject source) {
    public enum Effect { ASCEND, FORBIDDEN, FLAW, NOTHING }

    public static TemperOutcome parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "effect", "weight", "enabled"), "Tempering outcome");
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".tempering." + id.getPath());
        Effect effect;
        try {
            effect = Effect.valueOf(GsonHelper.getAsString(json, "effect").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown effect (use ascend, forbidden, flaw, nothing)");
        }
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new TemperOutcome(name, effect, Math.min(1_000_000, Math.max(0, GsonHelper.getAsInt(json, "weight", 10))), source);
    }
}
