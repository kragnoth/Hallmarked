package com.kragnoth.hallmarked.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class HallmarkedNetwork {
    private HallmarkedNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        reg.playToClient(SyncQualitiesPayload.TYPE, SyncQualitiesPayload.CODEC, SyncQualitiesPayload::handle);
        reg.playToServer(ReforgePayload.TYPE, ReforgePayload.CODEC, ReforgePayload::handle);
    }
}
