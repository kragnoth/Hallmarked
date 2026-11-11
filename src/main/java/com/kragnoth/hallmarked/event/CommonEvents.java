package com.kragnoth.hallmarked.event;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.compat.CuriosCompat;
import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.network.SyncQualitiesPayload;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Qualities;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.Holder;
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
    }

    /** Fires for each joining player and for everyone after /reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncQualitiesPayload payload = new SyncQualitiesPayload(Qualities.toWire());
        event.getRelevantPlayers().forEach(p -> PacketDistributor.sendToPlayer(p, payload));
    }

    // ---- Item modifiers (tools, bows, shields, armor) --------------------------------------

    @SubscribeEvent
    public static void onItemAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        Quality q = Reforging.qualityOf(stack);
        if (q == null) return;
        Equipment.Applied applied = Equipment.applied(q, stack);
        if (applied == null || applied.slot() == null) return; // baubles are applied to the player instead

        for (Quality.Mod m : q.section(applied.section())) {
            m.attribute().ifPresent(attr -> event.addModifier(attr,
                    new AttributeModifier(itemModifierId(m, applied), m.value(), m.operation()),
                    applied.slot()));
        }
    }

    public static ResourceLocation itemModifierId(Quality.Mod m, Equipment.Applied applied) {
        return Hallmarked.id(m.name() + applied.idSuffix());
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

    // ---- Per-player tick: auto-reforge, legacy migration, bauble modifiers -----------------

    private static final int INTERVAL = 10;
    /** Bauble modifiers currently applied to each player: id -> attribute. Transient, rebuilt on respawn/login. */
    private static final Map<Player, Map<ResourceLocation, Holder<Attribute>>> APPLIED_BAUBLES = new WeakHashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if ((player.tickCount + player.getId()) % INTERVAL != 0) return;

        scanInventory(player);
        if (Hallmarked.curiosLoaded()) syncBaubles(player);
    }

    private static void scanInventory(ServerPlayer player) {
        boolean auto = HallmarkedConfig.AUTO_REFORGE.get();
        double chance = HallmarkedConfig.AUTO_REFORGE_CHANCE.get();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            Reforging.migrateLegacy(stack);
            if (!auto) continue;
            if (stack.has(HMRegistries.ROLLED.get()) || stack.has(HMRegistries.QUALITY.get())) continue;
            if (!Reforging.isEligible(stack)) continue;

            stack.set(HMRegistries.ROLLED.get(), true);
            if (player.getRandom().nextDouble() < chance) Reforging.reforge(stack, player.getRandom());
        }
    }

    private static void syncBaubles(ServerPlayer player) {
        Map<ResourceLocation, Desired> desired = new HashMap<>();
        List<ItemStack> worn = CuriosCompat.equipped(player);
        for (int slot = 0; slot < worn.size(); slot++) {
            ItemStack stack = worn.get(slot);
            if (stack.isEmpty()) continue;
            Reforging.migrateLegacy(stack);
            Quality q = Reforging.qualityOf(stack);
            if (q == null) continue;
            for (Quality.Mod m : q.section(Category.BAUBLE)) {
                ResourceLocation id = Hallmarked.id("bauble/" + slot + "/" + m.name());
                m.attribute().ifPresent(attr -> desired.put(id, new Desired(attr, new AttributeModifier(id, m.value(), m.operation()))));
            }
        }

        Map<ResourceLocation, Holder<Attribute>> applied = APPLIED_BAUBLES.computeIfAbsent(player, p -> new HashMap<>());

        // Remove stale
        var it = applied.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Desired d = desired.get(e.getKey());
            if (d == null || !d.attribute().equals(e.getValue())) {
                AttributeInstance inst = player.getAttribute(e.getValue());
                if (inst != null) inst.removeModifier(e.getKey());
                it.remove();
            }
        }
        // Add / update
        for (var e : desired.entrySet()) {
            AttributeInstance inst = player.getAttribute(e.getValue().attribute());
            if (inst == null) continue;
            AttributeModifier current = inst.getModifier(e.getKey());
            AttributeModifier want = e.getValue().modifier();
            if (current != null && current.amount() == want.amount() && current.operation() == want.operation()) {
                applied.put(e.getKey(), e.getValue().attribute());
                continue;
            }
            if (current != null) inst.removeModifier(e.getKey());
            inst.addTransientModifier(want);
            applied.put(e.getKey(), e.getValue().attribute());
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private record Desired(Holder<Attribute> attribute, AttributeModifier modifier) {}
}
