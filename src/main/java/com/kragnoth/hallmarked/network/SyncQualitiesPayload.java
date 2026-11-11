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
public record SyncQualitiesPayload(List<String> qualities, List<String> grades, List<String> affixes, List<String> fusions) implements CustomPacketPayload {
    public static final Type<SyncQualitiesPayload> TYPE = new Type<>(Hallmarked.id("sync_data"));
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<String>> STRINGS = ByteBufCodecs.stringUtf8(1 << 20).apply(ByteBufCodecs.list()); // NeoForge splits oversized packets
    public static final StreamCodec<FriendlyByteBuf, SyncQualitiesPayload> CODEC = StreamCodec.composite(
            STRINGS, SyncQualitiesPayload::qualities,
            STRINGS, SyncQualitiesPayload::grades,
            STRINGS, SyncQualitiesPayload::affixes,
            STRINGS, SyncQualitiesPayload::fusions,
            SyncQualitiesPayload::new);

    public static SyncQualitiesPayload snapshot() {
        return new SyncQualitiesPayload(Qualities.toWire(), Grading.GRADES.toWire(), Grading.AFFIXES.toWire(), Grading.FUSIONS.toWire());
    }

    @Override
    public Type<SyncQualitiesPayload> type() {
        return TYPE;
    }

    public static void handle(SyncQualitiesPayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Qualities.fromWire(msg.qualities());
            Grading.GRADES.fromWire(msg.grades());
            Grading.AFFIXES.fromWire(msg.affixes());
            Grading.FUSIONS.fromWire(msg.fusions());
        });
    }
}
