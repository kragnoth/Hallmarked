package com.kragnoth.hallmarked.compat.jei;

import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.inlay.Inlay;
import com.kragnoth.hallmarked.inlay.Inlays;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * One page per inlay: template + equipment + material -> the equipment with that inlay. Below the recipe the page
 * lists what the inlay actually grants (stat lines in the same wording as the item tooltip, plus virtual
 * enchantment levels), which equipment accepts it, and how many inlay slots each grade of work has.
 * Look up any material in JEI (U) to see what it adds.
 */
public class InlayCategory implements IRecipeCategory<Inlay> {
    public static final RecipeType<Inlay> TYPE = RecipeType.create("hallmarked", "inlay", Inlay.class);
    private static final int W = 150, H = 104;
    private static final int TEXT_X = 2, TEXT_W = W - 4;
    private static final int MAX_EFFECT_LINES = 4, MAX_APPLIES_LINES = 2;

    private final IDrawable icon;
    private final IDrawableStatic arrow;

    /** Wrapped text per inlay, built on first draw and rebuilt when the language changes. */
    private final Map<Inlay, Text> cache = new IdentityHashMap<>();
    private String cacheLang = "";

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
        Font font = Minecraft.getInstance().font;

        String lang = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!lang.equals(cacheLang)) {
            cache.clear();
            cacheLang = lang;
        }
        Text t = cache.computeIfAbsent(inlay, i -> Text.build(i, font));

        int y = 26;
        g.drawString(font, Component.translatable(inlay.name()), TEXT_X, y, Inlay.COLOR | 0xFF000000, false);
        y += font.lineHeight + 2;
        for (FormattedCharSequence line : t.effects()) {
            g.drawString(font, line, TEXT_X, y, 0xFF404040, false);
            y += font.lineHeight;
        }
        y += 3;
        for (FormattedCharSequence line : t.applies()) {
            g.drawString(font, line, TEXT_X, y, 0xFF555555, false);
            y += font.lineHeight;
        }
        for (FormattedCharSequence line : t.slots()) {
            if (y + font.lineHeight > H) break;
            g.drawString(font, line, TEXT_X, y, 0xFF555555, false);
            y += font.lineHeight;
        }
    }

    /** Pre-wrapped lines for one inlay's page. */
    private record Text(List<FormattedCharSequence> effects, List<FormattedCharSequence> applies,
                        List<FormattedCharSequence> slots) {

        static Text build(Inlay inlay, Font font) {
            return new Text(
                    cap(wrap(font, effectLines(inlay)), MAX_EFFECT_LINES),
                    cap(font.split(appliesTo(inlay), TEXT_W), MAX_APPLIES_LINES),
                    font.split(slotsLine(), TEXT_W));
        }

        private static List<FormattedCharSequence> wrap(Font font, List<Component> lines) {
            List<FormattedCharSequence> out = new ArrayList<>();
            for (Component c : lines) out.addAll(font.split(c, TEXT_W));
            return out;
        }

        private static <T> List<T> cap(List<T> in, int max) {
            return in.size() <= max ? in : in.subList(0, max);
        }
    }

    /**
     * The inlay's stat lines, worded like the item tooltip. Identical lines across sections are merged; a line that
     * only some of the inlay's sections get is tagged, e.g. "+1 Oxygen Bonus (Helmets)". Enchantments are virtual
     * levels and apply to every section.
     */
    private static List<Component> effectLines(Inlay inlay) {
        Map<String, Component> text = new LinkedHashMap<>();
        Map<String, Set<Category>> where = new LinkedHashMap<>();
        for (Category c : Category.values()) {
            for (var mod : inlay.section(c)) {
                var attr = mod.attribute();
                if (attr.isEmpty()) continue;
                AttributeModifier am = new AttributeModifier(
                        ResourceLocation.fromNamespaceAndPath("hallmarked", "jei/" + mod.name()), mod.min(), mod.operation());
                MutableComponent line = attr.get().value().toComponent(am, TooltipFlag.NORMAL)
                        .withStyle(am.amount() > 0 ? ChatFormatting.DARK_BLUE : ChatFormatting.DARK_RED);
                String key = line.getString();
                text.putIfAbsent(key, line);
                where.computeIfAbsent(key, k -> EnumSet.noneOf(Category.class)).add(c);
            }
        }

        Set<Category> all = inlay.sections().keySet();
        List<Component> out = new ArrayList<>();
        for (var e : text.entrySet()) {
            Set<Category> cats = where.get(e.getKey());
            MutableComponent line = Component.empty().append(e.getValue());
            if (!cats.containsAll(all)) {
                line.append(Component.literal(" (").append(categoryList(cats)).append(")").withStyle(ChatFormatting.DARK_GRAY));
            }
            out.add(line);
        }
        for (var ench : inlay.enchantments().entrySet()) {
            out.add(Component.translatable("tooltip.hallmarked.inlay_enchant", ench.getValue(), enchantmentName(ench.getKey()))
                    .withStyle(ChatFormatting.DARK_BLUE));
        }
        return out;
    }

    private static Component appliesTo(Inlay inlay) {
        Set<Category> cats = EnumSet.noneOf(Category.class);
        cats.addAll(inlay.sections().keySet());
        return Component.translatable("jei.hallmarked.inlay.applies", categoryList(cats));
    }

    private static Component categoryList(Set<Category> cats) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Category c : Category.values()) {
            if (!cats.contains(c)) continue;
            if (!first) out.append(", ");
            out.append(Component.translatable("jei.hallmarked.inlay.section." + c.key()));
            first = false;
        }
        return out;
    }

    /** "Slots: Common 2, Journeyman 1, Masterwork 1", read from the loaded grades. */
    private static Component slotsLine() {
        List<Grade> grades = new ArrayList<>(Grading.GRADES.all());
        grades.sort(Comparator.comparingInt(Grade::rank));
        MutableComponent list = Component.empty();
        boolean first = true;
        for (Grade gr : grades) {
            if (gr.inlaySlots() <= 0) continue;
            if (!first) list.append(", ");
            list.append(Component.translatable(gr.name())).append(" " + gr.inlaySlots());
            first = false;
        }
        return Component.translatable("jei.hallmarked.inlay.slots", list);
    }

    /** Real description from the (data-driven) enchantment registry; key guess only as a fallback. */
    private static Component enchantmentName(ResourceLocation id) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(id);
            if (holder.isPresent()) return holder.get().value().description();
        }
        return Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath());
    }
}
