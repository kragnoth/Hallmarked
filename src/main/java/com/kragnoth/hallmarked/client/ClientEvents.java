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
            if (stack.is(HMRegistries.SMITHS_SCRAP.get())) {
                e.getToolTip().add(Component.translatable("tooltip.hallmarked.scrap").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (stack.is(HMRegistries.MOUNTEBANK_COFFER_ITEM.get())) {
                e.getToolTip().add(Component.translatable("tooltip.hallmarked.coffer").withStyle(ChatFormatting.GRAY));
                return;
            }
            if (stack.is(HMRegistries.MOUNTEBANK_TOKEN.get())) {
                e.getToolTip().add(Component.translatable("tooltip.hallmarked.token").withStyle(ChatFormatting.GRAY));
                return;
            }
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
            // Compact by default (names only); hold Shift for the full stat blocks.
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                compact(block, stack, qualityName, grade, inlays, sealed);
                insert(e, stack, grade, block);
                return;
            }

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
                if (stack.getOrDefault(HMRegistries.TEMPERED.get(), false)) {
                    block.add(Component.translatable("tooltip.hallmarked.tempered").withStyle(ChatFormatting.DARK_RED));
                }
                if (Grading.isUnappraised(stack)) {
                    block.add(Component.translatable("tooltip.hallmarked.unappraised").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
                } else if (Grading.uniqueOf(stack) != null) {
                    var u = Grading.uniqueOf(stack);
                    if (u.lore() != null) block.add(Component.translatable(u.lore()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
                    addLines(block, entries, Marks.Origin.UNIQUE, advanced, List.of());
                    for (var ench : u.enchantments().entrySet()) {
                        block.add(Component.literal(" ").append(Component.translatable("tooltip.hallmarked.inlay_enchant", ench.getValue(), enchantmentName(ench.getKey())))
                                .withStyle(ChatFormatting.BLUE));
                    }
                } else if (com.kragnoth.hallmarked.sets.Sets.defOf(stack) != null) {
                    addLines(block, entries, Marks.Origin.SET, advanced, List.of());
                } else {
                    if (com.kragnoth.hallmarked.gamble.Gamble.isExceptional(stack)) {
                        block.add(Component.translatable("tooltip.hallmarked.exceptional").withStyle(ChatFormatting.GOLD));
                    }
                    addLines(block, entries, Marks.Origin.AFFIX, advanced, Grading.fusions(Grading.dataOf(stack)));
                    addProcLines(block, stack);
                }
            }
            setBlock(block, stack, true);

            // Inlay block
            if (!inlays.isEmpty() || sealed != null) {
                block.add(Component.empty());
                block.add(Component.translatable("tooltip.hallmarked.inlays", Inlays.used(stack), Inlays.slots(stack))
                        .withStyle(s -> s.withColor(Inlay.COLOR)));
                for (Inlay inlay : inlays) addInlay(block, entries, inlay, -1, false);
                if (sealed != null) addInlay(block, entries, sealed, Marks.SEALED_INDEX, true);
            }

            insert(e, stack, grade, block);
        }

        private static void insert(ItemTooltipEvent e, ItemStack stack, Grade grade, List<Component> block) {
            boolean advanced = e.getFlags().isAdvanced();
            List<Component> tip = e.getToolTip();
            // Titled items show their base item under the title, Diablo-style.
            if (grade != null && (grade.titled() || Grading.uniqueOf(stack) != null || com.kragnoth.hallmarked.sets.Sets.defOf(stack) != null)
                    && !Grading.isUnappraised(stack) && !tip.isEmpty()) {
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

        /** One line per layer: quality, grade with affix/fusion names, inlays. */
        private static void compact(List<Component> block, ItemStack stack, String qualityName, Grade grade,
                                    List<Inlay> inlays, Inlay sealed) {
            block.add(Component.empty());
            if (qualityName != null) {
                Quality q = Reforging.qualityOf(stack);
                block.add(Component.translatable("tooltip.hallmarked.quality").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable(qualityName).withStyle(q != null ? q.color() : ChatFormatting.GRAY)));
            }
            if (grade != null && !grade.hidden()) {
                MutableComponent line = Component.translatable(grade.name()).withStyle(grade.color());
                if (stack.getOrDefault(HMRegistries.TEMPERED.get(), false)) {
                    line.append(Component.literal(" ").append(Component.translatable("tooltip.hallmarked.tempered.short")).withStyle(ChatFormatting.DARK_RED));
                }
                List<Component> names = Grading.isUnappraised(stack)
                        ? List.of(Component.translatable("tooltip.hallmarked.unappraised.short").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))
                        : affixNames(stack);
                if (!names.isEmpty()) {
                    line.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
                    for (int i = 0; i < names.size(); i++) {
                        if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                        line.append(names.get(i));
                    }
                }
                block.add(line);
                if (com.kragnoth.hallmarked.gamble.Gamble.isExceptional(stack)) {
                    block.add(Component.translatable("tooltip.hallmarked.exceptional").withStyle(ChatFormatting.GOLD));
                }
            }
            setBlock(block, stack, false);
            if (!inlays.isEmpty() || sealed != null) {
                MutableComponent line = Component.translatable("tooltip.hallmarked.inlays", Inlays.used(stack), Inlays.slots(stack))
                        .withStyle(s -> s.withColor(Inlay.COLOR));
                line.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
                for (int i = 0; i < inlays.size(); i++) {
                    if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                    line.append(Component.translatable(inlays.get(i).name()).withStyle(s -> s.withColor(Inlay.COLOR)));
                }
                if (sealed != null) {
                    if (!inlays.isEmpty()) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
                    line.append(Component.translatable(sealed.name()).withStyle(ChatFormatting.GOLD));
                }
                block.add(line);
            }
            block.add(Component.translatable("tooltip.hallmarked.shift").withStyle(ChatFormatting.DARK_GRAY));
        }

        /** Fusion names stand in for their two parts; flaws in red, the rest gray. */
        private static List<Component> affixNames(ItemStack stack) {
            List<Component> out = new ArrayList<>();
            var d = Grading.dataOf(stack);
            if (d == null) return out;
            var fused = Grading.fusions(d);
            for (int i = 0; i < d.affixes().size(); i++) {
                var f = Grading.fusedAt(fused, i);
                if (f != null) {
                    if (f.first() == i) out.add(Component.translatable(f.fusion().name()).withStyle(ChatFormatting.DARK_AQUA));
                    continue;
                }
                var a = Grading.AFFIXES.get(d.affixes().get(i).affix());
                if (a == null) continue;
                out.add(Component.translatable(a.name()).withStyle(switch (a.type()) {
                    case FLAW -> ChatFormatting.RED;
                    case CURSE -> ChatFormatting.DARK_RED;
                    case FORBIDDEN -> ChatFormatting.DARK_PURPLE;
                    default -> ChatFormatting.GRAY;
                }));
            }
            return out;
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

        /** One line per proc: "15% on hit: Bleeding (1 dmg/s, 4s)", values from the affix roll. */
        private static void addProcLines(List<Component> block, ItemStack stack) {
            var d = Grading.dataOf(stack);
            if (d == null) return;
            for (var r : d.affixes()) {
                var a = Grading.AFFIXES.get(r.affix());
                if (a == null) continue;
                for (var proc : a.procs()) {
                    double power = proc.power(r.roll());
                    boolean pct = switch (proc.type()) { case LEECH, CLEAVE, REPRISAL, BACKLASH, FESTER -> true; default -> false; };
                    String powerText = pct ? FMT.format(power * 100) : FMT.format(power);
                    Component target = proc.target() == null
                            ? Component.translatable("hallmarked.target.any")
                            : Component.translatableWithFallback("hallmarked.target." + proc.target().location().getPath().replace('/', '.'),
                                    proc.target().location().getPath());
                    block.add(Component.literal(" ").append(Component.translatable("hallmarked.proc." + proc.type().id(),
                            FMT.format(proc.chance(r.roll()) * 100), powerText, FMT.format(proc.duration() / 20.0), target))
                            .withStyle(a.type() == com.kragnoth.hallmarked.grade.Affix.Type.CURSE ? ChatFormatting.DARK_RED : ChatFormatting.DARK_AQUA));
                }
            }
        }

        /**
         * The set a piece belongs to: "Regalia of the Oathsworn (3/5)", then (full view) each piece, worn or not,
         * then every bonus, lit when the worn count reaches it.
         */
        private static void setBlock(List<Component> block, ItemStack stack, boolean full) {
            var set = com.kragnoth.hallmarked.sets.Sets.setOf(stack);
            if (set == null) return;
            var player = net.minecraft.client.Minecraft.getInstance().player;
            java.util.Set<String> worn = player == null ? java.util.Set.of()
                    : com.kragnoth.hallmarked.sets.Sets.worn(player).getOrDefault(set.name(), java.util.Set.of());
            int n = worn.size();
            block.add(Component.empty());
            block.add(Component.translatable(set.name()).withStyle(set.color())
                    .append(Component.literal(" (" + n + "/" + set.pieces().size() + ")").withStyle(ChatFormatting.DARK_GRAY)));
            if (full) {
                for (var piece : set.pieces()) {
                    boolean on = worn.contains(piece.key());
                    block.add(Component.literal(on ? " \u2714 " : "   ").append(Component.translatable(piece.name()))
                            .withStyle(on ? set.color() : ChatFormatting.DARK_GRAY));
                }
            }
            for (var bonus : set.bonuses()) {
                boolean on = n >= bonus.pieces();
                ChatFormatting c = on ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY;
                MutableComponent line = bonus.perPiece()
                        ? Component.literal(" (").append(Component.translatable("tooltip.hallmarked.set.per_piece")).append(") ").withStyle(c)
                        : Component.literal(" (" + bonus.pieces() + ") ").withStyle(c);
                boolean first = true;
                for (var m : bonus.modifiers()) {
                    var attr = m.attribute();
                    if (attr.isEmpty()) continue;
                    if (!first) line.append(Component.literal(", ").withStyle(c));
                    first = false;
                    var mod = new AttributeModifier(com.kragnoth.hallmarked.Hallmarked.id("preview"), m.min(), m.operation());
                    line.append(attr.get().value().toComponent(mod, TooltipFlag.NORMAL).copy().withStyle(c));
                }
                for (var eff : bonus.effects()) {
                    if (!first) line.append(Component.literal(", ").withStyle(c));
                    first = false;
                    MutableComponent en = Component.translatable(eff.effect().value().getDescriptionId());
                    if (eff.amplifier() > 0) en.append(" ").append(Component.translatable("enchantment.level." + (eff.amplifier() + 1)));
                    line.append(en.withStyle(c));
                }
                for (var proc : bonus.procs()) {
                    if (!first) line.append(Component.literal(", ").withStyle(c));
                    first = false;
                    double power = proc.power(1f);
                    boolean pct = switch (proc.type()) { case LEECH, CLEAVE, REPRISAL, BACKLASH, FESTER -> true; default -> false; };
                    Component target = proc.target() == null ? Component.translatable("hallmarked.target.any")
                            : Component.translatableWithFallback("hallmarked.target." + proc.target().location().getPath().replace('/', '.'),
                                    proc.target().location().getPath());
                    line.append(Component.translatable("hallmarked.proc." + proc.type().id(), FMT.format(proc.chance(1f) * 100),
                            pct ? FMT.format(power * 100) : FMT.format(power), FMT.format(proc.duration() / 20.0), target).withStyle(c));
                }
                block.add(line);
            }
        }

        private static MutableComponent statLine(Marks.Entry en, String indent, boolean advanced) {
            AttributeModifier mod = en.modifier();
            MutableComponent line = Component.literal(indent).append(en.attribute().value().toComponent(mod, TooltipFlag.NORMAL)
                    .withStyle(mod.amount() > 0 ? ChatFormatting.BLUE : ChatFormatting.RED));
            if (en.converted()) {
                line.append(Component.literal(" ").append(Component.translatable("tooltip.hallmarked.converted"))
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
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
