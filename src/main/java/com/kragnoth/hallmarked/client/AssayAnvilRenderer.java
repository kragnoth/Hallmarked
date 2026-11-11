package com.kragnoth.hallmarked.client;

import com.kragnoth.hallmarked.block.AssayAnvilBlock;
import com.kragnoth.hallmarked.block.AssayAnvilBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Lays the equipment and the material flat on the anvil's working face. */
public class AssayAnvilRenderer implements BlockEntityRenderer<AssayAnvilBlockEntity> {
    private final ItemRenderer items;

    public AssayAnvilRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    @Override
    public void render(AssayAnvilBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float yaw = -be.getBlockState().getValue(AssayAnvilBlock.FACING).toYRot();

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));

        draw(be, be.items().getStackInSlot(AssayAnvilBlockEntity.SLOT_EQUIPMENT), -0.12, 0.05, 200F, pose, buffers, light, overlay);
        draw(be, be.items().getStackInSlot(AssayAnvilBlockEntity.SLOT_MATERIAL), 0.26, -0.2, 35F, pose, buffers, light, overlay);

        pose.popPose();
    }

    private void draw(AssayAnvilBlockEntity be, ItemStack stack, double x, double z, float spin,
                      PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        pose.translate(x, 1.01, z);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(0.45F, 0.45F, 0.45F);
        items.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
        pose.popPose();
    }
}
