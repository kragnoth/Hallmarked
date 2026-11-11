package com.kragnoth.hallmarked.sets;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Stored on an item: which set it belongs to and which piece of it it is. */
public record SetPiece(String set, String piece) {
    public static final Codec<SetPiece> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("set").forGetter(SetPiece::set),
            Codec.STRING.fieldOf("piece").forGetter(SetPiece::piece)
    ).apply(i, SetPiece::new));

    public static final StreamCodec<ByteBuf, SetPiece> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetPiece::set,
            ByteBufCodecs.STRING_UTF8, SetPiece::piece,
            SetPiece::new);
}
