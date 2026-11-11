package com.kragnoth.hallmarked.client;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.config.HallmarkedClientConfig;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.Grading;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/**
 * Diablo-style loot beams and champion titles, entirely client-side. Mob equipment and item components are already
 * synced, so the client works out the best grade itself: no packets, and no custom name on the mob (a real custom
 * name would make it persistent and never despawn).
 */
@EventBusSubscriber(modid = Hallmarked.MODID, value = Dist.CLIENT)
public final class LootBeams {
    private LootBeams() {}

    /** Best-ranked grade among a mob's equipment that has beam or champion flags; also returns that stack. */
    private record Best(Grade grade, ItemStack stack) {}

    @Nullable
    private static Best best(Entity e) {
        if (e instanceof ItemEntity item) {
            Grade g = Grading.gradeOf(item.getItem());
            return g != null && g.beam() ? new Best(g, item.getItem()) : null;
        }
        if (e instanceof Mob mob) {
            Best best = null;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack s = mob.getItemBySlot(slot);
                if (s.isEmpty()) continue;
                Grade g = Grading.gradeOf(s);
                if (g == null || !(g.beam() || g.champion())) continue;
                if (best == null || g.rank() > best.grade().rank()) best = new Best(g, s);
            }
            return best;
        }
        return null;
    }

    private static int rgb(Grade g) {
        Integer c = g.color().getColor();
        return c == null ? 0xFFFFFF : c;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        boolean items = HallmarkedClientConfig.ITEM_BEAMS.get(), mobs = HallmarkedClientConfig.MOB_BEAMS.get();
        if (!items && !mobs) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        double range = HallmarkedClientConfig.BEAM_RANGE.get();
        double rangeSq = range * range;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long time = mc.level.getGameTime();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean drew = false;

        for (Entity e : mc.level.entitiesForRendering()) {
            boolean isItem = e instanceof ItemEntity;
            if (isItem ? !items : !(mobs && e instanceof Mob)) continue;
            if (e.distanceToSqr(cam) > rangeSq || !event.getFrustum().isVisible(e.getBoundingBoxForCulling())) continue;
            Best b = best(e);
            if (b == null || !b.grade().beam()) continue;

            Vec3 p = e.getPosition(partial);
            pose.pushPose();
            pose.translate(p.x - cam.x - 0.5, p.y - cam.y, p.z - cam.z - 0.5);
            int height = isItem ? 3 : 6;
            float core = isItem ? 0.06F : 0.09F;
            BeaconRenderer.renderBeaconBeam(pose, buffers, BeaconRenderer.BEAM_LOCATION, partial, 1.0F, time,
                    0, height, 0xFF000000 | rgb(b.grade()), core, core + 0.04F);
            pose.popPose();
            drew = true;
        }
        if (drew) buffers.endBatch();
    }

    /** "Zombie, Bearer of Grim Ledger" in the grade colour, always shown like a champion's name. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!HallmarkedClientConfig.MOB_TITLES.get() || !(event.getEntity() instanceof Mob mob) || mob.hasCustomName()) return;
        Best b = best(mob);
        if (b == null || !b.grade().champion()) return;
        event.setContent(Component.translatable("hallmarked.mob.bearer", mob.getType().getDescription(), b.stack().getHoverName())
                .withStyle(b.grade().color()));
        event.setCanRender(TriState.TRUE);
    }
}
