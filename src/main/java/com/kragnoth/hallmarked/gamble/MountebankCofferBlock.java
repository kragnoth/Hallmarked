package com.kragnoth.hallmarked.gamble;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Mountebank's job site: a banded, padlocked coffer that is never opened in front of customers.
 * <ul>
 *   <li>Right-click: open its 27 slots. Only the player who placed it can (or a creative player); anyone else hears
 *       the lock. Non-owners can't break it, and explosions don't touch it.</li>
 *   <li>Sneak + right-click holding gear: sell it (set pieces earn tokens, graded gear earns emeralds; see
 *       {@link TradeIn}).</li>
 * </ul>
 */
public class MountebankCofferBlock extends BaseEntityBlock {
    public static final MapCodec<MountebankCofferBlock> CODEC = simpleCodec(p -> new MountebankCofferBlock());
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public MountebankCofferBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).sound(SoundType.WOOD).strength(2.5F, 1200.0F));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CofferBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player p && level.getBlockEntity(pos) instanceof CofferBlockEntity be) be.setOwner(p);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CofferBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!be.mayOpen(player)) {
            player.displayClientMessage(Component.translatable("hallmarked.coffer.locked", be.ownerName()).withStyle(ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, be.items()),
                Component.translatable("block.hallmarked.mountebank_coffer")));
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.6f, 0.8f);
        return InteractionResult.CONSUME;
    }

    /** Non-owners make no progress breaking it. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CofferBlockEntity be && !be.mayOpen(player)) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CofferBlockEntity be) {
            Containers.dropContents(level, pos, be.items());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
