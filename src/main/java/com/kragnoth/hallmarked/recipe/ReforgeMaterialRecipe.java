package com.kragnoth.hallmarked.recipe;

import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * Declares a material the Assay Anvil accepts for re-marking equipment.
 *
 * <pre>{@code
 * { "type": "hallmarked:reforge_material", "equipment": { "tag": "c:tools/bow" },
 *   "material": { "item": "minecraft:string" }, "cost": 1 }
 * }</pre>
 * Omitting {@code equipment} makes the material valid for every piece of equipment (the old "Fallback").
 * An item's own repair material is always accepted at cost 1 without needing a recipe.
 */
public record ReforgeMaterialRecipe(Optional<Ingredient> equipment, Ingredient material, int cost)
        implements Recipe<ReforgeMaterialRecipe.Input> {

    /** The two Assay Anvil slots. */
    public record Input(ItemStack equipment, ItemStack material) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return index == 0 ? equipment : material;
        }

        @Override
        public int size() {
            return 2;
        }
    }

    public static final MapCodec<ReforgeMaterialRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC_NONEMPTY.optionalFieldOf("equipment").forGetter(ReforgeMaterialRecipe::equipment),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(ReforgeMaterialRecipe::material),
            Codec.intRange(1, 64).optionalFieldOf("cost", 1).forGetter(ReforgeMaterialRecipe::cost)
    ).apply(i, ReforgeMaterialRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ReforgeMaterialRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), ReforgeMaterialRecipe::equipment,
            Ingredient.CONTENTS_STREAM_CODEC, ReforgeMaterialRecipe::material,
            ByteBufCodecs.VAR_INT, ReforgeMaterialRecipe::cost,
            ReforgeMaterialRecipe::new);

    @Override
    public boolean matches(Input in, Level level) {
        if (!material.test(in.material())) return false;
        return equipment.map(e -> e.test(in.equipment())).orElseGet(() -> Reforging.isEligible(in.equipment()));
    }

    /** Most specific first: recipes naming equipment win over catch-all ones when both match. */
    public boolean isCatchAll() {
        return equipment.isEmpty();
    }

    @Override
    public ItemStack assemble(Input in, HolderLookup.Provider registries) {
        return in.equipment().copy();
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true; // keep out of the vanilla recipe book
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HMRegistries.REFORGE_MATERIAL_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return HMRegistries.REFORGE_MATERIAL_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ReforgeMaterialRecipe> {
        @Override
        public MapCodec<ReforgeMaterialRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ReforgeMaterialRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
