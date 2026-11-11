package com.kragnoth.hallmarked.tier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.grade.DataStore;
import com.kragnoth.hallmarked.grade.Strict;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * Per-loot-table odds ({@code data/<ns>/loot_odds/*.json}): early chests roll worse, late ones better. Server-only.
 *
 * <pre>{@code
 * { "tables": ["minecraft:chests/village/*", "minecraft:chests/spawn_bonus_chest"], "luck": -1.0, "cap_shift": 0 }
 * }</pre>
 * {@code tables} are loot table ids; a trailing {@code *} matches any id with that prefix. Every matching entry applies:
 * luck adds (like regions), and {@code cap_shift} raises a material tier's grade cap by that many grades (and lifts its
 * roll cap), because a wooden sword that turns up in an ancient city is there for a reason.
 */
public record LootOdds(String name, List<String> tables, double luck, int capShift, JsonObject source) {

    public static final DataStore<LootOdds> STORE = new DataStore<>("loot_odds", LootOdds::parse, LootOdds::name, LootOdds::source);

    public static LootOdds parse(ResourceLocation id, JsonObject json) {
        Strict.warnUnknownKeys(id, json, Set.of("name", "tables", "luck", "cap_shift"), "Loot odds");
        String name = GsonHelper.getAsString(json, "name", id.toString());
        List<String> tables = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "tables")) tables.add(e.getAsString());
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new LootOdds(name, List.copyOf(tables), Math.max(-10, Math.min(10, GsonHelper.getAsDouble(json, "luck", 0))),
                Math.max(0, Math.min(5, GsonHelper.getAsInt(json, "cap_shift", 0))), source);
    }

    public boolean matches(ResourceLocation table) {
        String id = table.toString();
        for (String t : tables) {
            if (t.endsWith("*") ? id.startsWith(t.substring(0, t.length() - 1)) : id.equals(t)) return true;
        }
        return false;
    }

    /** Summed luck and cap shift for a table; zeros when there's no table. */
    public record Effect(double luck, int capShift) {
        public static final Effect NONE = new Effect(0, 0);
    }

    public static Effect forTable(@Nullable ResourceLocation table) {
        if (table == null) return Effect.NONE;
        double luck = 0;
        int shift = 0;
        for (LootOdds o : STORE.all()) {
            if (o.matches(table)) {
                luck += o.luck;
                shift += o.capShift;
            }
        }
        return new Effect(luck, shift);
    }
}
