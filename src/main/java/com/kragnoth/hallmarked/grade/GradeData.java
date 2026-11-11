package com.kragnoth.hallmarked.grade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Stored on the item: its grade, the affixes it rolled (with a 0..1 roll each, so values follow the
 * current affix data), and the title words for titled grades (empty when untitled).
 * Words are stored, not indices, so editing the word lists never renames existing gear.
 */
public record GradeData(String grade, List<Rolled> affixes, String titleFirst, String titleSecond) {

    public record Rolled(String affix, float roll) {
        public static final Codec<Rolled> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("affix").forGetter(Rolled::affix),
                Codec.FLOAT.fieldOf("roll").forGetter(Rolled::roll)
        ).apply(i, Rolled::new));
        public static final StreamCodec<ByteBuf, Rolled> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Rolled::affix,
                ByteBufCodecs.FLOAT, Rolled::roll,
                Rolled::new);
    }

    public static final Codec<GradeData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("grade").forGetter(GradeData::grade),
            Rolled.CODEC.listOf().optionalFieldOf("affixes", List.of()).forGetter(GradeData::affixes),
            Codec.STRING.optionalFieldOf("title_first", "").forGetter(GradeData::titleFirst),
            Codec.STRING.optionalFieldOf("title_second", "").forGetter(GradeData::titleSecond)
    ).apply(i, GradeData::new));

    public static final StreamCodec<ByteBuf, GradeData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, GradeData::grade,
            Rolled.STREAM_CODEC.apply(ByteBufCodecs.list()), GradeData::affixes,
            ByteBufCodecs.STRING_UTF8, GradeData::titleFirst,
            ByteBufCodecs.STRING_UTF8, GradeData::titleSecond,
            GradeData::new);

    public GradeData {
        affixes = List.copyOf(affixes);
    }

    public boolean titled() {
        return !titleFirst.isEmpty() && !titleSecond.isEmpty();
    }
}
