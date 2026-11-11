package com.kragnoth.hallmarked.api.event;

import com.kragnoth.hallmarked.grade.GradeData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the game bus after a grade and its affixes are rolled, just before they are stamped onto the item.
 * Listeners may replace the result (e.g. force a minimum grade) or react to it (announce a Gearwrought find).
 */
public class ItemGradedEvent extends Event {
    private final ItemStack stack;
    private final String source;
    @Nullable private final Player player;
    @Nullable private final Level level;
    private GradeData result;

    public ItemGradedEvent(ItemStack stack, String source, @Nullable Player player, @Nullable Level level, GradeData result) {
        this.stack = stack;
        this.source = source;
        this.player = player;
        this.level = level;
        this.result = result;
    }

    public ItemStack getStack() { return stack; }
    public String getSource() { return source; }
    /** The player whose pickup or loot caused the roll, when known. */
    @Nullable public Player getPlayer() { return player; }
    @Nullable public Level getLevel() { return level; }
    public GradeData getResult() { return result; }
    public void setResult(GradeData result) { this.result = result; }
    /** Convenience: the rolled grade's name, e.g. "hallmarked.grade.masterwork". */
    public String getGrade() { return result.grade(); }
}
