package com.kragnoth.hallmarked.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player visual options (hallmarked-client.toml). */
public final class HallmarkedClientConfig {
    private HallmarkedClientConfig() {}

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ITEM_BEAMS;
    public static final ModConfigSpec.BooleanValue MOB_BEAMS;
    public static final ModConfigSpec.BooleanValue MOB_TITLES;
    public static final ModConfigSpec.IntValue BEAM_RANGE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("Beams");
        ITEM_BEAMS = b.comment("Light pillars on dropped items whose grade has \"beam\": true (Masterwork, Gearwrought).")
                .define("ItemBeams", true);
        MOB_BEAMS = b.comment("Light pillars on mobs carrying such gear.").define("MobBeams", true);
        MOB_TITLES = b.comment("Show \"<Mob>, Bearer of <item>\" over mobs carrying gear whose grade has \"champion\": true.")
                .define("MobTitles", true);
        BEAM_RANGE = b.comment("Max distance (blocks) at which beams are drawn.").defineInRange("Range", 48, 8, 256);
        b.pop();
        SPEC = b.build();
    }
}
