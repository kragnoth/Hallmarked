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
 * The coffer's 27 locked slots and its owner. Like a shulker box, both travel with the coffer when it's broken
 * (the {@code minecraft:container} and {@code hallmarked:coffer_owner} item components), so breaking or moving it
 * never spills anything or unlocks it.
 * <p> Deliberately not a {@code Container} and with no item-handler
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

    public boolean isEmpty() {
        return items.isEmpty();
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
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(items.getItems()));
        if (owner != null) components.set(HMRegistries.COFFER_OWNER.get(), new CofferOwner(owner, ownerName));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        items.clearContent();
        input.getOrDefault(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyInto(items.getItems());
        CofferOwner o = input.get(HMRegistries.COFFER_OWNER.get());
        if (o != null) {
            owner = o.id();
            ownerName = o.name();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Items");
        tag.remove("Owner");
        tag.remove("OwnerName");
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
