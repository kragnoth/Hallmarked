package com.kragnoth.hallmarked.compat.jei;

import com.kragnoth.hallmarked.inlay.InlayRecipe;
import com.kragnoth.hallmarked.inlay.Inlay;
import com.kragnoth.hallmarked.inlay.Inlays;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Generic view of the inlay recipe in JEI's smithing tab; the Inlays tab has the per-inlay detail. */
class InlaySmithingExtension implements ISmithingCategoryExtension<InlayRecipe> {
    @Override
    public <T extends IIngredientAcceptor<T>> void setTemplate(InlayRecipe r, T acceptor) {
        acceptor.addItemStack(new ItemStack(HMRegistries.SMITHS_STAMP.get()));
        acceptor.addItemStack(new ItemStack(HMRegistries.GEARWRIGHTS_SEAL.get()));
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setBase(InlayRecipe r, T acceptor) {
        acceptor.addItemStacks(equipment());
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setAddition(InlayRecipe r, T acceptor) {
        List<ItemStack> mats = new ArrayList<>();
        for (Inlay i : Inlays.STORE.all()) mats.addAll(Arrays.asList(i.material().getItems()));
        acceptor.addItemStacks(mats);
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setOutput(InlayRecipe r, T acceptor) {
        acceptor.addItemStacks(equipment());
    }

    private static List<ItemStack> equipment() {
        List<ItemStack> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack s = new ItemStack(item);
            if (Equipment.categoryOf(s) != null) out.add(s);
        }
        return out;
    }
}
