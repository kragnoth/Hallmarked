package com.kragnoth.hallmarked.gamble;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Who a Mountebank's Coffer is locked to; travels with the coffer when it's broken and carried as an item. */
public record CofferOwner(UUID id, String name) {
    public static final Codec<CofferOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(CofferOwner::id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(CofferOwner::name)
    ).apply(i, CofferOwner::new));

    public static final StreamCodec<ByteBuf, CofferOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CofferOwner::id,
            ByteBufCodecs.STRING_UTF8, CofferOwner::name,
            CofferOwner::new);
}
