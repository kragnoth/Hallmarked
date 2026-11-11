package com.kragnoth.hallmarked.quality;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.Hallmarked;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the loaded qualities. Loaded from {@code data/<namespace>/qualities/*.json} on the server and
 * mirrored to clients over the network. A single immutable snapshot is swapped atomically.
 */
public final class Qualities {
    private Qualities() {}

    private static final Gson GSON = new GsonBuilder().create();
    private static volatile Map<String, Quality> byName = Map.of();

    public static Collection<Quality> all() {
        return byName.values();
    }

    @Nullable
    public static Quality get(@Nullable String name) {
        return name == null ? null : byName.get(Quality.normalizeName(name));
    }

    /** Replaces the active set. Order matters: a later entry with the same name overrides an earlier one. */
    public static void set(List<Quality> ordered) {
        Map<String, Quality> map = new LinkedHashMap<>();
        for (Quality q : ordered) {
            map.remove(q.name()); // keep "last wins" but re-insert at the end
            map.put(q.name(), q);
        }
        byName = java.util.Collections.unmodifiableMap(map); // keep load order (Map.copyOf scrambles it per JVM)
    }

    /** Server-side reload listener. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(GSON, "qualities");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            // Hallmarked's own files load first so any other namespace can override a quality by name.
            List<Map.Entry<ResourceLocation, JsonElement>> entries = new ArrayList<>(files.entrySet());
            entries.sort(Comparator
                    .comparing((Map.Entry<ResourceLocation, JsonElement> e) -> !e.getKey().getNamespace().equals(Hallmarked.MODID))
                    .thenComparing(e -> e.getKey().toString()));

            List<Quality> loaded = new ArrayList<>();
            java.util.Set<String> disabled = new java.util.HashSet<>();
            for (var e : entries) {
                try {
                    JsonObject json = e.getValue().getAsJsonObject();
                    if (json.has("enabled") && !json.get("enabled").getAsBoolean()) {
                        if (json.has("name")) disabled.add(Quality.normalizeName(json.get("name").getAsString()));
                        continue;
                    }
                    loaded.add(Quality.parse(e.getKey(), json));
                } catch (Exception ex) {
                    Hallmarked.LOG.error("Skipping malformed quality {}: {}", e.getKey(), ex.getMessage());
                }
            }
            loaded.removeIf(q -> disabled.contains(q.name()));
            set(loaded);
            Hallmarked.LOG.info("Loaded {} qualities", byName.size());
        }
    }

    /** Serialised form for client sync. */
    public static List<String> toWire() {
        List<String> out = new ArrayList<>();
        for (Quality q : byName.values()) out.add(q.source().toString());
        return out;
    }

    public static void fromWire(List<String> jsons) {
        List<Quality> list = new ArrayList<>();
        for (String s : jsons) {
            JsonObject o = GSON.fromJson(s, JsonObject.class);
            list.add(Quality.parse(Hallmarked.id("synced"), o));
        }
        set(list);
    }
}
