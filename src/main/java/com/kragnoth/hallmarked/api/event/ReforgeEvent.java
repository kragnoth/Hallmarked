package com.kragnoth.hallmarked.api.event;

import com.kragnoth.hallmarked.quality.Quality;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on the game bus when a player strikes the Assay Anvil, after the new quality is rolled but before
 * anything is consumed. Cancel to refuse the reforge; change the cost or the quality to taste.
 */
public class ReforgeEvent extends Event implements ICancellableEvent {
    private final Player player;
    private final ItemStack equipment;
    private final ItemStack material;
    private int cost;
    private Quality quality;

    public ReforgeEvent(Player player, ItemStack equipment, ItemStack material, int cost, Quality quality) {
        this.player = player;
        this.equipment = equipment;
        this.material = material;
        this.cost = cost;
        this.quality = quality;
    }

    public Player getPlayer() { return player; }
    /** Read-only view of the equipment as it is before the reforge. */
    public ItemStack getEquipment() { return equipment; }
    public ItemStack getMaterial() { return material; }
    public int getCost() { return cost; }
    /** Materials consumed; clamped to what's in the slot. 0 makes the reforge free. */
    public void setCost(int cost) { this.cost = Math.max(0, cost); }
    public Quality getQuality() { return quality; }
    public void setQuality(Quality quality) { this.quality = quality; }
}
