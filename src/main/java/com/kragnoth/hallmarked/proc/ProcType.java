package com.kragnoth.hallmarked.proc;

import com.kragnoth.hallmarked.compat.NeoVitaeCompat;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/**
 * Code-defined proc behaviours that data can attach to affixes. Each has a fixed trigger and, optionally, a mod it
 * needs. A proc whose mod is missing is unavailable, so an affix using it can't load and never rolls.
 */
public enum ProcType {
    /** Bleeding: armor-ignoring damage over time. power = damage per second, duration in ticks. */
    BLEED("bleed", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            int amp = Math.max(0, (int) Math.round(c.power() * 2) - 1);
            c.other().addEffect(new MobEffectInstance(HMRegistries.BLEEDING, c.duration(), amp));
        }
    },
    /** Slowness II + Weakness for the duration. */
    STAGGER("stagger", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            c.other().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, c.duration(), 1));
            c.other().addEffect(new MobEffectInstance(MobEffects.WEAKNESS, c.duration(), 0));
        }
    },
    /** Heal the owner by power × damage dealt. */
    LEECH("leech", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            float heal = (float) (c.damage() * c.power());
            if (heal > 0) c.owner().heal(heal);
        }
    },
    /** Set the target alight for power seconds. */
    IGNITE("ignite", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            c.other().igniteForSeconds((float) c.power());
        }
    },
    /** Melee only: power × damage splashes to enemies within 2.5 blocks of the target. */
    CLEAVE("cleave", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            if (!c.melee()) return;
            float splash = (float) (c.damage() * c.power());
            if (splash <= 0) return;
            ProcEvents.guarded(() -> {
                for (LivingEntity e : c.other().level().getEntitiesOfClass(LivingEntity.class, c.other().getBoundingBox().inflate(2.5))) {
                    if (e == c.other() || e == c.owner() || !e.isAlive() || friendly(c.owner(), e)) continue;
                    e.hurt(c.owner().damageSources().mobAttack(c.owner()), splash);
                }
            });
        }
    },
    /** When the wearer is hurt: reflect power × damage back at the attacker. */
    REPRISAL("reprisal", Trigger.HURT, null) {
        @Override void fire(Ctx c) {
            float back = (float) (c.damage() * c.power());
            if (back > 0) ProcEvents.guarded(() -> c.other().hurt(c.owner().damageSources().thorns(c.owner()), back));
        }
    },
    /** On a kill: heal power hit points. */
    REAPER("reaper", Trigger.KILL, null) {
        @Override void fire(Ctx c) {
            c.owner().heal((float) c.power());
        }
    },
    /** Curse: hitting a matching target wounds the wielder for power × damage dealt (pair with a "target" tag). */
    BACKLASH("backlash", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            float back = (float) (c.damage() * c.power());
            if (back > 0) ProcEvents.guarded(() -> c.owner().hurt(c.owner().damageSources().magic(), back));
        }
    },
    /** Curse: when the wearer is hurt, they take power × damage more. */
    FESTER("fester", Trigger.HURT, null) {
        @Override void fire(Ctx c) {
            float more = (float) (c.damage() * c.power());
            if (more > 0) ProcEvents.guarded(() -> c.owner().hurt(c.owner().damageSources().magic(), more));
        }
    },
    /** Curse: each hit drains power points of hunger exhaustion from a player wielder. */
    FAMISH("famish", Trigger.HIT, null) {
        @Override void fire(Ctx c) {
            if (c.owner() instanceof Player p) p.causeFoodExhaustion((float) c.power());
        }
    },
    /** On a kill (player): feed power LP to the killer's NeoVitae soul network. Needs NeoVitae. */
    ANIMA("anima", Trigger.KILL, "neovitae") {
        @Override void fire(Ctx c) {
            if (c.owner() instanceof ServerPlayer sp) NeoVitaeCompat.addLP(sp, (int) Math.round(c.power()));
        }
    };

    public enum Trigger { HIT, HURT, KILL }

    /**
     * @param owner    entity wearing/holding the item
     * @param other    the target (HIT/KILL) or the attacker (HURT)
     * @param damage   damage dealt (HIT) or taken (HURT); the victim's max health for KILL
     * @param melee    HIT came from a direct melee strike
     */
    public record Ctx(LivingEntity owner, LivingEntity other, float damage, boolean melee, double power, int duration) {}

    private final String id;
    private final Trigger trigger;
    @Nullable private final String requiredMod;

    ProcType(String id, Trigger trigger, @Nullable String requiredMod) {
        this.id = id;
        this.trigger = trigger;
        this.requiredMod = requiredMod;
    }

    abstract void fire(Ctx ctx);

    public String id() { return id; }
    public Trigger trigger() { return trigger; }

    /** False when the proc needs a mod that isn't installed. */
    public boolean available() {
        return requiredMod == null || ModList.get().isLoaded(requiredMod);
    }

    @Nullable
    public static ProcType byId(String id) {
        return BY_ID.get(id);
    }

    private static final Map<String, ProcType> BY_ID;
    static {
        var m = new java.util.HashMap<String, ProcType>();
        for (ProcType t : values()) m.put(t.id, t);
        BY_ID = Map.copyOf(m);
    }

    static boolean friendly(LivingEntity owner, LivingEntity e) {
        if (e.isAlliedTo(owner)) return true;
        if (e instanceof OwnableEntity own && owner.getUUID().equals(own.getOwnerUUID())) return true;
        return owner instanceof Player && e instanceof Player; // no PvP splash from procs
    }
}
