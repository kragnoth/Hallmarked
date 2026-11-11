package com.kragnoth.hallmarked.config;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class HallmarkedConfig {
    private HallmarkedConfig() {}

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue AUTO_REFORGE;
    public static final ModConfigSpec.DoubleValue AUTO_REFORGE_CHANCE;
    public static final ModConfigSpec.DoubleValue MAX_DAMAGE_RESISTANCE;
    public static final ModConfigSpec.BooleanValue GRADING;
    public static final ModConfigSpec.DoubleValue LUCK_FACTOR;
    public static final ModConfigSpec.BooleanValue MOB_EQUIPMENT;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        // Reforge materials are recipes (type hallmarked:reforge_material) and exclusions are the
        // hallmarked:equipment/excluded item tag. Both sync to clients, unlike a common config.

        b.push("Reforging");
        AUTO_REFORGE = b
                .comment("If true, equipment gets one chance to roll a quality the first time it enters a player's inventory.")
                .define("AutoReforge", true);
        AUTO_REFORGE_CHANCE = b
                .comment("Chance for that one-time roll to apply a quality.")
                .defineInRange("AutoReforgeChance", 0.5, 0.1, 1.0);
        b.pop();

        b.push("Grading");
        GRADING = b
                .comment("If true, equipment is given a grade of work (Shoddy .. Gearwrought) with rolled affixes.",
                        "Loot rolls on generation; everything else rolls the first time it enters a player's inventory.",
                        "Grade odds and affix counts live in data/<namespace>/grades/*.json.")
                .define("Enabled", true);
        LUCK_FACTOR = b
                .comment("How strongly the Luck attribute shifts grade odds. Each grade's weight is multiplied by",
                        "(1 + luck * LuckFactor * luck_scaling); luck_scaling is set per grade (negative for Shoddy).")
                .defineInRange("LuckFactor", 0.15, 0.0, 10.0);
        MOB_EQUIPMENT = b
                .comment("If true, equipment mobs spawn with is graded (and may get a quality) when they enter the world,",
                        "using the grades' \"mob\" weights. Mobs fight with those stats, and drop graded gear.")
                .define("MobEquipment", true);
        b.pop();

        b.push("Balance");
        MAX_DAMAGE_RESISTANCE = b
                .comment("Upper cap for the hallmarked:damage_resistance reduction (0.8 = at most 80% less damage).")
                .defineInRange("MaxDamageResistance", 0.8, 0.0, 0.95);
        b.pop();

        SPEC = b.build();
    }
}
