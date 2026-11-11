package com.kragnoth.hallmarked.compat.jei;

import com.kragnoth.hallmarked.inlay.Inlay;
import com.kragnoth.hallmarked.inlay.Inlays;
import com.kragnoth.hallmarked.quality.Equipment;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One page per inlay: template + equipment + material -> the equipment with that inlay. */
public class InlayCategory implements IRecipeCategory<Inlay> {
    public static final RecipeType<Inlay> TYPE = RecipeType.create("hallmarked", "inlay", Inlay.class);
    private static final int W = 128, H = 40;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    public InlayCategory(IGuiHelper gui) {
        this.icon = gui.createDrawableItemStack(new ItemStack(Items.SMITHING_TABLE));
        this.arrow = gui.getRecipeArrow();
    }

    @Override public RecipeType<Inlay> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.hallmarked.inlay"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return W; }
    @Override public int getHeight() { return H; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Inlay inlay, IFocusGroup focuses) {
        List<ItemStack> templates = new ArrayList<>(Arrays.asList(inlay.template().getItems()));
        templates.add(new ItemStack(HMRegistries.GEARWRIGHTS_SEAL.get()));
        List<ItemStack> bases = new ArrayList<>(), outputs = new ArrayList<>();
        ItemStack stamp = new ItemStack(HMRegistries.SMITHS_STAMP.get());
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(item);
            if (Equipment.categoryOf(s) != null && Equipment.applied(inlay, s) != null) {
                bases.add(s);
                outputs.add(Inlays.apply(stamp, s, inlay));
            }
        }
        b.addSlot(RecipeIngredientRole.INPUT, 2, 4).addItemStacks(templates).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.INPUT, 20, 4).addItemStacks(bases).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.INPUT, 38, 4).addIngredients(inlay.material()).setStandardSlotBackground();
        b.addSlot(RecipeIngredientRole.OUTPUT, 98, 4).addItemStacks(outputs).setOutputSlotBackground();
    }

    @Override
    public void draw(Inlay inlay, IRecipeSlotsView slots, GuiGraphics g, double mx, double my) {
        arrow.draw(g, 64, 4);
        var font = Minecraft.getInstance().font;
        g.drawString(font, Component.translatable(inlay.name()), 2, H - 18, Inlay.COLOR | 0xFF000000, false);
        g.drawString(font, Component.translatable("jei.hallmarked.inlay.slots"), 2, H - 9, 0xFF555555, false);
    }
}
