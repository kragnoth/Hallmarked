package com.kragnoth.hallmarked.client;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.grade.Marks;
import com.kragnoth.hallmarked.inlay.Inlay;
import com.kragnoth.hallmarked.inlay.Inlays;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.GatherSkippedAttributeTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

public final class ClientEvents {
    private ClientEvents() {}

    @EventBusSubscriber(modid = Hallmarked.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent e) {
            e.register(HMRegistries.ASSAY_ANVIL_MENU.get(), AssayAnvilScreen::new);
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
            e.registerBlockEntityRenderer(HMRegistries.ASSAY_ANVIL_BE.get(), AssayAnvilRenderer::new);
        }
    }

    @EventBusSubscriber(modid = Hallmarked.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        private static final DecimalFormat FMT = new DecimalFormat("#.##");

        /** Pull our modifiers out of vanilla's sections; they're listed under the quality and grade instead. */
        @SubscribeEvent
        public static void skipOwnModifiers(GatherSkippedAttributeTooltipsEvent e) {
            for (Marks.Entry entry : Marks.of(e.getStack())) {
                if (entry.slot() != null) e.skipId(entry.modifier().id());
            }
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void tooltip(ItemTooltipEvent e) {
            ItemStack stack = e.getItemStack();
            if (stack.is(HMRegistries.SMITHS_STAMP.get())) {
                e.getToolTip().add(Component.translatable("tooltip.hallmarked.stamp").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (Inlays.isSeal(stack)) {
                e.getToolTip().add(Component.translatable("tooltip.hallmarked.seal").withStyle(ChatFormatting.GRAY));
                return;
            }
            String qualityName = stack.get(HMRegistries.QUALITY.get());
            Grade grade = Grading.gradeOf(stack);
            List<Inlay> inlays = Inlays.on(stack);
            Inlay sealed = Inlays.sealed(stack);
            if (qualityName == null && grade == null && inlays.isEmpty() && sealed == null) return;

            List<Marks.Entry> entries = Marks.of(stack);
            List<Component> block = new ArrayList<>();
            boolean advanced = e.getFlags().isAdvanced();

            // Quality block
            if (qualityName != null) {
                Quality q = Reforging.qualityOf(stack);
                block.add(Component.empty());
                block.add(Component.translatable("tooltip.hallmarked.quality").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable(qualityName).withStyle(q != null ? q.color() : ChatFormatting.GRAY)));
                addLines(block, entries, Marks.Origin.QUALITY, advanced, List.of());
            }

            // Grade block
            if (grade != null && !grade.hidden()) {
                block.add(Component.empty());
                block.add(Component.translatable(grade.name()).withStyle(grade.color()));
                addLines(block, entries, Marks.Origin.AFFIX, advanced, Grading.fusions(Grading.dataOf(stack)));
            }

            // Inlay block
            if (!inlays.isEmpty() || sealed != null) {
                block.add(Component.empty());
                block.add(Component.translatable("tooltip.hallmarked.inlays", Inlays.used(stack), Inlays.slots(stack))
                        .withStyle(s -> s.withColor(Inlay.COLOR)));
                for (Inlay inlay : inlays) addInlay(block, entries, inlay, -1, false);
                if (sealed != null) addInlay(block, entries, sealed, Marks.SEALED_INDEX, true);
            }

            List<Component> tip = e.getToolTip();
            // Titled items show their base item under the title, Diablo-style.
            if (grade != null && grade.titled() && !tip.isEmpty()) {
                tip.add(1, Grading.baseName(stack).copy().withStyle(ChatFormatting.GRAY));
            }

            // Insert above the advanced-tooltip footer (durability / id / component count) if present.
            int insertAt = tip.size();
            if (advanced) {
                insertAt -= 2;
                if (stack.isDamaged()) insertAt -= 1;
                insertAt = Math.max(1, insertAt);
            }
            tip.addAll(insertAt, block);
        }

        private static void addLines(List<Component> block, List<Marks.Entry> all, Marks.Origin origin, boolean advanced,
                                     List<Grading.Fused> fused) {
            List<Marks.Entry> list = new ArrayList<>();
            for (Marks.Entry en : all) if (en.origin() == origin && !en.shadow()) list.add(en);
            if (list.isEmpty()) return;

            EquipmentSlotGroup slot = list.get(0).displaySlot();
            Component heading = slot == null
                    ? Component.translatable("tooltip.hallmarked.bauble")
                    : Component.translatable("item.modifiers." + slot.getSerializedName());
            block.add(heading.copy().withStyle(ChatFormatting.GRAY));

            Comparator<Marks.Entry> positivesFirst = Comparator.comparing((Marks.Entry en) -> en.modifier().amount() <= 0);

            // Fused pairs first: a named line, then both parts' stats beneath it.
            for (Grading.Fused f : fused) {
                List<Marks.Entry> group = new ArrayList<>();
                for (Marks.Entry en : list) if (en.index() == f.first() || en.index() == f.second()) group.add(en);
                if (group.isEmpty()) continue;
                block.add(Component.literal(" ").append(Component.translatable(f.fusion().name()))
                        .withStyle(ChatFormatting.DARK_AQUA));
                group.sort(positivesFirst);
                for (Marks.Entry en : group) block.add(statLine(en, "   ", advanced));
                list.removeAll(group);
            }

            list.sort(positivesFirst);
            for (Marks.Entry en : list) block.add(statLine(en, " ", advanced));
        }

        /** One inlay: its name (copper; sealed ones gold), then its stats and virtual enchantment levels. */
        private static void addInlay(List<Component> block, List<Marks.Entry> all, Inlay inlay, int index, boolean sealed) {
            MutableComponent name = Component.literal(" ").append(Component.translatable(inlay.name()));
            if (sealed) {
                name.append(Component.literal(" (").append(Component.translatable("tooltip.hallmarked.sealed")).append(")"))
                        .withStyle(ChatFormatting.GOLD);
            } else {
                name.withStyle(s -> s.withColor(Inlay.COLOR));
            }
            block.add(name);
            for (Marks.Entry en : all) {
                if (en.origin() == Marks.Origin.INLAY && !en.shadow() && en.label().equals(inlay.name()) && en.index() == index) {
                    block.add(statLine(en, "   ", false));
                }
            }
            for (var ench : inlay.enchantments().entrySet()) {
                Component enchName = enchantmentName(ench.getKey());
                block.add(Component.literal("   ").append(Component.translatable("tooltip.hallmarked.inlay_enchant", ench.getValue(), enchName))
                        .withStyle(ChatFormatting.BLUE));
            }
        }

        /** Real description from the (data-driven) enchantment registry; key guess only as a fallback. */
        private static Component enchantmentName(net.minecraft.resources.ResourceLocation id) {
            var level = net.minecraft.client.Minecraft.getInstance().level;
            if (level != null) {
                var holder = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolder(id);
                if (holder.isPresent()) return holder.get().value().description();
            }
            return Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath());
        }

        private static MutableComponent statLine(Marks.Entry en, String indent, boolean advanced) {
            AttributeModifier mod = en.modifier();
            MutableComponent line = Component.literal(indent).append(en.attribute().value().toComponent(mod, TooltipFlag.NORMAL)
                    .withStyle(mod.amount() > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
            if (advanced && en.origin() == Marks.Origin.AFFIX) {
                line.append(Component.literal("  ").append(Component.translatable(en.label()))
                        .append(" [" + fmt(en, en.min()) + " .. " + fmt(en, en.max()) + "]")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            return line;
        }

        private static String fmt(Marks.Entry en, double v) {
            return en.modifier().operation() == AttributeModifier.Operation.ADD_VALUE
                    ? FMT.format(v) : FMT.format(v * 100) + "%";
        }
    }
}
