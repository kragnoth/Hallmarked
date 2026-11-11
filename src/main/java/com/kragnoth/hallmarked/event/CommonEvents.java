package com.kragnoth.hallmarked.event;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.compat.CuriosCompat;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.grade.Marks;
import com.kragnoth.hallmarked.network.SyncQualitiesPayload;
import com.kragnoth.hallmarked.quality.Qualities;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = Hallmarked.MODID)
public final class CommonEvents {
    private CommonEvents() {}

    // ---- Data loading & sync ---------------------------------------------------------------

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new Qualities.Loader());
        event.addListener(Grading.GRADES.loader());
        event.addListener(Grading.AFFIXES.loader());
        event.addListener(Grading.FUSIONS.loader());
        event.addListener(com.kragnoth.hallmarked.inlay.Inlays.STORE.loader());
        event.addListener(Grading.UNIQUES.loader());
        event.addListener(com.kragnoth.hallmarked.quality.Conversion.STORE.loader());
        event.addListener(Grading.REGIONS.loader());
        event.addListener(com.kragnoth.hallmarked.tempering.Tempering.OUTCOMES.loader());
        event.addListener(new com.kragnoth.hallmarked.grade.TitleWords.Loader());
    }

    /** Fires for each joining player and for everyone after /reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncQualitiesPayload payload = SyncQualitiesPayload.snapshot();
        event.getRelevantPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, payload));
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        com.kragnoth.hallmarked.command.HallmarkedCommands.register(event.getDispatcher());
    }

    // ---- Item modifiers (quality + affixes on tools, bows, shields, armor) -----------------

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        for (Marks.Entry e : Marks.of(event.getItemStack())) {
            if (e.slot() != null) event.addModifier(e.attribute(), e.modifier(), e.slot());
        }
    }

    // ---- Damage ----------------------------------------------------------------------------

    /** Projectiles shot by a player are scaled by their projectile_damage attribute (pre-armor). */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_PROJECTILE)) return;
        if (!(event.getSource().getEntity() instanceof Player shooter)) return;
        AttributeInstance inst = shooter.getAttribute(HMRegistries.PROJECTILE_DAMAGE);
        if (inst == null) return;
        double mult = inst.getValue();
        if (mult != 1.0) event.setAmount((float) Math.max(0.0, event.getAmount() * mult));
    }

    /**
     * damage_resistance reduces the final (post-armor) damage by (value - 1), capped by config.
     * A reduction, never a heal-back, so stacking it with heavy armor can't make a player invincible.
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill, void
        AttributeInstance inst = player.getAttribute(HMRegistries.DAMAGE_RESISTANCE);
        if (inst == null) return;
        double resistance = Math.min(inst.getValue() - 1.0, HallmarkedConfig.MAX_DAMAGE_RESISTANCE.get());
        if (resistance <= 0.0) return;
        event.setNewDamage((float) (event.getNewDamage() * (1.0 - resistance)));
    }

    // ---- Per-player tick: first-seen rolls, legacy migration, names, bauble modifiers ------

    private static final int INTERVAL = 10;
    /** Last known health, kept in persistent data so bauble max-health isn't lost to the login clamp. */
    private static final String HEALTH_KEY = "hallmarked_health";

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.tickCount + player.getId()) % INTERVAL != 0) return;

        scanInventory(player);
        if (Hallmarked.curiosLoaded()) syncBaubles(player);
        if (player.isAlive()) player.getPersistentData().putFloat(HEALTH_KEY, player.getHealth());
    }

    /**
     * Vanilla loads saved health before our (transient) bauble modifiers exist, clamping it to the lower max.
     * Apply baubles immediately on login, then restore the health the player actually had.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        float capBefore = player.getMaxHealth();
        boolean clamped = player.getHealth() >= capBefore - 0.01F; // loaded health was cut down to the bauble-less max
        if (Hallmarked.curiosLoaded()) syncBaubles(player);
        var data = player.getPersistentData();
        if (clamped && data.contains(HEALTH_KEY) && player.isAlive()) {
            float saved = data.getFloat(HEALTH_KEY);
            if (saved > player.getHealth()) player.setHealth(Math.min(saved, player.getMaxHealth()));
        }
    }

    /** Fires before the player file is written, so this is the exact health at logout (no combat-log healing). */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()) {
            player.getPersistentData().putFloat(HEALTH_KEY, player.getHealth());
        }
    }

    /**
     * Mob gear: graded when the mob first enters the world (after its spawn equipment is in place, which
     * FinalizeSpawnEvent is too early for). Only ungraded stacks are touched, so reloading a chunk changes nothing.
     */
    @SubscribeEvent
    public static void onEntityJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof net.minecraft.world.entity.Mob mob)) return;
        if (!HallmarkedConfig.GRADING.get() || !HallmarkedConfig.MOB_EQUIPMENT.get()) return;
        var ctx = new Grading.Context(null, event.getLevel(), mob.position());
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = mob.getItemBySlot(slot);
            if (stack.isEmpty() || stack.has(HMRegistries.GRADE.get()) || !Grading.canGrade(stack)) continue;
            Grading.grade(stack, Grading.SOURCE_MOB, 0.0, mob.getRandom(), ctx);
            if (HallmarkedConfig.AUTO_REFORGE.get() && !stack.has(HMRegistries.QUALITY.get()) && Reforging.isEligible(stack)) {
                stack.set(HMRegistries.ROLLED.get(), true);
                if (mob.getRandom().nextDouble() < HallmarkedConfig.AUTO_REFORGE_CHANCE.get()) Reforging.reforge(stack, mob.getRandom());
            }
        }
    }

    /** Tags can change on /reload; equipment categories are cached per item. */
    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        com.kragnoth.hallmarked.quality.Equipment.clearCache();
    }

    private static void scanInventory(ServerPlayer player) {
        boolean autoQuality = HallmarkedConfig.AUTO_REFORGE.get();
        double chance = HallmarkedConfig.AUTO_REFORGE_CHANCE.get();
        boolean grading = HallmarkedConfig.GRADING.get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            processFirstSeen(player, stack, autoQuality, chance, grading);
        }
    }

    /**
     * Anything not graded by the loot modifier (crafted, traded, given) is treated as crafted work and
     * graded with the player's Luck. Crafted odds are lower than loot, but the top grade is never off the table.
     */
    private static void processFirstSeen(ServerPlayer player, ItemStack stack, boolean autoQuality, double chance, boolean grading) {
        Reforging.migrateLegacy(stack);
        // Appraisal switched off in the config: reveal anything still sealed from before.
        if (Grading.isUnappraised(stack) && !HallmarkedConfig.APPRAISAL.get()) Grading.appraise(stack, player);

        if (grading && !stack.has(HMRegistries.GRADE.get()) && Grading.canGrade(stack)) {
            Grading.grade(stack, Grading.SOURCE_CRAFTED, player.getLuck(), player.getRandom(), Grading.Context.of(player));
        } else if (stack.has(HMRegistries.GRADE.get()) || stack.has(HMRegistries.INLAYS.get()) || stack.has(HMRegistries.SEALED_INLAY.get())) {
            Grading.refreshName(stack);
        }

        if (autoQuality && !stack.has(HMRegistries.ROLLED.get()) && !stack.has(HMRegistries.QUALITY.get())
                && Reforging.isEligible(stack)) {
            stack.set(HMRegistries.ROLLED.get(), true);
            if (player.getRandom().nextDouble() < chance) Reforging.reforge(stack, player.getRandom());
        }
    }

    private static final String BAUBLE_PREFIX = "bauble/";

    /**
     * Stateless: the player's attributes are the record of what's applied. Every Hallmarked modifier whose id starts
     * with "bauble/" is compared against what the worn curios want right now; extras are removed, missing or changed
     * ones are (re)added. Nothing to drift out of step after respawns, crashes or other mods copying attributes.
     */
    private static void syncBaubles(ServerPlayer player) {
        Map<ResourceLocation, Desired> desired = new HashMap<>();
        List<ItemStack> worn = CuriosCompat.equipped(player);
        for (int slot = 0; slot < worn.size(); slot++) {
            ItemStack stack = worn.get(slot);
            if (stack.isEmpty() || !Marks.isBauble(stack)) continue;
            processFirstSeen(player, stack, HallmarkedConfig.AUTO_REFORGE.get(),
                    HallmarkedConfig.AUTO_REFORGE_CHANCE.get(), HallmarkedConfig.GRADING.get());
            for (Marks.Entry e : Marks.of(stack)) {
                if (e.slot() != null) continue;
                ResourceLocation id = Hallmarked.id(BAUBLE_PREFIX + slot + "/" + e.modifier().id().getPath());
                desired.put(id, new Desired(e.attribute(), new AttributeModifier(id, e.modifier().amount(), e.modifier().operation())));
            }
        }

        // Remove anything of ours that shouldn't be there (or sits on the wrong attribute).
        BuiltInRegistries.ATTRIBUTE.holders().forEach(holder -> {
            AttributeInstance inst = player.getAttribute(holder);
            if (inst == null) return;
            for (AttributeModifier mod : List.copyOf(inst.getModifiers())) {
                ResourceLocation id = mod.id();
                if (!id.getNamespace().equals(Hallmarked.MODID) || !id.getPath().startsWith(BAUBLE_PREFIX)) continue;
                Desired d = desired.get(id);
                if (d == null || !d.attribute().unwrapKey().equals(holder.unwrapKey())) inst.removeModifier(id);
            }
        });

        // Add missing or changed.
        for (var e : desired.entrySet()) {
            AttributeInstance inst = player.getAttribute(e.getValue().attribute());
            if (inst == null) continue;
            AttributeModifier current = inst.getModifier(e.getKey());
            AttributeModifier want = e.getValue().modifier();
            if (current == null || current.amount() != want.amount() || current.operation() != want.operation()) {
                if (current != null) inst.removeModifier(e.getKey());
                inst.addTransientModifier(want);
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private record Desired(Holder<Attribute> attribute, AttributeModifier modifier) {}
}
