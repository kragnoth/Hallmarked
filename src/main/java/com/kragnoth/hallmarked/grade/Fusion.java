package com.kragnoth.hallmarked.grade;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A named pairing of two affixes. Rolling is untouched: when an item has rolled both parts, they are
 * presented as one compound (same stats, same values) with a more meaningful name.
 *
 * <pre>{@code
 * { "name": "hallmarked.fusion.duelists", "type": "prefix",
 *   "parts": [ "hallmarked.affix.honed", "hallmarked.affix.of_haste" ],
 *   "title": "hallmarked.fusion.duelists.title", "priority": 0 }
 * }</pre>
 *
 * @param type     where the fused name sits in a Journeyman name ("prefix" or "suffix")
 * @param title    optional title noun; on titled grades it replaces the second title word ("Ashen Riposte")
 * @param priority higher fusions claim their parts first when two fusions want the same affix
 */
public record Fusion(String name, Affix.Type type, List<String> parts, @Nullable String title, int priority, JsonObject source) {

    public static Fusion parse(ResourceLocation id, JsonObject json) {
        String name = GsonHelper.getAsString(json, "name", id.getNamespace() + ".fusion." + id.getPath().replace('/', '.'));
        Affix.Type type = Affix.Type.valueOf(GsonHelper.getAsString(json, "type", "prefix").toUpperCase(Locale.ROOT));
        List<String> parts = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "parts")) parts.add(e.getAsString());
        if (parts.size() != 2) throw new IllegalArgumentException("a fusion needs exactly 2 parts");
        String title = json.has("title") ? GsonHelper.getAsString(json, "title") : null;
        JsonObject source = json.deepCopy();
        source.addProperty("name", name);
        return new Fusion(name, type, List.copyOf(parts), title, GsonHelper.getAsInt(json, "priority", 0), source);
    }
}
