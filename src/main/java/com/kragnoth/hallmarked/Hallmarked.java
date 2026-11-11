package com.kragnoth.hallmarked;

import com.kragnoth.hallmarked.compat.BetterCombatCompat;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.network.HallmarkedNetwork;
import com.kragnoth.hallmarked.registry.HMRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Hallmarked.MODID)
public final class Hallmarked {
    public static final String MODID = "hallmarked";
    public static final Logger LOG = LoggerFactory.getLogger("Hallmarked");

    public Hallmarked(IEventBus modBus, ModContainer container) {
        HMRegistries.register(modBus);
        modBus.addListener(HallmarkedNetwork::register);
        container.registerConfig(ModConfig.Type.COMMON, HallmarkedConfig.SPEC);

        if (ModList.get().isLoaded("bettercombat")) {
            modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(BetterCombatCompat::init));
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static Boolean curios;

    /** Cached: called from tick and tooltip paths. */
    public static boolean curiosLoaded() {
        Boolean c = curios;
        if (c == null) curios = c = ModList.get().isLoaded("curios");
        return c;
    }
}
