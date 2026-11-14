package com.kragnoth.hallmarked.qa;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs {@link QaSuite} on a headless dedicated server a few seconds after start, then stops the server. */
@Mod("hallmarked_qa")
public final class QaMod {
    private static QaSuite suite;
    private static int ticks;

    public QaMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            MinecraftServer server = e.getServer();
            if (!server.isDedicatedServer()) return;
            ticks++;
            if (ticks == 60) suite = new QaSuite(server);
            if (suite != null && ticks > 60 && suite.step(ticks)) {
                suite = null;
                server.halt(false);
            }
        });
    }
}
