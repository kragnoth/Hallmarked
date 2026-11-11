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
    public static final ModConfigSpec.BooleanValue APPRAISAL;
    public static final ModConfigSpec.BooleanValue PROCS;
    public static final ModConfigSpec.BooleanValue MOB_PROCS;
    public static final ModConfigSpec.DoubleValue SMART_DROP;
    public static final ModConfigSpec.IntValue TRADE_IN;

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
        APPRAISAL = b
                .comment("If true, found gear of grades marked \"appraise\" (Masterwork, Gearwrought, Unique by default) arrives",
                        "Unappraised: affixes hidden and dormant until struck at an Assay Anvil.",
                        "If false, nothing new is sealed, and Unappraised items a player carries are revealed automatically.",
                        "Overrides the per-grade \"appraise\" flag without a datapack edit.")
                .define("Appraisal", true);
        b.pop();

        b.push("Procs");
        PROCS = b.comment("Master switch for affix procs (bleed, stagger, leech, ignite, cleave, reprisal, reaper, anima).",
                        "Procs that need another mod only exist when that mod is installed.")
                .define("Enabled", true);
        MOB_PROCS = b.comment("If true, mobs wearing graded gear trigger its procs too.").define("MobsUseProcs", true);
        b.pop();

        b.push("Sets");
        SMART_DROP = b.comment("Chance that a set piece dropping for a player (chests, parcels) is a missing piece of a set",
                        "they have already started, instead of a random piece.")
                .defineInRange("SmartDropChance", 0.6, 0.0, 1.0);
        TRADE_IN = b.comment("Set pieces a Mountebank takes for one Mountebank's Token (hand them to him: right-click holding one).")
                .defineInRange("TradeInPieces", 3, 1, 64);
        b.pop();

        b.push("Balance");
        MAX_DAMAGE_RESISTANCE = b
                .comment("Upper cap for the hallmarked:damage_resistance reduction (0.8 = at most 80% less damage).")
                .defineInRange("MaxDamageResistance", 0.8, 0.0, 0.95);
        b.pop();

        SPEC = b.build();
    }
}
