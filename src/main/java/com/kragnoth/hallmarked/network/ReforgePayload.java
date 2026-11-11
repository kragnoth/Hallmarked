package com.kragnoth.hallmarked.network;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.menu.AssayAnvilMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: the player pressed the hammer in the Assay Anvil screen. */
public record ReforgePayload(int containerId) implements CustomPacketPayload {
    public static final Type<ReforgePayload> TYPE = new Type<>(Hallmarked.id("reforge"));
    public static final StreamCodec<FriendlyByteBuf, ReforgePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ReforgePayload::containerId, ReforgePayload::new);

    @Override
    public Type<ReforgePayload> type() {
        return TYPE;
    }

    public static void handle(ReforgePayload msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player().containerMenu instanceof AssayAnvilMenu menu && menu.containerId == msg.containerId()) {
                menu.tryReforge(ctx.player());
            }
        });
    }
}
