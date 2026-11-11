package com.kragnoth.hallmarked.api.event;

import com.kragnoth.hallmarked.grade.Grade;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the game bus before a grade is picked, with the final (luck-adjusted) weight of every grade.
 * Change weights to make grade odds depend on anything: dimension, depth, player progression, the item.
 *
 * <p>KubeJS, without any Hallmarked-specific plugin:
 * <pre>{@code
 * NativeEvents.onEvent('com.kragnoth.hallmarked.api.event.GradeRollEvent', e => {
 *   if (e.level && String(e.level.dimension().location()) == 'minecraft:the_nether')
 *     e.setWeight('hallmarked.grade.gearwrought', e.getWeight('hallmarked.grade.gearwrought') * 3)
 * })
 * }</pre>
 */
public class GradeRollEvent extends Event {
    private final ItemStack stack;
    private final String source;
    private final double luck;
    @Nullable private final Player player;
    @Nullable private final Level level;
    @Nullable private final Vec3 pos;
    private final Map<String, Double> weights;

    public GradeRollEvent(ItemStack stack, String source, double luck, @Nullable Player player, @Nullable Level level,
                          @Nullable Vec3 pos, Map<Grade, Double> weights) {
        this.stack = stack;
        this.source = source;
        this.luck = luck;
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.weights = new LinkedHashMap<>();
        weights.forEach((g, w) -> this.weights.put(g.name(), w));
    }

    /** Read-only view of the stack being graded. */
    public ItemStack getStack() { return stack; }
    /** "crafted", "loot", or a custom source from a loot modifier. */
    public String getSource() { return source; }
    public double getLuck() { return luck; }
    @Nullable public Player getPlayer() { return player; }
    @Nullable public Level getLevel() { return level; }
    @Nullable public Vec3 getPos() { return pos; }

    public double getWeight(String gradeName) { return weights.getOrDefault(gradeName, 0.0); }
    public void setWeight(String gradeName, double weight) {
        if (weights.containsKey(gradeName)) weights.put(gradeName, Math.max(0.0, weight));
    }
    /** Grade name -> weight, in load order. */
    public Map<String, Double> getWeights() { return Collections.unmodifiableMap(weights); }
}
