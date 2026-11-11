package com.kragnoth.hallmarked.network;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.quality.Qualities;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client: the full quality list (as JSON) so tooltips and item modifiers match the server. */
public record SyncQualitiesPayload(List<String> qualities) implements CustomPacketPayload {
    public static final Type<SyncQualitiesPayload> TYPE = new Type<>(Hallmarked.id("sync_qualities"));
    public static final StreamCodec<FriendlyByteBuf, SyncQualitiesPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncQualitiesPayload::qualities,
            SyncQualitiesPayload::new);

    @Override
    public Type<SyncQualitiesPayload> type() {
        return TYPE;
    }

    public static void handle(SyncQualitiesPayload msg, IPayloadContext ctx) {
        // On an integrated server the server already populated the same store; re-parsing is harmless.
        ctx.enqueueWork(() -> Qualities.fromWire(msg.qualities()));
    }
}
