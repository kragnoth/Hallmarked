package com.kragnoth.hallmarked.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HallmarkedConfig {
    private HallmarkedConfig() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.ConfigValue<String> FALLBACK_MATERIAL;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> MATERIAL_OVERRIDES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.BooleanValue AUTO_REFORGE;
    public static final ModConfigSpec.DoubleValue AUTO_REFORGE_CHANCE;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_RESISTANCE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("Materials");
        FALLBACK_MATERIAL = b
                .comment("This material can reforge any equipment, even equipment with no override or repair item.")
                .define("Fallback", "minecraft:obsidian");
        MATERIAL_OVERRIDES = b
                .comment("Extra reforge materials for specific equipment, as \"equipment,material\" pairs.",
                        "These are accepted in addition to the equipment's normal repair items and the fallback.")
                .defineListAllowEmpty("Overrides",
                        List.of("minecraft:bow,minecraft:string",
                                "minecraft:crossbow,minecraft:iron_ingot",
                                "hallmarked:emerald_ring,minecraft:emerald",
                                "hallmarked:emerald_amulet,minecraft:emerald"),
                        () -> "minecraft:bow,minecraft:string",
                        o -> o instanceof String s && s.contains(","));
        BLACKLIST = b
                .comment("Equipment that can never receive a quality (neither at the Assay Anvil nor automatically).")
                .defineListAllowEmpty("Blacklist", List.of(), () -> "minecraft:stick", o -> o instanceof String);
        b.pop();

        b.push("Reforging");
        AUTO_REFORGE = b
                .comment("If true, equipment gets one chance to roll a quality the first time it enters a player's inventory.")
                .define("AutoReforge", true);
        AUTO_REFORGE_CHANCE = b
                .comment("Chance for that one-time roll to apply a quality.")
                .defineInRange("AutoReforgeChance", 0.5, 0.1, 1.0);
        b.pop();

        b.push("Balance");
        MAX_DAMAGE_RESISTANCE = b
                .comment("Upper cap for the hallmarked:damage_resistance reduction (0.8 = at most 80% less damage).")
                .defineInRange("MaxDamageResistance", 0.8, 0.0, 0.95);
        b.pop();

        SPEC = b.build();
    }
}
