package com.kragnoth.hallmarked.gamble;

import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The coffer's 27 locked slots and its owner. Deliberately not a {@code Container} and with no item-handler
 * capability, so hoppers and pipes can't reach in: the only way in is the owner opening it.
 */
public class CofferBlockEntity extends BlockEntity {
    public static final int SIZE = 27;

    @Nullable private UUID owner;
    private String ownerName = "";
    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            CofferBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !isRemoved() && level != null && level.getBlockEntity(worldPosition) == CofferBlockEntity.this
                    && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
        }
    };

    public CofferBlockEntity(BlockPos pos, BlockState state) {
        super(HMRegistries.MOUNTEBANK_COFFER_BE.get(), pos, state);
    }

    public SimpleContainer items() { return items; }

    @Nullable public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    /** Owners, creative players, and anyone at all if the coffer has no owner (placed before locking existed). */
    public boolean mayOpen(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.isCreative();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items.getItems(), registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clearContent();
        ContainerHelper.loadAllItems(tag, items.getItems(), registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
