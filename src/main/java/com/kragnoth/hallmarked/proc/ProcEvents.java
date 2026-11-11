package com.kragnoth.hallmarked.proc;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.compat.CuriosCompat;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.quality.Equipment;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Fires affix procs for anything wearing graded gear: players and mobs alike. Server only. */
@EventBusSubscriber(modid = Hallmarked.MODID)
public final class ProcEvents {
    private ProcEvents() {}

    /**
     * Re-entrancy guard: proc damage (cleave, reprisal, backlash, fester) runs inside {@link #guarded}, and the damage
     * event returns early while it's set, so proc damage never triggers further procs. Only the damage itself is
     * guarded: wrapping whole proc runs would make every damaging proc see the guard set and do nothing.
     */
    private static boolean firing = false;

    static void guarded(Runnable r) {
        if (firing) return;
        firing = true;
        try {
            r.run();
        } finally {
            firing = false;
        }
    }

    private record Active(Proc proc, float roll) {}

    /** Procs from appraised affixes on items worn or held where those affixes actually apply. */
    private static List<Active> procsOf(LivingEntity entity, ProcType.Trigger trigger) {
        List<Active> out = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) collect(out, entity.getItemBySlot(slot), slot, trigger);
        if (entity instanceof Player && Hallmarked.curiosLoaded()) {
            for (ItemStack s : CuriosCompat.equipped(entity)) collect(out, s, null, trigger);
        }
        for (Proc p : com.kragnoth.hallmarked.sets.Sets.activeProcs(entity)) {
            if (p.type().trigger() == trigger) out.add(new Active(p, 1f));
        }
        return out;
    }

    private static void collect(List<Active> out, ItemStack stack, EquipmentSlot slot, ProcType.Trigger trigger) {
        if (stack.isEmpty() || Grading.isUnappraised(stack)) return;
        GradeData d = Grading.dataOf(stack);
        if (d == null) return;
        for (GradeData.Rolled r : d.affixes()) {
            Affix a = Grading.AFFIXES.get(r.affix());
            if (a == null || a.procs().isEmpty()) continue;
            Equipment.Applied ap = Equipment.applied(a, stack);
            if (ap == null) continue;
            boolean active = ap.slot() == null ? slot == null : slot != null && ap.slot().test(slot);
            if (!active) continue;
            for (Proc p : a.procs()) if (p.type().trigger() == trigger) out.add(new Active(p, r.roll()));
        }
    }

    private static boolean enabledFor(LivingEntity owner) {
        if (!HallmarkedConfig.PROCS.get()) return false;
        return owner instanceof Player || HallmarkedConfig.MOB_PROCS.get();
    }

    private static void run(List<Active> procs, LivingEntity owner, LivingEntity other, float damage, boolean melee) {
        for (Active a : procs) {
            if (!a.proc().targets(other)) continue;
            if (owner.getRandom().nextDouble() >= a.proc().chance(a.roll())) continue;
            a.proc().type().fire(new ProcType.Ctx(owner, other, damage, melee, a.proc().power(a.roll()), a.proc().duration()));
        }
    }

    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        if (firing || event.getNewDamage() <= 0) return;
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) return;
        var src = event.getSource();

        // HIT: the attacker's gear
        if (src.getEntity() instanceof LivingEntity attacker && attacker != target && enabledFor(attacker) && target.isAlive()) {
            boolean melee = src.getDirectEntity() == attacker;
            run(procsOf(attacker, ProcType.Trigger.HIT), attacker, target, event.getNewDamage(), melee);
        }
        // HURT: the victim's gear
        if (src.getEntity() instanceof LivingEntity attacker && attacker != target && enabledFor(target)) {
            run(procsOf(target, ProcType.Trigger.HURT), target, attacker, event.getNewDamage(), false);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dying = event.getEntity();
        if (dying.level().isClientSide() || !(event.getSource().getEntity() instanceof LivingEntity killer) || killer == dying) return;
        if (!enabledFor(killer)) return;
        run(procsOf(killer, ProcType.Trigger.KILL), killer, dying, dying.getMaxHealth(), false);
    }
}
