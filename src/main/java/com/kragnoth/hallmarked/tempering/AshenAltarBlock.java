package com.kragnoth.hallmarked.tempering;

import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.Grading;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Ashen Altar. Hold a graded piece in your main hand and a tempering catalyst (default: Echo Shard) in your
 * off hand, then use the altar. One catalyst is consumed and the piece is tempered: sometimes better, sometimes
 * worse, always locked.
 */
public class AshenAltarBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(box(1, 0, 1, 15, 4, 15), box(3, 4, 3, 13, 11, 13), box(0, 11, 0, 16, 14, 16));

    public AshenAltarBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).sound(SoundType.POLISHED_DEEPSLATE)
                .strength(3.0F, 9.0F).requiresCorrectToolForDrops().noOcclusion().lightLevel(s -> 5));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || !Tempering.canTemper(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack catalyst = player.getOffhandItem();
        if (!catalyst.is(Tempering.CATALYSTS)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("hallmarked.tempering.need_catalyst").withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        Grade before = Grading.gradeOf(stack);
        TemperOutcome outcome = Tempering.temper(stack, level.getRandom());
        if (outcome == null) return ItemInteractionResult.FAIL;
        if (!player.getAbilities().instabuild) catalyst.shrink(1);

        ServerLevel sl = (ServerLevel) level;
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 24, 0.25, 0.2, 0.25, 0.02);
        sl.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.2, 0.3, 0.2, 0.01);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 0.7F);

        player.displayClientMessage(Component.translatable(outcome.name()).withStyle(ChatFormatting.DARK_RED), true);
        Grade after = Grading.gradeOf(stack);
        if (player instanceof ServerPlayer sp && after != null && after != before && after.announce() != null) {
            Grading.announce(sp, stack, after);
        }
        return ItemInteractionResult.CONSUME;
    }
}
