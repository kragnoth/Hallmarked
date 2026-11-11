package com.kragnoth.hallmarked.compat.jei;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.recipe.ReforgeMaterialRecipe;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Discovered by JEI via the annotation; never class-loaded when JEI is absent. */
@JeiPlugin
public class HallmarkedJeiPlugin implements IModPlugin {
    public static final RecipeType<ReforgeMaterialRecipe> REFORGE =
            RecipeType.create(Hallmarked.MODID, "reforge_material", ReforgeMaterialRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return Hallmarked.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new ReforgeCategory(reg.getJeiHelpers().getGuiHelper()));
        reg.addRecipeCategories(new InlayCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            List<ReforgeMaterialRecipe> recipes = level.getRecipeManager()
                    .getAllRecipesFor(HMRegistries.REFORGE_MATERIAL_TYPE.get()).stream()
                    .map(RecipeHolder::value)
                    // Specific recipes first, catch-alls last.
                    .sorted((a, b) -> Boolean.compare(a.isCatchAll(), b.isCatchAll()))
                    .toList();
            reg.addRecipes(REFORGE, recipes);
        }
        reg.addRecipes(InlayCategory.TYPE, List.copyOf(com.kragnoth.hallmarked.inlay.Inlays.STORE.all()));
        reg.addIngredientInfo(new ItemStack(HMRegistries.GEARWRIGHTS_SEAL.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.hallmarked.seal.info"));
        reg.addIngredientInfo(new ItemStack(HMRegistries.ASSAY_ANVIL_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.hallmarked.assay_anvil.info"));
        reg.addIngredientInfo(List.of(new ItemStack(HMRegistries.EMERALD_RING.get()), new ItemStack(HMRegistries.EMERALD_AMULET.get())),
                VanillaTypes.ITEM_STACK, Component.translatable("jei.hallmarked.baubles.info"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration reg) {
        reg.addRecipeCatalyst(new ItemStack(HMRegistries.ASSAY_ANVIL_ITEM.get()), REFORGE);
        reg.addRecipeCatalyst(new ItemStack(net.minecraft.world.item.Items.SMITHING_TABLE), InlayCategory.TYPE);
    }

    /** Teach JEI's smithing category about the single data-driven inlay recipe (shown generically). */
    @Override
    public void registerVanillaCategoryExtensions(mezz.jei.api.registration.IVanillaCategoryExtensionRegistration reg) {
        reg.getSmithingCategory().addExtension(com.kragnoth.hallmarked.inlay.InlayRecipe.class, new InlaySmithingExtension());
    }
}
