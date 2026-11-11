package com.kragnoth.hallmarked.client;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.event.CommonEvents;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
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

        /** Pull the quality's modifiers out of vanilla's sections; they're listed under the quality instead. */
        @SubscribeEvent
        public static void skipQualityModifiers(GatherSkippedAttributeTooltipsEvent e) {
            ItemStack stack = e.getStack();
            Quality q = Reforging.qualityOf(stack);
            if (q == null) return;
            Equipment.Applied applied = Equipment.applied(q, stack);
            if (applied == null || applied.slot() == null) return;
            for (Quality.Mod m : q.section(applied.section())) e.skipId(CommonEvents.itemModifierId(m, applied));
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public static void tooltip(ItemTooltipEvent e) {
            ItemStack stack = e.getItemStack();
            String name = stack.get(HMRegistries.QUALITY.get());
            if (name == null) return;
            Quality q = Reforging.qualityOf(stack);

            List<Component> block = new ArrayList<>();
            block.add(Component.empty());
            block.add(Component.translatable("tooltip.hallmarked.quality").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(name).withStyle(q != null ? q.color() : ChatFormatting.GRAY)));

            if (q != null) {
                List<Quality.Mod> mods;
                Component heading;
                Equipment.Applied applied = Equipment.applied(q, stack);
                if (applied != null && applied.section() == Category.BAUBLE) {
                    mods = q.section(Category.BAUBLE);
                    heading = Component.translatable("tooltip.hallmarked.bauble");
                } else if (applied != null && applied.slot() != null) {
                    mods = q.section(applied.section());
                    heading = Component.translatable("item.modifiers." + applied.slot().getSerializedName());
                } else {
                    mods = List.of();
                    heading = null;
                }
                if (heading != null && !mods.isEmpty()) {
                    block.add(heading.copy().withStyle(ChatFormatting.GRAY));
                    List<Quality.Mod> sorted = new ArrayList<>(mods);
                    sorted.sort(Comparator.comparing((Quality.Mod m) -> m.value() <= 0)); // positives first, stable
                    for (Quality.Mod m : sorted) {
                        m.attribute().ifPresent(attr -> {
                            AttributeModifier mod = new AttributeModifier(Hallmarked.id(m.name()), m.value(), m.operation());
                            MutableComponent line = attr.value().toComponent(mod, e.getFlags());
                            block.add(Component.literal(" ").append(line.withStyle(m.value() > 0 ? ChatFormatting.BLUE : ChatFormatting.RED)));
                        });
                    }
                }
            }

            // Insert above the advanced-tooltip footer (durability / id / component count) if present.
            List<Component> tip = e.getToolTip();
            int insertAt = tip.size();
            if (e.getFlags().isAdvanced()) {
                insertAt -= 2;
                if (stack.isDamaged()) insertAt -= 1;
                insertAt = Math.max(1, insertAt);
            }
            tip.addAll(insertAt, block);
        }
    }
}
