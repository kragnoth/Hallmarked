package com.kragnoth.hallmarked.grade;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.Hallmarked;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Word lists for titled grades, from {@code data/<namespace>/titles/*.json}:
 * <pre>{@code { "replace": false, "first": ["Ashen", ...], "second": ["Vow", ...] } }</pre>
 * Files merge; {@code "replace": true} discards everything loaded before it (Hallmarked's own files load first).
 * The chosen words are stored on the item, so editing the lists never renames existing gear.
 * Server-side only: clients just see the stored name.
 */
public final class TitleWords {
    private TitleWords() {}

    private static volatile List<String> first = List.of("Nameless");
    private static volatile List<String> second = List.of("Work");

    public static String randomFirst(RandomSource r) {
        List<String> l = first;
        return l.get(r.nextInt(l.size()));
    }

    public static String randomSecond(RandomSource r) {
        List<String> l = second;
        return l.get(r.nextInt(l.size()));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(new Gson(), "titles");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
            List<Map.Entry<ResourceLocation, JsonElement>> entries = new ArrayList<>(files.entrySet());
            entries.sort(Comparator
                    .comparing((Map.Entry<ResourceLocation, JsonElement> e) -> !e.getKey().getNamespace().equals(Hallmarked.MODID))
                    .thenComparing(e -> e.getKey().toString()));
            List<String> a = new ArrayList<>(), b = new ArrayList<>();
            for (var e : entries) {
                try {
                    JsonObject o = e.getValue().getAsJsonObject();
                    if (GsonHelper.getAsBoolean(o, "replace", false)) {
                        a.clear();
                        b.clear();
                    }
                    for (JsonElement w : GsonHelper.getAsJsonArray(o, "first", new com.google.gson.JsonArray())) addUnique(a, w.getAsString());
                    for (JsonElement w : GsonHelper.getAsJsonArray(o, "second", new com.google.gson.JsonArray())) addUnique(b, w.getAsString());
                } catch (Exception ex) {
                    Hallmarked.LOG.error("Skipping malformed title list {}: {}", e.getKey(), ex.getMessage());
                }
            }
            if (!a.isEmpty()) first = List.copyOf(a);
            if (!b.isEmpty()) second = List.copyOf(b);
            Hallmarked.LOG.info("Loaded {} + {} title words", first.size(), second.size());
        }

        private static void addUnique(List<String> list, String word) {
            String w = word.trim();
            if (!w.isEmpty() && !list.contains(w)) list.add(w);
        }
    }
}
