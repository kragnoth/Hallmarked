package com.kragnoth.hallmarked.grade;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.Hallmarked;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

/**
 * A name-keyed set of JSON definitions loaded from {@code data/<namespace>/<folder>/}.
 * Hallmarked's own files load first so any other namespace can override an entry by name.
 * Held as one immutable snapshot; mirrored to clients over the network.
 */
public final class DataStore<T> {
    private static final Gson GSON = new Gson();

    private final String folder;
    private final BiFunction<ResourceLocation, JsonObject, T> parser;
    private final Function<T, String> nameOf;
    private final Function<T, JsonObject> sourceOf;
    private volatile Map<String, T> byName = Map.of();

    public DataStore(String folder, BiFunction<ResourceLocation, JsonObject, T> parser,
                     Function<T, String> nameOf, Function<T, JsonObject> sourceOf) {
        this.folder = folder;
        this.parser = parser;
        this.nameOf = nameOf;
        this.sourceOf = sourceOf;
    }

    public Collection<T> all() {
        return byName.values();
    }

    @Nullable
    public T get(@Nullable String name) {
        return name == null ? null : byName.get(name);
    }

    private void set(List<T> ordered) {
        Map<String, T> map = new LinkedHashMap<>();
        for (T t : ordered) {
            map.remove(nameOf.apply(t));
            map.put(nameOf.apply(t), t);
        }
        byName = Map.copyOf(map);
    }

    public SimpleJsonResourceReloadListener loader() {
        return new SimpleJsonResourceReloadListener(GSON, folder) {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                List<Map.Entry<ResourceLocation, JsonElement>> entries = new ArrayList<>(files.entrySet());
                entries.sort(Comparator
                        .comparing((Map.Entry<ResourceLocation, JsonElement> e) -> !e.getKey().getNamespace().equals(Hallmarked.MODID))
                        .thenComparing(e -> e.getKey().toString()));
                List<T> loaded = new ArrayList<>();
                for (var e : entries) {
                    try {
                        loaded.add(parser.apply(e.getKey(), e.getValue().getAsJsonObject()));
                    } catch (Exception ex) {
                        Hallmarked.LOG.error("Skipping malformed {} entry {}: {}", folder, e.getKey(), ex.getMessage());
                    }
                }
                set(loaded);
                Hallmarked.LOG.info("Loaded {} {}", byName.size(), folder);
            }
        };
    }

    public List<String> toWire() {
        List<String> out = new ArrayList<>();
        for (T t : byName.values()) out.add(sourceOf.apply(t).toString());
        return out;
    }

    public void fromWire(List<String> jsons) {
        List<T> list = new ArrayList<>();
        for (String s : jsons) list.add(parser.apply(Hallmarked.id("synced"), GSON.fromJson(s, JsonObject.class)));
        set(list);
    }
}
