package com.kragnoth.hallmarked.compat;

import com.kragnoth.hallmarked.Hallmarked;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Better Combat computes melee range on its own and ignores {@code entity_interaction_range}.
 * This forwards any bonus above vanilla's 3.0 so reach qualities (Long, Sweeping, ...) still work.
 * Done reflectively so Better Combat stays an optional, compile-free dependency.
 */
public final class BetterCombatCompat {
    private BetterCombatCompat() {}

    private static final double VANILLA_REACH = 3.0;

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void init() {
        try {
            Class<?> ext = Class.forName("net.bettercombat.api.client.AttackRangeExtensions");
            Class<?> modifierCls = Class.forName("net.bettercombat.api.client.AttackRangeExtensions$Modifier");
            Class<?> opCls = Class.forName("net.bettercombat.api.client.AttackRangeExtensions$Operation");
            Constructor<?> ctor = modifierCls.getDeclaredConstructor(double.class, opCls);
            Object add = Enum.valueOf((Class<Enum>) opCls, "ADD");
            Method register = ext.getDeclaredMethod("register", Function.class);

            Function<Object, Object> source = ctx -> {
                try {
                    Player p = (Player) ctx.getClass().getMethod("player").invoke(ctx);
                    double bonus = p.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE) - VANILLA_REACH;
                    return ctor.newInstance(bonus, add);
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            };
            register.invoke(null, source);
            Hallmarked.LOG.info("Better Combat reach integration enabled");
        } catch (ReflectiveOperationException | RuntimeException e) {
            Hallmarked.LOG.warn("Better Combat present but its range API was not found; reach qualities won't affect it", e);
        }
    }
}
