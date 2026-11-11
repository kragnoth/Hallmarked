package com.kragnoth.hallmarked.network;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Qualities;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: qualities, grades and affixes (as JSON) so tooltips and item modifiers match the server. */
public record SyncQualitiesPayload(List<String> qualities, List<String> grades, List<String> affixes, List<String> fusions, List<String> inlays, List<String> uniques, List<String> conversions) implements CustomPacketPayload {
    public static final Type<SyncQualitiesPayload> TYPE = new Type<>(Hallmarked.id("sync_data"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS = ByteBufCodecs.stringUtf8(1 << 20).apply(ByteBufCodecs.list()); // NeoForge splits oversized packets
    // Seven lists: past StreamCodec.composite's six-field limit, so encode/decode by hand.
    public static final StreamCodec<FriendlyByteBuf, SyncQualitiesPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                STRINGS.encode(buf, p.qualities()); STRINGS.encode(buf, p.grades()); STRINGS.encode(buf, p.affixes());
                STRINGS.encode(buf, p.fusions()); STRINGS.encode(buf, p.inlays()); STRINGS.encode(buf, p.uniques());
                STRINGS.encode(buf, p.conversions());
            },
            buf -> new SyncQualitiesPayload(STRINGS.decode(buf), STRINGS.decode(buf), STRINGS.decode(buf),
                    STRINGS.decode(buf), STRINGS.decode(buf), STRINGS.decode(buf), STRINGS.decode(buf)));

    public static SyncQualitiesPayload snapshot() {
        return new SyncQualitiesPayload(Qualities.toWire(), Grading.GRADES.toWire(), Grading.AFFIXES.toWire(), Grading.FUSIONS.toWire(),
                com.kragnoth.hallmarked.inlay.Inlays.STORE.toWire(), Grading.UNIQUES.toWire(),
                com.kragnoth.hallmarked.quality.Conversion.STORE.toWire());
    }

    @Override
    public Type<SyncQualitiesPayload> type() {
        return TYPE;
    }

    public static void handle(SyncQualitiesPayload msg, IPayloadContext ctx) {
        // Singleplayer/LAN host: client and server share these stores and the server already loaded them.
        // Don't overwrite live server data from the client thread with a re-parsed copy.
        if (net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null) return;
        ctx.enqueueWork(() -> {
            Qualities.fromWire(msg.qualities());
            Grading.GRADES.fromWire(msg.grades());
            Grading.AFFIXES.fromWire(msg.affixes());
            Grading.FUSIONS.fromWire(msg.fusions());
            com.kragnoth.hallmarked.inlay.Inlays.STORE.fromWire(msg.inlays());
            Grading.UNIQUES.fromWire(msg.uniques());
            com.kragnoth.hallmarked.quality.Conversion.STORE.fromWire(msg.conversions());
        });
    }
}
