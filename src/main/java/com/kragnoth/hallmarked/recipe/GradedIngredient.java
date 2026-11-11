package com.kragnoth.hallmarked.recipe;

import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import org.jetbrains.annotations.Nullable;

/**
 * Matches items by Hallmarked grade and/or quality. Usable anywhere an ingredient is: vanilla recipes,
 * Create, KubeJS, JEI.
 *
 * <pre>{@code
 * { "type": "hallmarked:graded", "items": { "tag": "minecraft:swords" },
 *   "min_grade": "masterwork", "max_grade": "gearwrought", "quality": "hallmarked.quality.keen" }
 * }</pre>
 * Grades may be given by short id ("masterwork") or full name. All filters are optional except {@code items}.
 * An item with no grade counts as Common for min/max checks.
 */
public record GradedIngredient(Ingredient items, Optional<String> minGrade, Optional<String> maxGrade,
                               Optional<String> quality) implements ICustomIngredient {

    public static final MapCodec<GradedIngredient> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("items").forGetter(GradedIngredient::items),
            Codec.STRING.optionalFieldOf("min_grade").forGetter(GradedIngredient::minGrade),
            Codec.STRING.optionalFieldOf("max_grade").forGetter(GradedIngredient::maxGrade),
            Codec.STRING.optionalFieldOf("quality").forGetter(GradedIngredient::quality)
    ).apply(i, GradedIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, GradedIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, GradedIngredient::items,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GradedIngredient::minGrade,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GradedIngredient::maxGrade,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GradedIngredient::quality,
            GradedIngredient::new);

    @Override
    public boolean test(ItemStack stack) {
        if (!items.test(stack)) return false;

        if (quality.isPresent()) {
            String q = stack.get(HMRegistries.QUALITY.get());
            if (q == null || !Quality.normalizeName(q).equals(Quality.normalizeName(quality.get()))) return false;
        }

        if (minGrade.isPresent() || maxGrade.isPresent()) {
            int rank = rankOf(stack);
            Grade min = minGrade.map(Grading::findGrade).orElse(null);
            Grade max = maxGrade.map(Grading::findGrade).orElse(null);
            if (min != null && rank < min.rank()) return false;
            if (max != null && rank > max.rank()) return false;
        }
        return true;
    }

    private static int rankOf(ItemStack stack) {
        Grade g = Grading.gradeOf(stack);
        if (g != null) return g.rank();
        Grade common = Grading.findGrade("common");
        return common != null ? common.rank() : 1;
    }

    /** Display stacks are stamped with the minimum grade/quality so JEI shows what's required. */
    @Override
    public Stream<ItemStack> getItems() {
        @Nullable Grade min = minGrade.map(Grading::findGrade).orElse(null);
        List<ItemStack> base = List.of(items.getItems());
        return base.stream().map(s -> {
            ItemStack c = s.copy();
            if (min != null) c.set(HMRegistries.GRADE.get(), new GradeData(min.name(), List.of(), "", ""));
            quality.ifPresent(q -> c.set(HMRegistries.QUALITY.get(), Quality.normalizeName(q)));
            return c;
        });
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return HMRegistries.GRADED_INGREDIENT.get();
    }
}
