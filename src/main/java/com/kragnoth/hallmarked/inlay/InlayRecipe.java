package com.kragnoth.hallmarked.inlay;

import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * One smithing recipe that serves every inlay: template + equipment + material, resolved against the loaded
 * inlay data. Lives at data/hallmarked/recipe/inlay/inlay.json ({@code {"type": "hallmarked:inlay"}}); a pack can
 * remove that file to switch inlays off entirely. Runs on both sides (vanilla predicts the result client-side),
 * which is why inlay data is synced to clients.
 */
public class InlayRecipe implements SmithingRecipe {
    public static final InlayRecipe INSTANCE = new InlayRecipe();

    @Override
    public boolean matches(SmithingRecipeInput in, Level level) {
        return Inlays.find(in.template(), in.base(), in.addition()) != null;
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput in, HolderLookup.Provider registries) {
        Inlay inlay = Inlays.find(in.template(), in.base(), in.addition());
        return inlay == null ? ItemStack.EMPTY : Inlays.apply(in.template(), in.base(), inlay);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return Inlays.isTemplate(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return Equipment.categoryOf(stack) != null;
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return Inlays.isMaterial(stack);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return HMRegistries.INLAY_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<InlayRecipe> {
        private static final MapCodec<InlayRecipe> CODEC = MapCodec.unit(INSTANCE);
        private static final StreamCodec<RegistryFriendlyByteBuf, InlayRecipe> STREAM = StreamCodec.unit(INSTANCE);

        @Override public MapCodec<InlayRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, InlayRecipe> streamCodec() { return STREAM; }
    }
}
