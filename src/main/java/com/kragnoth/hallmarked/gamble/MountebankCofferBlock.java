package com.kragnoth.hallmarked.gamble;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 *       the lock. An unowned coffer (placed before locking existed) belongs to whoever opens it first.</li>
 *   <li>Non-owners can't break it by any player action (mining, area tools, hammers), and explosions don't touch
 *       it. However it's broken, it drops as one item still holding its contents and owner, like a shulker box.</li>
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

    /** A coffer item carries its owner; only a fresh one is claimed by whoever places it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player p && level.getBlockEntity(pos) instanceof CofferBlockEntity be && be.owner() == null) be.setOwner(p);
    }

    /** Every player-driven break (mining, Ultimine, hammers, paxels) goes through here; non-owners are refused. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest,
                                       net.minecraft.world.level.material.FluidState fluid) {
        if (level.getBlockEntity(pos) instanceof CofferBlockEntity be && !be.mayOpen(player)) {
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("hallmarked.coffer.locked", be.ownerName()).withStyle(ChatFormatting.GRAY), true);
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Creative breaks drop no loot, so keep a non-empty coffer as an item, as shulker boxes do. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.isCreative() && level.getBlockEntity(pos) instanceof CofferBlockEntity be && !be.isEmpty()) {
            ItemStack drop = new ItemStack(this);
            drop.applyComponents(be.collectComponents());
            var entity = new net.minecraft.world.entity.item.ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CofferBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (be.owner() == null) be.setOwner(player); // claim an unowned (pre-lock) coffer
        if (!be.mayOpen(player)) {
            player.displayClientMessage(Component.translatable("hallmarked.coffer.locked", be.ownerName()).withStyle(ChatFormatting.GRAY), true);
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0f, 1.0f);
            return InteractionResult.CONSUME;
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CofferMenu(id, inv, be.items()),
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

    // Contents leave inside the dropped coffer item (loot table copy_components), never spilled.
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) level.updateNeighbourForOutputSignal(pos, this);
        super.onRemove(state, level, pos, newState, moved);
    }
}
