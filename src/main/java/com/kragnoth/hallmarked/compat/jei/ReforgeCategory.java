package com.kragnoth.hallmarked.compat.jei;

import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.recipe.ReforgeMaterialRecipe;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Equipment + material -> the same equipment with a re-rolled quality. */
public class ReforgeCategory implements IRecipeCategory<ReforgeMaterialRecipe> {
    private static final int W = 120, H = 38;

    private final IDrawable icon;
    private final IDrawableStatic arrow;
    private List<ItemStack> allEquipment;

    public ReforgeCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(HMRegistries.ASSAY_ANVIL_ITEM.get()));
        this.arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<ReforgeMaterialRecipe> getRecipeType() { return HallmarkedJeiPlugin.REFORGE; }
    @Override public Component getTitle() { return Component.translatable("jei.hallmarked.reforge"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, ReforgeMaterialRecipe recipe, IFocusGroup focuses) {
        List<ItemStack> equipment = recipe.equipment()
                .map(i -> Arrays.asList(i.getItems()))
                .orElseGet(this::allEquipment);
        List<ItemStack> materials = new ArrayList<>();
        for (ItemStack s : recipe.material().getItems()) materials.add(s.copyWithCount(recipe.cost()));

        b.addSlot(RecipeIngredientRole.INPUT, 4, 6).addItemStacks(equipment).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.INPUT, 30, 6).addItemStacks(materials).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.OUTPUT, 96, 6).addItemStacks(equipment).setOutputSlotBackground();
    }

    @Override
    public void draw(ReforgeMaterialRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mx, double my) {
        arrow.draw(g, 60, 6);
        var font = Minecraft.getInstance().font;
        Component note = recipe.isCatchAll()
                ? Component.translatable("jei.hallmarked.reforge.any")
                : Component.translatable("jei.hallmarked.reforge.rerolls");
        g.drawString(font, note.copy().withStyle(ChatFormatting.DARK_GRAY), 2, H - 10, 0xFF555555, false);
    }

    /** Every registered item that counts as equipment (tags + fallback), for catch-all recipes. */
    private List<ItemStack> allEquipment() {
        if (allEquipment == null) {
            List<ItemStack> out = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack s = new ItemStack(item);
                if (Equipment.categoryOf(s) != null) out.add(s);
            }
            allEquipment = List.copyOf(out);
        }
        return allEquipment;
    }
}
