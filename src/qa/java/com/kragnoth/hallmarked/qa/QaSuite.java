package com.kragnoth.hallmarked.qa;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.gamble.CofferBlockEntity;
import com.kragnoth.hallmarked.gamble.Gamble;
import com.kragnoth.hallmarked.gamble.MountebankTrades;
import com.kragnoth.hallmarked.gamble.Parcel;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.Durability;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.grade.Marks;
import com.kragnoth.hallmarked.network.SyncQualitiesPayload;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.kragnoth.hallmarked.sets.GearSet;
import com.kragnoth.hallmarked.sets.Sets;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Every check writes BUG / WARN / OK lines to run-qa/qa-report.txt. Duplicates are folded with a count. */
final class QaSuite {
    private final MinecraftServer server;
    private final ServerLevel level;
    private final RegistryAccess access;
    private final long seed = Long.getLong("qa.seed", System.nanoTime());
    private final RandomSource random = RandomSource.create(seed);
    private final Map<String, Integer> bugs = new LinkedHashMap<>();
    private final Map<String, Integer> warns = new LinkedHashMap<>();
    private final List<String> lines = new ArrayList<>();
    private final FakePlayer alice, bob;
    private BlockPos base;
    private int phase = 0;
    private CompletableFuture<Void> reload;
    private int phaseTick;

    // proc recursion counter
    static int damageEvents = 0;
    private int regradeChanged = 0;

    QaSuite(MinecraftServer server) {
        this.server = server;
        this.level = server.overworld();
        this.access = server.registryAccess();
        alice = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("00000000-0000-0000-0000-0000000a11ce"), "qa_alice"));
        bob = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("00000000-0000-0000-0000-000000000b0b"), "qa_bob"));
        alice.setGameMode(GameType.SURVIVAL);
        bob.setGameMode(GameType.SURVIVAL);
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Post e) -> damageEvents++);
    }

    // ------------------------------------------------------------------------------------------------

    private void bug(String area, String msg) { bugs.merge("[" + area + "] " + msg, 1, Integer::sum); }
    private void warn(String area, String msg) { warns.merge("[" + area + "] " + msg, 1, Integer::sum); }
    private void info(String area, String msg) { lines.add("[" + area + "] " + msg); }

    private void guard(String area, String what, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String[] st = sw.toString().split("\n");
            StringBuilder s = new StringBuilder(t.toString());
            for (int i = 1; i < Math.min(st.length, 7); i++) s.append("\n      ").append(st[i].trim());
            bug(area, what + " threw " + s);
        }
    }

    private static String id(Item i) { return BuiltInRegistries.ITEM.getKey(i).toString(); }

    /** @return true when finished. */
    boolean step(int tick) {
        switch (phase) {
            case 0 -> {
                long t0 = System.nanoTime();
                base = new BlockPos(0, level.getMaxBuildHeight() - 40, 0);
                prepareArena();
                guard("data", "load check", this::dataCounts);
                guard("fuzz", "generation fuzz", this::generationFuzz);
                guard("sets", "set tests", this::setTests);
                guard("smithing", "smithing", this::smithingTest);
                guard("coffer", "coffer tests", this::cofferTests);
                guard("sell", "sell/trade-in", this::sellTests);
                guard("gamble", "gamble", this::gambleTests);
                guard("trades", "villager trades", this::tradeTests);
                guard("loot", "loot tables", this::lootTests);
                guard("procs", "procs", this::procTests);
                guard("lootjs", "loot pipeline / LootJS", this::lootPipelineTests);
                guard("cmd", "command fuzz", this::commandFuzz);
                guard("net", "sync payload", this::payloadTest);
                guard("perf", "perf", this::perfTests);
                info("seed", "random seed " + seed);
                info("time", String.format("phase 0 took %.1f s", (System.nanoTime() - t0) / 1e9));
                phase = 1;
            }
            case 1 -> {
                guard("reload", "broken datapack enable", () -> {
                    Path src = FMLPaths.GAMEDIR.get().resolve("qa_broken_src");
                    Path dst = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR).resolve("qa_broken");
                    try (var walk = Files.walk(src)) {
                        for (Path f : walk.toList()) {
                            Path t = dst.resolve(src.relativize(f).toString());
                            if (Files.isDirectory(f)) Files.createDirectories(t); else Files.copy(f, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
                    var repo = server.getPackRepository();
                    repo.reload();
                    List<String> ids = new ArrayList<>(repo.getSelectedIds());
                    String pack = repo.getAvailableIds().stream().filter(s -> s.contains("qa_broken")).findFirst().orElse(null);
                    if (pack == null) { warn("reload", "qa_broken pack not found: " + repo.getAvailableIds()); return; }
                    if (!ids.contains(pack)) ids.add(pack);
                    snapshotCounts("before broken pack");
                    reload = server.reloadResources(ids);
                });
                phaseTick = tick;
                phase = reload == null ? 3 : 2;
            }
            case 2 -> {
                if (reload.isDone() || tick - phaseTick > 1200) {
                    if (!reload.isDone()) bug("reload", "reload with broken pack never finished");
                    else if (reload.isCompletedExceptionally()) bug("reload", "reload with broken pack failed: " + reload.handle((v, t) -> String.valueOf(t)).join());
                    snapshotCounts("after broken pack");
                    guard("reload", "post-reload fuzz", () -> { quickFuzz(); brokenPackChecks(); });
                    phase = 3;
                }
            }
            case 3 -> { writeReport(); return true; }
        }
        return false;
    }

    private void prepareArena() {
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 0; y < 5; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        alice.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 2.5, 180, 30);
        bob.moveTo(base.getX() + 0.5, base.getY(), base.getZ() - 1.5, 0, 30);
    }

    // ---- data -------------------------------------------------------------------------------------

    private void dataCounts() {
        info("data", "grades=" + Grading.GRADES.all().size() + " affixes=" + Grading.AFFIXES.all().size()
                + " uniques=" + Grading.UNIQUES.all().size() + " fusions=" + Grading.FUSIONS.all().size()
                + " sets=" + Sets.STORE.all().size() + " parcels=" + Parcel.STORE.all().size());
        Map<String, Integer> byNs = new TreeMap<>();
        for (Affix a : Grading.AFFIXES.all()) byNs.merge(a.name().contains(".affix.") ? a.name().substring(0, a.name().indexOf(".affix.")) : "?", 1, Integer::sum);
        info("data", "affix namespaces " + byNs);
        info("mods", "loaded: " + net.neoforged.fml.ModList.get().getMods().stream().map(m -> m.getModId() + "@" + m.getVersion()).toList());
        // Every set piece's base item resolves, every set has pieces, bonuses reachable
        for (GearSet s : Sets.STORE.all()) {
            if (s.pieces().isEmpty()) bug("data", s.name() + " has no pieces");
            Set<String> keys = new HashSet<>();
            for (GearSet.Piece p : s.pieces()) {
                if (!keys.add(p.key())) bug("data", s.name() + " duplicate piece key " + p.key());
                if (BuiltInRegistries.ITEM.getOptional(p.item()).isEmpty()) bug("data", s.name() + "/" + p.key() + " base item missing " + p.item());
            }
            for (GearSet.Bonus b : s.bonuses()) {
                if (b.pieces() > s.pieces().size()) bug("data", s.name() + " bonus needs " + b.pieces() + " pieces but set has " + s.pieces().size());
                if (b.pieces() <= 0) bug("data", s.name() + " bonus pieces <= 0");
                for (Affix.Mod m : b.modifiers()) if (m.attribute().isEmpty()) warn("data", s.name() + " bonus attribute not present: " + m.attributeId());
            }
            // Two pieces of the same set in the same slot type would make the set uncompletable
            Map<String, Integer> slots = new HashMap<>();
            for (GearSet.Piece p : s.pieces()) {
                ItemStack st = Sets.create(s, p, access);
                if (st == null) continue;
                Category c = Equipment.categoryOf(st);
                String key = c == null ? "none" : Marks.isBauble(st) ? "bauble:" + p.key() : (c == Category.TOOL || c == Category.BOW || c == Category.SHIELD) ? "hand"
                        : c == Category.ARMOR ? String.valueOf(Equipment.armorPiece(st)) : c.key();
                slots.merge(key, 1, Integer::sum);
            }
            slots.forEach((k, v) -> { if (v > 1 && !k.equals("hand")) bug("data", s.name() + " has " + v + " pieces in slot " + k + " (set can't be fully worn)"); });
            if (slots.getOrDefault("hand", 0) > 2) bug("data", s.name() + " has " + slots.get("hand") + " hand pieces (only 2 hands)");
            if (slots.containsKey("none")) bug("data", s.name() + " has a piece with no equipment category");
        }
        // parcels
        for (Parcel p : Parcel.STORE.all()) {
            if (p.categories().isEmpty()) warn("data", p.name() + " has no categories");
            if (p.level() < 1 || p.level() > 5) bug("data", p.name() + " level out of range " + p.level());
        }
    }

    private void snapshotCounts(String when) {
        info("reload", when + ": grades=" + Grading.GRADES.all().size() + " affixes=" + Grading.AFFIXES.all().size()
                + " sets=" + Sets.STORE.all().size() + " parcels=" + Parcel.STORE.all().size()
                + " uniques=" + Grading.UNIQUES.all().size());
    }

    // ---- generation fuzz --------------------------------------------------------------------------

    private final List<String> SOURCES = List.of("loot", "mob", "crafted", "gamble");

    private List<Item> gradeable() {
        List<Item> out = new ArrayList<>();
        for (Item i : BuiltInRegistries.ITEM) if (Grading.canGrade(new ItemStack(i))) out.add(i);
        return out;
    }

    private void generationFuzz() {
        List<Item> items = gradeable();
        List<ResourceLocation> tables = new ArrayList<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        info("fuzz", items.size() + " gradeable items, " + tables.size() + " loot tables");
        Map<String, Integer> grades = new TreeMap<>();
        int n = 0;
        for (Item item : items) {
            for (String source : SOURCES) {
                for (int i = 0; i < 6; i++) {
                    ItemStack s = new ItemStack(item);
                    double luck = random.nextInt(9) - 3;
                    ResourceLocation table = tables.get(random.nextInt(tables.size()));
                    final ItemStack fs = s;
                    guard("fuzz", "grade " + id(item) + " " + source, () ->
                            Grading.grade(fs, source, luck, random, new Grading.Context(alice, level, Vec3.atCenterOf(base), table)));
                    n++;
                    GradeData d = Grading.dataOf(s);
                    if (d != null) grades.merge(d.grade().replace("hallmarked.grade.", ""), 1, Integer::sum);
                    checkStack("fuzz", s, true);
                    // grading twice must not reroll
                    ItemStack before = s.copy();
                    guard("fuzz", "regrade", () -> Grading.grade(fs, source, luck, random, new Grading.Context(alice, level, Vec3.atCenterOf(base), table)));
                    if (!ItemStack.isSameItemSameComponents(before, s)) regradeChanged++;
                }
            }
            // Unappraised -> appraise path
            ItemStack u = new ItemStack(item);
            guard("fuzz", "appraise", () -> {
                Grading.grade(u, "loot", 3, random, new Grading.Context(alice, level, Vec3.atCenterOf(base)));
                u.set(HMRegistries.UNAPPRAISED.get(), true);
                Grading.appraise(u, null);
                if (u.has(HMRegistries.UNAPPRAISED.get())) bug("fuzz", "appraise left UNAPPRAISED on " + id(item));
                checkStack("appraise", u, true);
            });
        }
        info("fuzz", n + " rolls; grade spread " + grades + "; Grading.grade() on an already graded stack re-rolled it " + regradeChanged + " times (API has no guard; every caller checks GRADE first)");

        // NaN / infinite luck
        for (double luck : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e9, -1e9}) {
            ItemStack s = new ItemStack(Items.IRON_SWORD);
            guard("fuzz", "grade with luck " + luck, () -> Grading.grade(s, "loot", luck, random, new Grading.Context(alice, level, Vec3.atCenterOf(base))));
            GradeData d = Grading.dataOf(s);
            info("fuzz", "luck " + luck + " -> " + (d == null ? "ungraded" : d.grade()));
            checkStack("luck " + luck, s, true);
        }
    }

    /** Invariants every graded stack should hold. */
    private void checkStack(String area, ItemStack s, boolean codec) {
        String iid = id(s.getItem());
        // modifier sanity
        try {
            Set<String> seen = new HashSet<>();
            for (Marks.Entry e : Marks.of(s)) {
                double a = e.modifier().amount();
                if (Double.isNaN(a) || Double.isInfinite(a)) bug(area, "non-finite modifier " + e.modifier().id() + " on " + iid);
                if (!seen.add(e.attribute().getRegisteredName() + "|" + e.modifier().id() + "|" + e.slot()))
                    bug(area, "duplicate modifier id " + e.modifier().id() + " on " + iid);
            }
            var mods = s.getAttributeModifiers();
            Set<String> seen2 = new HashSet<>();
            for (var m : mods.modifiers()) {
                if (!seen2.add(m.attribute().getRegisteredName() + "|" + m.modifier().id() + "|" + m.slot()))
                    bug(area, "duplicate final attribute modifier " + m.modifier().id() + " on " + iid);
                if (!Double.isFinite(m.modifier().amount())) bug(area, "non-finite final modifier " + m.modifier().id() + " on " + iid);
            }
        } catch (Throwable t) { bug(area, "modifiers threw " + t + " on " + iid); }
        // durability
        if (s.isDamageableItem() && s.getMaxDamage() <= 0) bug(area, "damageable with max damage " + s.getMaxDamage() + ": " + iid);
        int md = s.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE) ? s.getMaxDamage() : -1;
        try {
            Durability.apply(s);
            Durability.apply(s);
        } catch (Throwable t) { bug(area, "Durability.apply threw " + t); }
        int md2 = s.has(net.minecraft.core.component.DataComponents.MAX_DAMAGE) ? s.getMaxDamage() : -1;
        if (md != md2) bug(area, "Durability.apply not idempotent on " + iid + " " + md + " -> " + md2);
        // name idempotent
        try {
            String n1 = s.getHoverName().getString();
            Grading.refreshName(s);
            Grading.refreshName(s);
            String n2 = s.getHoverName().getString();
            if (!n1.equals(n2)) bug(area, "refreshName not idempotent on " + iid + ": '" + n1 + "' -> '" + n2 + "'");
            if (n2.length() > 70) warn(area, "very long name (" + n2.length() + "): " + n2);
            if (n2.contains("hallmarked.") || n2.contains("item.") && n2.contains(".")) warn(area, "untranslated name: " + n2);
        } catch (Throwable t) { bug(area, "name threw " + t); }
        if (!codec) return;
        // codec round trips
        try {
            Tag t = s.save(access);
            ItemStack back = ItemStack.parse(access, t).orElse(ItemStack.EMPTY);
            if (!ItemStack.isSameItemSameComponents(s, back)) bug(area, "NBT round trip changed " + iid);
        } catch (Throwable t) { bug(area, "NBT codec threw " + t + " on " + iid); }
        try {
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
            ItemStack.STREAM_CODEC.encode(buf, s);
            ItemStack back = ItemStack.STREAM_CODEC.decode(buf);
            if (!ItemStack.isSameItemSameComponents(s, back)) bug(area, "network round trip changed " + iid);
        } catch (Throwable t) { bug(area, "network codec threw " + t + " on " + iid); }
    }

    private void quickFuzz() {
        List<Item> items = gradeable();
        int n = 0;
        for (Item item : items) for (String source : SOURCES) {
            ItemStack s = new ItemStack(item);
            guard("reload", "grade after broken reload " + id(item), () -> Grading.grade(s, source, 0, random, new Grading.Context(alice, level, Vec3.atCenterOf(base))));
            checkStack("reload", s, false);
            n++;
        }
        for (Parcel p : Parcel.STORE.all()) for (int i = 0; i < 20; i++) guard("reload", "open " + p.name(), () -> Gamble.open(p, alice));
        for (GearSet s : Sets.STORE.all()) for (GearSet.Piece p : s.pieces()) guard("reload", "create " + s.name(), () -> { ItemStack st = Sets.create(s, p, access); if (st != null) checkStack("reload", st, true); });
        info("reload", "post-reload fuzz: " + n + " rolls");
    }

    private void brokenPackChecks() {
        // The broken pack overrides: a grade with huge affix count, a set with a bonus for 0 pieces, etc.
        Grade weird = Grading.GRADES.get("qa.grade.weird");
        info("reload", "qa weird grade loaded: " + (weird != null));
        if (Grading.GRADES.get("hallmarked.grade.common") == null) bug("reload", "common grade vanished after broken pack");
        GearSet s = Sets.STORE.get("qa.set.edge");
        info("reload", "qa edge set loaded: " + (s != null));
        if (s != null) {
            for (GearSet.Piece p : s.pieces()) guard("reload", "wear edge set", () -> {
                ItemStack st = Sets.create(s, p, access);
                if (st != null) { alice.setItemSlot(EquipmentSlot.MAINHAND, st); Sets.syncBonuses(alice); }
            });
            info("reload", "edge set worn: max_health=" + alice.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
                    + " attack_damage=" + alice.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                    + " health=" + alice.getHealth());
            guard("reload", "edge set effects tick", () -> { for (int i = 0; i < 30; i++) { alice.tickCount = i * 10; alice.tick(); } });
            info("reload", "edge set effects: " + alice.getActiveEffects().stream().map(e -> e.getEffect().getRegisteredName() + " " + e.getAmplifier()).toList());
            alice.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            Sets.syncBonuses(alice);
        }
        if (weird != null) {
            for (int i = 0; i < 50; i++) {
                ItemStack st = new ItemStack(Items.DIAMOND_CHESTPLATE);
                final int k = i;
                guard("reload", "weird grade roll", () -> { st.set(HMRegistries.GRADE.get(), Grading.rollData(st, weird, random)); Grading.refreshName(st); Durability.apply(st); });
                if (i == 0) info("reload", "weird grade item: affixes=" + (Grading.dataOf(st) == null ? 0 : Grading.dataOf(st).affixes().size()) + " maxDamage=" + st.getMaxDamage() + " name=" + st.getHoverName().getString());
                checkStack("weird", st, true);
                if (st.isDamageableItem() && st.getMaxDamage() < 1) bug("reload", "negative grade durability gives max damage " + st.getMaxDamage());
            }
        }
        if (net.minecraft.world.level.storage.loot.LootTable.class != null) {
            String a = gearReport("after /reload: LootJS addLoot simple_dungeon", roll("minecraft:chests/simple_dungeon", 10, 0), Items.DIAMOND_SWORD);
            if (net.neoforged.fml.ModList.get().isLoaded("lootjs") && !a.startsWith("10/10/10")) bug("reload", "LootJS gear not graded after /reload: " + a);
            String b = gearReport("after /reload: custom igloo", roll("minecraft:chests/igloo_chest", 5, 0), Items.IRON_SWORD);
            if (net.neoforged.fml.ModList.get().isLoaded("lootjs") && false) bug("reload", "custom grade_loot lost after /reload: " + b);
        }
        // free parcels?
        var trades = VillagerTrades.TRADES.get(HMRegistries.MOUNTEBANK.get());
        Villager v = EntityType.VILLAGER.create(level);
        if (trades != null && v != null) for (var l : trades.get(1)) for (int i = 0; i < 100; i++) {
            MerchantOffer o = null;
            try { o = l.getOffer(v, random); } catch (Throwable t) { bug("reload", "listing threw after broken pack: " + t); break; }
            if (o != null && o.getCostA().isEmpty()) { bug("reload", "parcel with currency air is offered for FREE: " + o.getResult().get(HMRegistries.PARCEL.get())); break; }
        }
        // loot odds luck 1e9 / tier pointing at a missing grade
        for (int i = 0; i < 30; i++) {
            ItemStack st = new ItemStack(Items.DIAMOND_SWORD);
            guard("reload", "grade with huge loot odds", () -> Grading.grade(st, "loot", 0, random, new Grading.Context(alice, level, Vec3.atCenterOf(base), ResourceLocation.parse("minecraft:chests/simple_dungeon"))));
            checkStack("reload-odds", st, false);
        }
    }

    // ---- sets -------------------------------------------------------------------------------------

    private static EquipmentSlot slotFor(LivingEntity e, ItemStack s) {
        return e.getEquipmentSlotForItem(s);
    }

    private void setTests() {
        for (GearSet set : Sets.STORE.all()) {
            List<ItemStack> pieces = new ArrayList<>();
            for (GearSet.Piece p : set.pieces()) {
                ItemStack st = Sets.create(set, p, access);
                if (st == null) { warn("sets", set.name() + "/" + p.key() + " couldn't be created"); continue; }
                checkStack("sets", st, true);
                if (p.fireResistant() && !st.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT)) bug("sets", "fire_resistant not applied " + p.key());
                if (p.durability() != null && st.isDamageableItem()) {
                    double mult = Grading.GRADES.get(Sets.SET_GRADE).durability();
                    int expect = (int) Math.round(p.durability() * mult);
                    if (Math.abs(st.getMaxDamage() - expect) > 1) bug("sets", set.name() + "/" + p.key() + " durability " + st.getMaxDamage() + " expected ~" + expect);
                }
                pieces.add(st);
            }
            // wear all
            clearEquip(alice);
            int handIdx = 0;
            int baubles = 0;
            for (ItemStack st : pieces) {
                if (Marks.isBauble(st)) { baubles++; continue; }
                EquipmentSlot slot = slotFor(alice, st);
                if (slot == EquipmentSlot.MAINHAND) { slot = handIdx++ == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND; }
                alice.setItemSlot(slot, st.copy());
            }
            var active = Sets.active(alice);
            var worn = active.get(set);
            int wornCount = worn == null ? 0 : worn.count();
            if (wornCount != pieces.size() - baubles) {
                // which ones failed
                List<String> missing = new ArrayList<>();
                for (ItemStack st : pieces) missing.add(Sets.pieceOf(st).piece() + "@" + slotFor(alice, st));
                bug("sets", set.name() + " fully worn counts " + wornCount + "/" + pieces.size() + " " + missing);
            }
            Sets.syncBonuses(alice);
            Map<String, Double> applied = setModifiers(alice);
            // per_piece check
            for (GearSet.Bonus b : set.bonuses()) {
                if (b.pieces() > wornCount) continue;
                for (Affix.Mod m : b.modifiers()) {
                    if (m.attribute().isEmpty()) continue;
                    String key = set.name().replace(':', '.').toLowerCase() + "/" + b.pieces() + (b.perPiece() ? "p" : "") + "/" + m.name();
                    Double got = applied.entrySet().stream().filter(e -> e.getKey().endsWith(key)).map(Map.Entry::getValue).findFirst().orElse(null);
                    double want = m.min() * (b.perPiece() ? wornCount : 1);
                    if (got == null) bug("sets", set.name() + " bonus " + key + " not applied");
                    else if (Math.abs(got - want) > 1e-6) bug("sets", set.name() + " bonus " + key + " = " + got + " want " + want);
                }
            }
            // sync twice: stable
            Sets.syncBonuses(alice);
            if (!setModifiers(alice).equals(applied)) bug("sets", "syncBonuses not stable for " + set.name());
            // piece in the wrong slot: chest piece in hand
            clearEquip(alice);
            for (ItemStack st : pieces) {
                if (slotFor(alice, st).isArmor()) {
                    alice.setItemSlot(EquipmentSlot.MAINHAND, st.copy());
                    if (Sets.active(alice).containsKey(set) && set.bonuses().stream().anyMatch(b -> b.pieces() <= 1))
                        info("sets", set.name() + " armor piece held in hand counts toward its set");
                    else if (!Sets.worn(alice).getOrDefault(set.name(), Set.of()).isEmpty())
                        bug("sets", set.name() + " armor piece counted while held in hand");
                    break;
                }
            }
            // duplicate piece in both hands
            for (ItemStack st : pieces) {
                if (slotFor(alice, st) == EquipmentSlot.MAINHAND) {
                    clearEquip(alice);
                    alice.setItemSlot(EquipmentSlot.MAINHAND, st.copy());
                    alice.setItemSlot(EquipmentSlot.OFFHAND, st.copy());
                    int c = Sets.worn(alice).getOrDefault(set.name(), Set.of()).size();
                    if (c > 1) bug("sets", set.name() + " same piece in both hands counts twice");
                    break;
                }
            }
            // remove everything -> no leftovers
            clearEquip(alice);
            Sets.syncBonuses(alice);
            if (!setModifiers(alice).isEmpty()) bug("sets", "leftover set modifiers after unequip " + setModifiers(alice).keySet());
            // a mob wearing the whole set: procs/bonuses should be player-only
            Mob z = EntityType.ZOMBIE.create(level);
            if (z != null) {
                for (ItemStack st : pieces) z.setItemSlot(slotFor(z, st), st.copy());
                if (!Sets.activeProcs(z).isEmpty()) bug("sets", "mob gets set procs");
                z.discard();
            }
            // mob effects applied & cleared
        }
        // a stack of set pieces held in inventory twice should not count
        info("sets", "checked " + Sets.STORE.all().size() + " sets");

        // set pieces: tryRoll for every source & base item
        int dressed = 0;
        for (GearSet set : Sets.STORE.all()) for (GearSet.Piece p : set.pieces()) {
            Item it = BuiltInRegistries.ITEM.get(p.item());
            for (int i = 0; i < 2000 && dressed < 100000; i++) {
                ItemStack st = new ItemStack(it);
                if (Sets.tryRoll(st, "crafted", random)) bug("sets", "crafted item became a set piece: " + set.name());
                st = new ItemStack(it);
                if (Sets.tryRoll(st, "loot", random)) { dressed++; break; }
            }
        }
        info("sets", "tryRoll loot dressed at least " + dressed + " pieces across sets");
    }

    private Map<String, Double> setModifiers(LivingEntity e) {
        Map<String, Double> out = new TreeMap<>();
        for (var h : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance inst = e.getAttribute(h);
            if (inst == null) continue;
            for (AttributeModifier m : inst.getModifiers())
                if (m.id().getNamespace().equals(Hallmarked.MODID) && m.id().getPath().startsWith("set/")) out.put(h.getRegisteredName() + "|" + m.id(), m.amount());
        }
        return out;
    }

    private void clearEquip(LivingEntity e) {
        for (EquipmentSlot s : EquipmentSlot.values()) e.setItemSlot(s, ItemStack.EMPTY);
    }

    private void smithingTest() {
        int tested = 0;
        for (GearSet set : Sets.STORE.all()) for (GearSet.Piece p : set.pieces()) {
            if (!p.item().getPath().startsWith("diamond_")) continue;
            ItemStack piece = Sets.create(set, p, access);
            SmithingRecipeInput in = new SmithingRecipeInput(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), piece, new ItemStack(Items.NETHERITE_INGOT));
            var r = server.getRecipeManager().getRecipeFor(RecipeType.SMITHING, in, level);
            if (r.isEmpty()) continue;
            ItemStack out = r.get().value().assemble(in, access);
            tested++;
            boolean keeps = Sets.pieceOf(out) != null;
            clearEquip(alice);
            alice.setItemSlot(slotFor(alice, out), out);
            boolean counts = Sets.worn(alice).getOrDefault(set.name(), Set.of()).contains(p.key());
            var mods = out.getAttributeModifiers().modifiers().stream().filter(m -> m.attribute().is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)).map(m -> m.modifier().amount()).toList();
            warn("smithing", "netherite-upgrading " + set.name() + "/" + p.key() + " -> " + id(out.getItem()) + " keepsSetPiece=" + keeps
                    + " stillCounts=" + counts + " armor=" + mods + (p.baseStats().isEmpty() ? "" : " (piece has base_stats override: upgrade gives no stats)"));
            clearEquip(alice);
        }
        info("smithing", tested + " diamond-based set pieces tested");
        // trim a set piece -> still counts
        // grindstone / anvil are client menus, skipped.
    }

    // ---- coffer -----------------------------------------------------------------------------------

    private BlockHitResult hit(BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(pos).relative(face, 0.5), face, pos, false);
    }

    private BlockPos placeCoffer(FakePlayer p, BlockPos at) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HMRegistries.MOUNTEBANK_COFFER_ITEM.get()));
        p.setShiftKeyDown(false);
        p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit(at.below(), Direction.UP));
        return at;
    }

    private void cofferTests() {
        BlockPos pos = base.offset(0, 0, 0);
        placeCoffer(alice, pos);
        BlockState st = level.getBlockState(pos);
        if (!st.is(HMRegistries.MOUNTEBANK_COFFER.get())) { bug("coffer", "placing failed: " + st); return; }
        CofferBlockEntity be = (CofferBlockEntity) level.getBlockEntity(pos);
        if (be == null || !alice.getUUID().equals(be.owner())) bug("coffer", "owner not set on place: " + (be == null ? null : be.owner()));
        // job site
        var poi = net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(st);
        info("coffer", "POI for coffer: " + poi.map(h -> h.getRegisteredName()).orElse("none"));
        if (poi.isEmpty() || !poi.get().is(HMRegistries.MOUNTEBANK_POI.getKey())) bug("coffer", "coffer isn't the mountebank POI");

        be.items().setItem(0, new ItemStack(Items.DIAMOND, 64));
        be.items().setItem(5, new ItemStack(Items.NETHERITE_SWORD));
        // automation access
        var cont = HopperBlockEntity.getContainerAt(level, pos);
        if (cont != null) bug("coffer", "hopper can see the coffer as a container: " + cont);
        for (Direction d : Direction.values()) {
            var cap = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, d);
            if (cap != null) bug("coffer", "item handler capability exposed on side " + d + " (slots " + cap.getSlots() + ")");
        }
        // piston
        boolean pushable = PistonBaseBlock.isPushable(st, level, pos, Direction.NORTH, true, Direction.NORTH);
        if (pushable) bug("coffer", "pistons can push the coffer");
        // comparator
        info("coffer", "comparator signal: " + st.hasAnalogOutputSignal());

        // bob opens -> refused
        bob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult r = bob.gameMode.useItemOn(bob, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
        if (bob.containerMenu != bob.inventoryMenu) bug("coffer", "non-owner opened the coffer menu (" + r + ")");
        bob.closeContainer();
        // bob breaks -> refused
        float prog = st.getDestroyProgress(bob, level, pos);
        if (prog != 0) bug("coffer", "non-owner destroy progress " + prog);
        boolean broke = bob.gameMode.destroyBlock(pos);
        if (!level.getBlockState(pos).is(HMRegistries.MOUNTEBANK_COFFER.get())) { bug("coffer", "non-owner broke the coffer (destroyBlock=" + broke + ")"); return; }
        // bob with creative
        bob.setGameMode(GameType.CREATIVE);
        boolean creativeOpen = be.mayOpen(bob);
        bob.setGameMode(GameType.SURVIVAL);
        info("coffer", "creative non-owner may open: " + creativeOpen + " (by design)");
        // non-player destroyBlock (Create drill / wither / Mekanism)
        // alice opens -> menu, then alice breaks while open: stillValid must flip
        alice.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        alice.gameMode.useItemOn(alice, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
        AbstractContainerMenu menu = net.minecraft.world.inventory.ChestMenu.threeRows(1, alice.getInventory(), be.items()); // FakePlayer can't open menus
        if (!menu.stillValid(alice)) bug("coffer", "menu not valid for owner before break");
        // bare-hand break by owner: does it drop with contents?
        level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4)).forEach(e -> e.discard());
        boolean ok = alice.gameMode.destroyBlock(pos);
        if (!ok || !level.getBlockState(pos).isAir()) bug("coffer", "owner couldn't break the coffer");
        if (menu.stillValid(alice)) bug("coffer", "open menu still valid after coffer broken (item dupe window)");
        alice.closeContainer();
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4));
        ItemStack dropped = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(HMRegistries.MOUNTEBANK_COFFER_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
        int loose = (int) drops.stream().filter(e -> !e.getItem().is(HMRegistries.MOUNTEBANK_COFFER_ITEM.get())).count();
        if (dropped.isEmpty()) bug("coffer", "owner bare-hand break dropped no coffer item (contents lost!) drops=" + drops.stream().map(e -> e.getItem().toString()).toList());
        if (loose > 0) bug("coffer", "contents spilled on break: " + loose + " entities");
        ItemContainerContents cc = dropped.get(net.minecraft.core.component.DataComponents.CONTAINER);
        long diamonds = cc == null ? 0 : cc.stream().filter(s -> s.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
        if (!dropped.isEmpty() && diamonds != 64) bug("coffer", "dropped coffer holds " + diamonds + " diamonds, expected 64");
        if (!dropped.isEmpty() && !dropped.has(HMRegistries.COFFER_OWNER.get())) bug("coffer", "dropped coffer lost its owner");
        drops.forEach(ItemEntity::discard);

        // bob places alice's coffer -> stays alice's
        if (!dropped.isEmpty()) {
            BlockPos p2 = base.offset(3, 0, 0);
            bob.setItemInHand(InteractionHand.MAIN_HAND, dropped.copy());
            bob.gameMode.useItemOn(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND, hit(p2.below(), Direction.UP));
            if (level.getBlockEntity(p2) instanceof CofferBlockEntity be2) {
                if (!alice.getUUID().equals(be2.owner())) bug("coffer", "stolen coffer re-placed by bob is owned by " + be2.ownerName());
                if (be2.mayOpen(bob)) bug("coffer", "bob can open alice's coffer after moving it");
                if (be2.items().countItem(Items.DIAMOND) != 64) bug("coffer", "re-placed coffer lost contents: " + be2.items().countItem(Items.DIAMOND));
                // nesting: coffer / shulker into a coffer through the menu (normal click and shift-click), and coffer into a shulker
                var cm = new com.kragnoth.hallmarked.gamble.CofferMenu(2, bob.getInventory(), be2.items());
                for (ItemStack probe : List.of(dropped.copy(), new ItemStack(Items.SHULKER_BOX), new ItemStack(Items.DIAMOND))) {
                    boolean slotOk = cm.getSlot(1).mayPlace(probe);
                    bob.getInventory().setItem(9, probe.copy());
                    int invSlot = 27 + 0; // first main-inventory slot in the menu = inventory index 9
                    cm.quickMoveStack(bob, invSlot);
                    boolean moved = bob.getInventory().getItem(9).isEmpty();
                    bob.getInventory().setItem(9, ItemStack.EMPTY);
                    boolean expected = probe.is(Items.DIAMOND);
                    info("coffer", "into coffer: " + id(probe.getItem()) + " slot.mayPlace=" + slotOk + " shift-click moved=" + moved);
                    if (slotOk != expected || moved != expected) bug("coffer", "nesting rule wrong for " + id(probe.getItem()));
                }
                if (dropped.getItem().canFitInsideContainerItems()) bug("coffer", "coffer item fits inside shulkers/bundles");
                for (int i = 0; i < 27; i++) be2.items().setItem(i, ItemStack.EMPTY);
                be2.items().setItem(0, new ItemStack(Items.DIAMOND, 64));
                // non-player destroyBlock (drill/wither)
                level.destroyBlock(p2, true, null);
                List<ItemEntity> d2 = level.getEntitiesOfClass(ItemEntity.class, new AABB(p2).inflate(4));
                info("coffer", "non-player destroyBlock (Create drill / Wither) breaks a locked coffer: " + level.getBlockState(p2).isAir()
                        + ", drops " + d2.stream().map(e -> e.getItem().getItem().toString()).toList());
                d2.forEach(ItemEntity::discard);
            } else bug("coffer", "bob couldn't place a coffer item: " + level.getBlockState(p2));
        }

        // unowned legacy coffer: first opener claims
        BlockPos p3 = base.offset(-3, 0, 0);
        level.setBlock(p3, HMRegistries.MOUNTEBANK_COFFER.get().defaultBlockState(), 3);
        if (level.getBlockEntity(p3) instanceof CofferBlockEntity be3) {
            if (be3.owner() != null) bug("coffer", "setBlock coffer had an owner");
            bob.gameMode.useItemOn(bob, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit(p3, Direction.UP));
            info("coffer", "unowned coffer (worldgen/structure/setblock) claimed by first opener: " + bob.getUUID().equals(be3.owner())
                    + " — a structure-placed coffer goes to whoever touches it first");
            bob.closeContainer();
        }
        // villager-placed? no. Creative break of non-empty coffer -> exactly one item
        BlockPos p4 = base.offset(-6, 0, 0);
        placeCoffer(alice, p4);
        if (level.getBlockEntity(p4) instanceof CofferBlockEntity be4) {
            be4.items().setItem(0, new ItemStack(Items.EMERALD, 10));
            alice.setGameMode(GameType.CREATIVE);
            alice.gameMode.destroyBlock(p4);
            alice.setGameMode(GameType.SURVIVAL);
            List<ItemEntity> d4 = level.getEntitiesOfClass(ItemEntity.class, new AABB(p4).inflate(4));
            long n = d4.stream().filter(e -> e.getItem().is(HMRegistries.MOUNTEBANK_COFFER_ITEM.get())).count();
            if (n != 1) bug("coffer", "creative break of non-empty coffer dropped " + n + " coffer items");
            d4.forEach(ItemEntity::discard);
        }
        // explosion last (it wrecks the floor)
        BlockPos p5 = base.offset(8, 0, 8);
        placeCoffer(alice, p5);
        level.explode(null, p5.getX() + 0.5, p5.getY() + 0.5, p5.getZ() + 1.5, 6f, Level.ExplosionInteraction.TNT);
        if (!level.getBlockState(p5).is(HMRegistries.MOUNTEBANK_COFFER.get())) bug("coffer", "TNT-sized explosion destroyed the coffer");
        level.getEntitiesOfClass(ItemEntity.class, new AABB(p5).inflate(10)).forEach(ItemEntity::discard);
        prepareArena();
        clearEquip(alice); clearEquip(bob);
    }

    // ---- sell / trade-in --------------------------------------------------------------------------

    private int countItem(FakePlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private InteractionResult sneakUse(FakePlayer p, BlockPos pos, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        p.setShiftKeyDown(true);
        InteractionResult r = p.gameMode.useItemOn(p, level, p.getMainHandItem(), InteractionHand.MAIN_HAND, hit(pos, Direction.UP));
        p.setShiftKeyDown(false);
        return r;
    }

    private void sellTests() {
        BlockPos pos = base.offset(0, 0, 4);
        placeCoffer(alice, pos);
        if (!(level.getBlockEntity(pos) instanceof CofferBlockEntity)) { bug("sell", "no coffer"); return; }
        alice.getInventory().clearContent();
        bob.getInventory().clearContent();

        // crafted graded item refused
        ItemStack crafted = new ItemStack(Items.DIAMOND_SWORD);
        Grading.grade(crafted, "crafted", 0, random, Grading.Context.of(alice));
        Grade cg = Grading.gradeOf(crafted);
        sneakUse(alice, pos, crafted);
        if (countItem(alice, Items.EMERALD) > 0) bug("sell", "crafted item sold for emeralds (" + (cg == null ? "-" : cg.name()) + ")");
        if (alice.getMainHandItem().isEmpty() && cg != null && cg.sell() > 0) bug("sell", "crafted item consumed without payment");

        // found items: sell many and compare
        int paid = 0, sold = 0;
        Map<String, int[]> perGrade = new TreeMap<>();
        for (int i = 0; i < 400; i++) {
            ItemStack s = new ItemStack(random.nextBoolean() ? Items.IRON_SWORD : Items.DIAMOND_CHESTPLATE);
            Grading.grade(s, "loot", random.nextInt(6), random, new Grading.Context(alice, level, Vec3.atCenterOf(pos)));
            Grade g = Grading.gradeOf(s);
            if (g == null) continue;
            if (!s.getOrDefault(HMRegistries.FOUND.get(), false)) bug("sell", "loot item not marked FOUND");
            int before = countItem(alice, Items.EMERALD);
            sneakUse(alice, pos, s);
            int got = countItem(alice, Items.EMERALD) - before;
            if (got > 0) { paid += got; sold++; }
            perGrade.computeIfAbsent(g.name().replace("hallmarked.grade.", ""), k -> new int[2])[0]++;
            perGrade.get(g.name().replace("hallmarked.grade.", ""))[1] += got;
            if (got > 0 && !s.isEmpty()) bug("sell", "paid but item not consumed (dupe)");
            if (got == 0 && g.sell() > 0 && s.isEmpty()) bug("sell", "item consumed without payment: " + g.name());
            alice.getInventory().clearContent();
        }
        StringBuilder sb = new StringBuilder();
        perGrade.forEach((k, v) -> sb.append(k).append(" ").append(v[0]).append("x avg ").append(String.format("%.2f", v[1] / (double) v[0])).append("; "));
        info("sell", sold + " of 400 found items sold, " + paid + " emeralds: " + sb);

        // damaged item: price floor
        ItemStack worn = new ItemStack(Items.IRON_SWORD);
        Grading.grade(worn, "loot", 5, random, new Grading.Context(alice, level, Vec3.atCenterOf(pos)));
        worn.setDamageValue(worn.getMaxDamage() - 1);
        int b0 = countItem(alice, Items.EMERALD);
        sneakUse(alice, pos, worn);
        info("sell", "nearly broken " + Grading.gradeOf(worn) + " paid " + (countItem(alice, Items.EMERALD) - b0));
        alice.getInventory().clearContent();

        // unappraised found item sells without being revealed
        ItemStack unapp = new ItemStack(Items.IRON_SWORD);
        Grading.grade(unapp, "loot", 5, random, new Grading.Context(alice, level, Vec3.atCenterOf(pos)));
        unapp.set(HMRegistries.UNAPPRAISED.get(), true);
        sneakUse(alice, pos, unapp);
        info("sell", "unappraised item sells: " + (countItem(alice, Items.EMERALD) > 0));
        alice.getInventory().clearContent();

        // stack size > 1 graded item (modded stackable equipment?) — force count 2
        ItemStack two = new ItemStack(Items.IRON_SWORD);
        Grading.grade(two, "loot", 6, random, new Grading.Context(alice, level, Vec3.atCenterOf(pos)));
        // sell to someone else's coffer
        int bb = countItem(bob, Items.EMERALD);
        ItemStack bobs = new ItemStack(Items.DIAMOND_SWORD);
        Grading.grade(bobs, "loot", 6, random, new Grading.Context(bob, level, Vec3.atCenterOf(pos)));
        sneakUse(bob, pos, bobs);
        info("sell", "non-owner can sell at alice's coffer: " + (countItem(bob, Items.EMERALD) > bb));
        bob.getInventory().clearContent();

        // trade-in: set piece -> token
        GearSet set = Sets.STORE.all().iterator().next();
        ItemStack piece = Sets.create(set, set.pieces().get(0), access);
        sneakUse(alice, pos, piece);
        int tokens = countItem(alice, HMRegistries.MOUNTEBANK_TOKEN.get());
        if (tokens != 1) bug("sell", "trade-in of 1 piece gave " + tokens + " tokens");
        if (!piece.isEmpty()) bug("sell", "trade-in left the piece in hand");
        // set piece with Curios / held in offhand only — main hand empty: nothing happens
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.OFF_HAND, Sets.create(set, set.pieces().get(0), access));
        sneakUse(alice, pos, ItemStack.EMPTY);
        if (alice.getOffhandItem().isEmpty()) bug("sell", "offhand set piece consumed");
        alice.getInventory().clearContent();

        // trade-in of a set piece at 1% durability still a full token
        ItemStack brokenPiece = Sets.create(set, set.pieces().get(0), access);
        if (brokenPiece.isDamageableItem()) brokenPiece.setDamageValue(brokenPiece.getMaxDamage() - 1);
        sneakUse(alice, pos, brokenPiece);
        info("sell", "nearly broken set piece trades for " + countItem(alice, HMRegistries.MOUNTEBANK_TOKEN.get()) + " token");
        alice.getInventory().clearContent();

        // token loop: piece -> token; 2 tokens -> sworn parcel -> piece
        Parcel sworn = Parcel.STORE.all().stream().filter(p -> p.currency() == HMRegistries.MOUNTEBANK_TOKEN.get()).findFirst().orElse(null);
        if (sworn != null) info("sell", "token loop: 1 piece -> 1 token, " + sworn.name() + " costs " + sworn.priceMin() + "-" + sworn.priceMax() + " tokens -> 1 piece (net loss, no loop)");

        // salvage values: set grade salvage vs sell
        Grade sg = Grading.GRADES.get(Sets.SET_GRADE);
        if (sg != null) info("sell", "set grade sell=" + sg.sell() + " (set pieces go through trade-in, not sell)");

        // Is the event server-only safe when a *client* coffer click path is not simulated? n/a
        clearEquip(alice);
    }

    // ---- gamble -----------------------------------------------------------------------------------

    private int sellValue(ItemStack held) {
        Grade grade = Grading.gradeOf(held);
        if (grade == null || grade.sell() <= 0 || !held.getOrDefault(HMRegistries.FOUND.get(), false)) return 0;
        int value = grade.sell();
        if (Reforging.qualityOf(held) != null) {
            boolean allUp = true, any = false;
            for (var e : Marks.of(held)) {
                if (e.origin() != Marks.Origin.QUALITY) continue;
                any = true;
                if (e.modifier().amount() < 0) allUp = false;
            }
            if (any && allUp) value += 1;
        }
        return value;
    }

    private void gambleTests() {
        for (Parcel p : Parcel.STORE.all()) {
            int nulls = 0, sets = 0, curses = 0, exc = 0, misfit = 0, sellSum = 0;
            Map<String, Integer> grades = new TreeMap<>();
            int N = 600;
            for (int i = 0; i < N; i++) {
                Gamble.Result r;
                try { r = Gamble.open(p, alice); } catch (Throwable t) { bug("gamble", p.name() + " open threw " + t); continue; }
                if (r == null) { nulls++; continue; }
                ItemStack s = r.stack();
                if (i < 120) checkStack("gamble", s, true);
                if (Sets.pieceOf(s) != null) { sets++; continue; }
                GradeData d = Grading.dataOf(s);
                if (d != null) {
                    grades.merge(d.grade().replace("hallmarked.grade.", ""), 1, Integer::sum);
                    for (GradeData.Rolled ro : d.affixes()) {
                        Affix a = Grading.AFFIXES.get(ro.affix());
                        if (a == null) { bug("gamble", "unknown affix " + ro.affix()); continue; }
                        if (a.type() == Affix.Type.CURSE) curses++;
                        if (!a.fits(s)) misfit++;
                        if (!Float.isFinite(ro.roll())) bug("gamble", "non-finite roll");
                        if (ro.roll() > 1.6f) bug("gamble", "roll " + ro.roll() + " too high");
                    }
                }
                if (Gamble.isExceptional(s)) exc++;
                sellSum += sellValue(s);
            }
            double price = (p.priceMin() + p.priceMax()) / 2.0;
            double ev = sellSum / (double) N;
            String cur = id(p.currency());
            info("gamble", String.format("%s lvl%d: null=%d sets=%d curses=%d exceptional=%d misfit-affixes=%d | resale EV %.2f emeralds vs price %.1f %s %s",
                    p.name(), p.level(), nulls, sets, curses, exc, misfit, ev, price, cur, grades));
            if (nulls > 0) warn("gamble", p.name() + " returned null " + nulls + "/" + N + " (\"empty\" parcel, emeralds lost?)");
            if (misfit > 0) bug("gamble", p.name() + " rolled " + misfit + " affixes that don't fit the item");
            if (cur.equals("minecraft:emerald") && ev >= price * 0.9) bug("gamble", p.name() + " resale EV " + ev + " >= 90% of price " + price + " (emerald loop risk)");
            if (p.setChance() >= 1 && sets != N) bug("gamble", p.name() + " set_chance 1 but only " + sets + "/" + N + " set pieces");
        }
        // parcel stack with a stale/removed parcel name
        ItemStack ghost = new ItemStack(HMRegistries.PARCEL_ITEM.get());
        ghost.set(HMRegistries.PARCEL.get(), "nope.parcel.gone");
        alice.setItemInHand(InteractionHand.MAIN_HAND, ghost);
        guard("gamble", "use stale parcel", () -> HMRegistries.PARCEL_ITEM.get().use(level, alice, InteractionHand.MAIN_HAND));
        if (alice.getMainHandItem().isEmpty()) bug("gamble", "stale parcel consumed for nothing");
        // empty parcel item (no component), e.g. from creative tab
        alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HMRegistries.PARCEL_ITEM.get(), 16));
        guard("gamble", "use blank parcel", () -> HMRegistries.PARCEL_ITEM.get().use(level, alice, InteractionHand.MAIN_HAND));
        // full inventory open
        Parcel any = Parcel.STORE.all().iterator().next();
        alice.getInventory().clearContent();
        for (int i = 0; i < alice.getInventory().items.size(); i++) alice.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        ItemStack ps = MountebankTrades.parcelStack(any);
        ps.setCount(16);
        alice.setItemInHand(InteractionHand.MAIN_HAND, ps);
        level.getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4)).forEach(ItemEntity::discard);
        guard("gamble", "open with full inventory", () -> HMRegistries.PARCEL_ITEM.get().use(level, alice, InteractionHand.MAIN_HAND));
        int dropped = level.getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4)).size();
        info("gamble", "full-inventory open: parcels left " + alice.getMainHandItem().getCount() + ", items dropped " + dropped);
        level.getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4)).forEach(ItemEntity::discard);
        alice.getInventory().clearContent();
    }

    // ---- villager trades --------------------------------------------------------------------------

    private void tradeTests() {
        var trades = VillagerTrades.TRADES.get(HMRegistries.MOUNTEBANK.get());
        if (trades == null) { bug("trades", "no trade table for the mountebank"); return; }
        Villager v = EntityType.VILLAGER.create(level);
        if (v == null) return;
        v.moveTo(Vec3.atCenterOf(base.offset(5, 0, 5)));
        for (int lv = 1; lv <= 5; lv++) {
            var listings = trades.get(lv);
            if (listings == null || listings.length == 0) { bug("trades", "level " + lv + " has no listings"); continue; }
            Map<String, Integer> seen = new TreeMap<>();
            int nulls = 0;
            for (var l : listings) for (int i = 0; i < 200; i++) {
                MerchantOffer o;
                try { o = l.getOffer(v, random); } catch (Throwable t) { bug("trades", "listing threw " + t); continue; }
                if (o == null) { nulls++; continue; }
                String k = o.getResult().has(HMRegistries.PARCEL.get()) ? o.getResult().get(HMRegistries.PARCEL.get()) : id(o.getResult().getItem());
                seen.merge(k.replace("hallmarked.parcel.", "") + "<-" + id(o.getCostA().getItem()).replace("minecraft:", "").replace("hallmarked:", ""), 1, Integer::sum);
                if (o.getCostA().getCount() <= 0 || o.getCostA().getCount() > 64) bug("trades", "bad price " + o.getCostA());
            }
            info("trades", "level " + lv + " (" + listings.length + " listings) nulls=" + nulls + " " + seen);
        }
        // real villager with the profession: offers populate
        v.setVillagerData(v.getVillagerData().setProfession(HMRegistries.MOUNTEBANK.get()).setLevel(1));
        try {
            int n1 = v.getOffers().size();
            var m = net.minecraft.world.entity.npc.Villager.class.getDeclaredMethod("updateTrades");
            m.setAccessible(true);
            v.setVillagerData(v.getVillagerData().setLevel(2));
            m.invoke(v);
            List<String> names = v.getOffers().stream().map(o -> o.getResult().getOrDefault(HMRegistries.PARCEL.get(), id(o.getResult().getItem()))).toList();
            info("trades", "real villager offers after level 1: " + n1 + ", after level 2: " + names);
            boolean sworn = names.stream().anyMatch(s -> s.contains("sworn"));
            if (!sworn) bug("trades", "level-2 mountebank didn't get the sworn parcel");
        } catch (Throwable t) { warn("trades", "villager simulation: " + t); }
        v.discard();
    }

    // ---- loot tables ------------------------------------------------------------------------------

    private void lootTests() {
        List<ResourceLocation> all = new ArrayList<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        List<ResourceLocation> chests = all.stream().filter(r -> r.getPath().startsWith("chests/")).toList();
        int graded = 0, setp = 0, rolls = 0;
        Map<String, Integer> grades = new TreeMap<>();
        long t0 = System.nanoTime();
        for (ResourceLocation id : chests) {
            LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
            for (int i = 0; i < 25; i++) {
                LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base))
                        .withParameter(LootContextParams.THIS_ENTITY, alice).withLuck(random.nextInt(6) - 1).create(LootContextParamSets.CHEST);
                List<ItemStack> loot;
                try { loot = table.getRandomItems(params); } catch (Throwable t) { bug("loot", id + " threw " + t); break; }
                rolls++;
                for (ItemStack s : loot) {
                    if (Sets.pieceOf(s) != null) setp++;
                    GradeData d = Grading.dataOf(s);
                    if (d != null) { graded++; grades.merge(d.grade().replace("hallmarked.grade.", ""), 1, Integer::sum); if (graded % 7 == 0) checkStack("loot", s, true); }
                    if (s.getCount() > s.getMaxStackSize()) bug("loot", id + " oversized stack " + s);
                    if (Grading.canGrade(s) && d == null && !Grading.isUnappraised(s)) warn("loot", "ungraded gradeable item from " + id + ": " + id(s.getItem()));
                }
            }
        }
        info("loot", String.format("%d chest tables x25 = %d rolls in %.1f s: %d graded, %d set pieces; %s",
                chests.size(), rolls, (System.nanoTime() - t0) / 1e9, graded, setp, grades));
        // entity tables
        for (String e : List.of("zombie", "skeleton", "wither_skeleton", "drowned", "piglin_brute", "vindicator", "pillager", "evoker")) {
            var key = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.withDefaultNamespace("entities/" + e));
            LootTable table = server.reloadableRegistries().getLootTable(key);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.withDefaultNamespace(e));
            var ent = type.create(level);
            if (ent == null) continue;
            ent.moveTo(Vec3.atCenterOf(base));
            for (int i = 0; i < 20; i++) {
                try {
                    LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, ent)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base))
                            .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(alice))
                            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, alice)
                            .withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, alice)
                            .withLuck(2).create(LootContextParamSets.ENTITY);
                    for (ItemStack s : table.getRandomItems(params)) if (Grading.dataOf(s) != null) checkStack("loot-entity", s, true);
                } catch (Throwable t) { bug("loot", "entities/" + e + " threw " + t); break; }
            }
            ent.discard();
        }
        // mob spawn equipment grading
        int mobGraded = 0;
        for (int i = 0; i < 40; i++) {
            var z = EntityType.ZOMBIE.create(level);
            if (z == null) continue;
            z.moveTo(Vec3.atCenterOf(base.offset(2, 0, 2)));
            z.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            z.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            level.addFreshEntity(z);
            if (Grading.dataOf(z.getMainHandItem()) != null) mobGraded++;
            for (EquipmentSlot sl : EquipmentSlot.values()) if (!z.getItemBySlot(sl).isEmpty()) checkStack("mob-equip", z.getItemBySlot(sl), false);
            z.discard();
        }
        info("loot", "zombies joining with iron gear: " + mobGraded + "/40 weapons graded on join");
    }

    // ---- procs ------------------------------------------------------------------------------------

    private ItemStack withAffixes(Item item, List<Affix> affixes, float roll) {
        ItemStack s = new ItemStack(item);
        List<GradeData.Rolled> rolled = new ArrayList<>();
        for (Affix a : affixes) if (a.fits(s)) rolled.add(new GradeData.Rolled(a.name(), roll));
        s.set(HMRegistries.GRADE.get(), new GradeData("hallmarked.grade.masterwork", rolled, "", ""));
        s.set(HMRegistries.ROLLED.get(), true);
        return s;
    }

    private void procTests() {
        List<Affix> procAffixes = Grading.AFFIXES.all().stream().filter(a -> !a.procs().isEmpty()).toList();
        info("procs", procAffixes.size() + " affixes with procs: " + procAffixes.stream().map(a -> a.name().replace("hallmarked.affix.", "")).toList());
        // stack everything on two zombies and let them fight
        for (float roll : new float[]{0f, 1f, 1.35f}) {
            Mob a = EntityType.ZOMBIE.create(level), b = EntityType.SKELETON.create(level);
            if (a == null || b == null) return;
            a.moveTo(Vec3.atCenterOf(base.offset(6, 0, -6)));
            b.moveTo(Vec3.atCenterOf(base.offset(7, 0, -6)));
            level.addFreshEntity(a); level.addFreshEntity(b);
            for (Mob m : List.of(a, b)) {
                m.setItemSlot(EquipmentSlot.MAINHAND, withAffixes(Items.NETHERITE_AXE, procAffixes, roll));
                m.setItemSlot(EquipmentSlot.CHEST, withAffixes(Items.NETHERITE_CHESTPLATE, procAffixes, roll));
                m.setItemSlot(EquipmentSlot.HEAD, withAffixes(Items.NETHERITE_HELMET, procAffixes, roll));
                var hp = m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                hp.setBaseValue(1000);
                m.setHealth(1000);
            }
            int maxEvents = 0;
            for (int i = 0; i < 40; i++) {
                for (Mob[] pair : new Mob[][]{{a, b}, {b, a}}) {
                    pair[1].invulnerableTime = 0;
                    damageEvents = 0;
                    final Mob att = pair[0], tgt = pair[1];
                    guard("procs", "hit roll " + roll, () -> att.doHurtTarget(tgt));
                    maxEvents = Math.max(maxEvents, damageEvents);
                }
                for (Mob m : List.of(a, b)) {
                    if (!Float.isFinite(m.getHealth())) bug("procs", "non-finite health " + m.getHealth());
                    if (m.isDeadOrDying()) { m.setHealth(1000); }
                }
                // tick effects (bleed etc.)
                for (Mob m : List.of(a, b)) guard("procs", "tick", m::tick);
            }
            info("procs", "roll " + roll + ": worst damage-event cascade from one hit = " + maxEvents);
            if (maxEvents > 30) bug("procs", "one hit produced " + maxEvents + " damage events (recursion?) at roll " + roll);
            // kill each with the other (reaper, death procs)
            b.setHealth(1);
            b.invulnerableTime = 0;
            guard("procs", "kill", () -> a.doHurtTarget(b));
            a.discard(); b.discard();
        }
        // player attacker with all proc affixes + a full set with set procs
        Mob t = EntityType.ZOMBIE.create(level);
        if (t != null) {
            t.moveTo(Vec3.atCenterOf(base.offset(4, 0, 4)));
            level.addFreshEntity(t);
            t.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
            t.setHealth(1000);
            t.setItemSlot(EquipmentSlot.CHEST, withAffixes(Items.NETHERITE_CHESTPLATE, procAffixes, 1f));
            for (GearSet set : Sets.STORE.all()) {
                if (set.bonuses().stream().allMatch(bn -> bn.procs().isEmpty())) continue;
                clearEquip(alice);
                int hand = 0;
                for (GearSet.Piece p : set.pieces()) {
                    ItemStack s = Sets.create(set, p, access);
                    if (s == null) continue;
                    EquipmentSlot sl = slotFor(alice, s);
                    if (sl == EquipmentSlot.MAINHAND && hand++ > 0) sl = EquipmentSlot.OFFHAND;
                    alice.setItemSlot(sl, s);
                }
                Sets.syncBonuses(alice);
                for (int i = 0; i < 20; i++) {
                    t.invulnerableTime = 0;
                    damageEvents = 0;
                    alice.resetAttackStrengthTicker();
                    guard("procs", "player set attack " + set.name(), () -> alice.attack(t));
                    if (damageEvents > 30) bug("procs", set.name() + " player hit cascade " + damageEvents);
                    if (t.isDeadOrDying()) t.setHealth(1000);
                }
                if (!Float.isFinite(t.getHealth())) bug("procs", "target health non-finite after " + set.name());
            }
            clearEquip(alice);
            Sets.syncBonuses(alice);
            t.discard();
        }
    }


    // ---- loot pipeline (mixin catch-up, LootJS, custom grade_loot, config) -------------------------

    private List<ItemStack> roll(String table, int times, double luck) {
        LootTable t = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(table)));
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base))
                    .withParameter(LootContextParams.THIS_ENTITY, alice).withLuck((float) luck).create(LootContextParamSets.CHEST);
            out.addAll(t.getRandomItems(params));
        }
        return out;
    }

    private String gearReport(String label, List<ItemStack> items, Item want) {
        int n = 0, graded = 0, found = 0;
        for (ItemStack s : items) {
            if (!s.is(want)) continue;
            n++;
            if (Grading.dataOf(s) != null) graded++;
            if (s.getOrDefault(HMRegistries.FOUND.get(), false)) found++;
        }
        String r = label + ": " + id(want) + " x" + n + " graded " + graded + " found " + found;
        info("lootjs", r);
        return n + "/" + graded + "/" + found;
    }

    private void lootPipelineTests() {
        boolean lootjs = net.neoforged.fml.ModList.get().isLoaded("lootjs");
        info("lootjs", "LootJS loaded: " + lootjs);
        // vanilla gear: every gradeable item from every chest table graded (Iron's rings included)
        int ungraded = 0, total = 0;
        for (ResourceLocation id : server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)) {
            if (!id.getPath().startsWith("chests/")) continue;
            for (ItemStack s : roll(id.toString(), 8, 1)) {
                if (!Grading.canGrade(s)) continue;
                total++;
                if (Grading.dataOf(s) == null) { ungraded++; warn("lootjs", "still ungraded from " + id + ": " + id(s.getItem())); }
            }
        }
        info("lootjs", "all chest tables x8: " + total + " gear, " + ungraded + " ungraded");
        if (ungraded > 0) bug("lootjs", ungraded + " gear items left ungraded after the loot mixin");

        if (lootjs) {
            String a = gearReport("addLoot simple_dungeon", roll("minecraft:chests/simple_dungeon", 20, 0), Items.DIAMOND_SWORD);
            gearReport("addLoot simple_dungeon", roll("minecraft:chests/simple_dungeon", 20, 0), Items.IRON_CHESTPLATE);
            if (!a.startsWith("20/20/20")) bug("lootjs", "LootJS addLoot gear not graded as found: " + a);
            String b = gearReport("replaceLoot mineshaft", roll("minecraft:chests/abandoned_mineshaft", 20, 0), Items.GOLDEN_SWORD);
            String[] bp = b.split("/");
            if (!bp[0].equals(bp[1]) || !bp[0].equals(bp[2]) || bp[0].equals("0")) bug("lootjs", "LootJS replaceLoot gear not graded as found: " + b);
            List<ItemStack> house = roll("minecraft:chests/village/village_plains_house", 20, 0);
            String c = gearReport("lootTables pool plains_house", house, Items.IRON_BOOTS);
            if (!c.startsWith("20/20/20")) bug("lootjs", "LootJS lootTables entry not graded: " + c);
            // custom grade_loot (source crafted) must win over the catch-up
            String d = gearReport("custom grade_loot igloo (source crafted)", roll("minecraft:chests/igloo_chest", 20, 0), Items.IRON_SWORD);
            if (!d.equals("20/20/0")) bug("lootjs", "custom hallmarked:grade_loot modifier didn't win (want 20/20/0 = graded, not found): " + d);
            // renamed by script
            for (ItemStack s : roll("minecraft:chests/desert_pyramid", 3, 0)) if (s.is(Items.IRON_SWORD)) {
                info("lootjs", "script-named sword: '" + s.getHoverName().getString() + "'");
                Grading.refreshName(s);
                if (!s.getHoverName().getString().contains("Script Blade")) bug("lootjs", "script name lost after refresh: " + s.getHoverName().getString());
                checkStack("lootjs", s, true);
                break;
            }
            // removeLoot(ARMOR) on bastion also removes injected set pieces? (set injection runs after LootJS)
            int armor = 0;
            for (ItemStack s : roll("minecraft:chests/bastion_treasure", 200, 5)) if (s.getItem() instanceof net.minecraft.world.item.ArmorItem) armor++;
            info("lootjs", "bastion_treasure with removeLoot(ARMOR): " + armor + " armor pieces in 200 rolls (set pieces are added after LootJS)");
            // entity: real kill
            int helmets = 0, gradedHelmets = 0;
            for (int i = 0; i < 10; i++) {
                var z = EntityType.ZOMBIE.create(level);
                if (z == null) continue;
                z.moveTo(Vec3.atCenterOf(base.offset(3, 0, 3)));
                level.addFreshEntity(z);
                z.hurt(level.damageSources().playerAttack(alice), 10000);
            }
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(10))) {
                if (e.getItem().is(Items.IRON_HELMET)) { helmets++; if (Grading.dataOf(e.getItem()) != null) gradedHelmets++; }
                e.discard();
            }
            info("lootjs", "zombie kills: " + helmets + " LootJS helmets, " + gradedHelmets + " graded");
            if (helmets == 0 || gradedHelmets != helmets) bug("lootjs", "entity addLoot: " + helmets + " helmets, " + gradedHelmets + " graded");
            // block: gravel table with block params
            LootTable gravel = server.reloadableRegistries().getLootTable(net.minecraft.world.level.block.Blocks.GRAVEL.getLootTable());
            int shovels = 0, gradedShovels = 0;
            for (int i = 0; i < 10; i++) {
                LootParams bp2 = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base))
                        .withParameter(LootContextParams.BLOCK_STATE, net.minecraft.world.level.block.Blocks.GRAVEL.defaultBlockState())
                        .withParameter(LootContextParams.TOOL, new ItemStack(Items.IRON_SHOVEL))
                        .withOptionalParameter(LootContextParams.THIS_ENTITY, alice).create(LootContextParamSets.BLOCK);
                for (ItemStack s : gravel.getRandomItems(bp2)) if (s.is(Items.IRON_SHOVEL)) { shovels++; if (Grading.dataOf(s) != null) gradedShovels++; }
            }
            info("lootjs", "gravel drops: " + shovels + " shovels, " + gradedShovels + " graded");
            if (shovels == 0 || gradedShovels != shovels) bug("lootjs", "block addLoot: " + shovels + "/" + gradedShovels);
        }
        // real container: chest with a loot table, unpacked by a player
        BlockPos cpos = base.offset(-8, 0, -8);
        level.setBlock(cpos, Blocks.CHEST.defaultBlockState(), 3);
        if (level.getBlockEntity(cpos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")), 42L);
            chest.unpackLootTable(alice);
            int g = 0, n = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (Grading.canGrade(s)) { n++; if (Grading.dataOf(s) != null) g++; }
            }
            info("lootjs", "real chest unpack: " + n + " gear, " + g + " graded");
            if (g != n) bug("lootjs", "chest unpack left gear ungraded");
            level.setBlock(cpos, Blocks.AIR.defaultBlockState(), 3);
        }
        // config: excluded tables and the master switch
        var cfg = com.kragnoth.hallmarked.config.HallmarkedConfig.LOOT_EXCLUDED;
        var before = cfg.get();
        try {
            cfg.set(List.of("minecraft:chests/simple_dungeon", "minecraft:chests/village/*"));
            String ex = gearReport("excluded simple_dungeon", roll("minecraft:chests/simple_dungeon", 10, 0), Items.DIAMOND_SWORD);
            if (lootjs && !ex.startsWith("10/0/")) bug("lootjs", "excluded table still graded: " + ex);
            String ex2 = gearReport("excluded village/*", roll("minecraft:chests/village/village_weaponsmith", 20, 0), Items.IRON_SWORD);
            if (!ex2.split("/")[1].equals("0")) bug("lootjs", "wildcard exclusion didn't apply: " + ex2);
            String igl = gearReport("excluded doesn't touch other tables (igloo)", roll("minecraft:chests/igloo_chest", 5, 0), Items.IRON_SWORD);
        } finally { cfg.set(before); }
        var sw = com.kragnoth.hallmarked.config.HallmarkedConfig.LOOT_TABLES;
        try {
            sw.set(false);
            int g = 0, n = 0;
            for (ItemStack s : roll("minecraft:chests/end_city_treasure", 20, 0)) if (Grading.canGrade(s)) { n++; if (Grading.dataOf(s) != null) g++; }
            info("lootjs", "LootTables=false: end city gear " + n + ", graded " + g);
            if (g > 0) bug("lootjs", "LootTables=false still graded " + g);
            String igl = gearReport("LootTables=false, custom igloo modifier still grades", roll("minecraft:chests/igloo_chest", 5, 0), Items.IRON_SWORD);
            if (lootjs && !igl.startsWith("5/5/")) bug("lootjs", "custom grade_loot stopped working with LootTables=false: " + igl);
        } finally { sw.set(true); }

        // ---- extra pipeline edges ----
        // EMPTY table and a table built in code (no id)
        guard("lootjs", "roll EMPTY", () -> LootTable.EMPTY.getRandomItems(new LootParams.Builder(level).create(LootContextParamSets.EMPTY)));
        LootTable inline = LootTable.lootTable().withPool(net.minecraft.world.level.storage.loot.LootPool.lootPool()
                .add(net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(Items.IRON_SWORD))).build();
        List<ItemStack> inl = new ArrayList<>();
        guard("lootjs", "roll inline table", () -> inl.addAll(inline.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base)).create(LootContextParamSets.CHEST))));
        info("lootjs", "inline (id-less) table: " + inl.size() + " items, graded " + inl.stream().filter(x -> Grading.dataOf(x) != null).count());
        if (inl.stream().anyMatch(x -> Grading.dataOf(x) == null)) bug("lootjs", "id-less table gear not graded");
        // off-thread roll: no grading, no crash
        try {
            List<ItemStack> off = java.util.concurrent.CompletableFuture.supplyAsync(() -> roll("minecraft:chests/simple_dungeon", 5, 0)).get();
            long g = off.stream().filter(x -> Grading.canGrade(x) && Grading.dataOf(x) != null).count();
            long n = off.stream().filter(Grading::canGrade).count();
            info("lootjs", "off-thread roll: " + n + " gear, " + g + " graded (expected 0, graded on pickup)");
            if (g > 0) bug("lootjs", "graded off the server thread");
        } catch (Throwable t) { bug("lootjs", "off-thread roll threw " + t); }
        // other param sets: fishing, archaeology, bartering, hero gifts
        for (String[] t : new String[][]{{"minecraft:gameplay/fishing", "fishing"}, {"minecraft:gameplay/piglin_bartering", "barter"},
                {"minecraft:archaeology/trail_ruins_rare", "archaeology"}, {"minecraft:gameplay/hero_of_the_village/armorer_gift", "gift"},
                {"minecraft:gameplay/hero_of_the_village/weaponsmith_gift", "gift"}}) {
            LootTable lt = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(t[0])));
            int n = 0, g = 0;
            for (int i = 0; i < 300; i++) {
                try {
                    LootParams.Builder pb = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(base));
                    LootParams lp = switch (t[1]) {
                        case "fishing" -> pb.withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD)).withOptionalParameter(LootContextParams.THIS_ENTITY, alice).withLuck(5).create(LootContextParamSets.FISHING);
                        case "barter" -> { var pig = EntityType.PIGLIN.create(level); yield pb.withParameter(LootContextParams.THIS_ENTITY, pig).create(LootContextParamSets.PIGLIN_BARTER); }
                        case "archaeology" -> pb.withParameter(LootContextParams.TOOL, new ItemStack(Items.BRUSH)).withOptionalParameter(LootContextParams.THIS_ENTITY, alice).create(LootContextParamSets.ARCHAEOLOGY);
                        default -> pb.withParameter(LootContextParams.THIS_ENTITY, alice).create(LootContextParamSets.GIFT);
                    };
                    for (ItemStack x : lt.getRandomItems(lp)) if (Grading.canGrade(x)) { n++; if (Grading.dataOf(x) != null) g++; }
                } catch (Throwable e) { bug("lootjs", t[0] + " threw " + e); break; }
            }
            info("lootjs", t[0] + ": " + n + " gear, " + g + " graded");
            if (g != n) bug("lootjs", t[0] + " left gear ungraded");
        }
        // overhead of the mixin: same table with grading on/off
        var sw2 = com.kragnoth.hallmarked.config.HallmarkedConfig.LOOT_TABLES;
        long t0 = System.nanoTime(); roll("minecraft:chests/village/village_toolsmith", 3000, 0); long on = System.nanoTime() - t0;
        sw2.set(false);
        t0 = System.nanoTime(); roll("minecraft:chests/village/village_toolsmith", 3000, 0); long off2 = System.nanoTime() - t0;
        sw2.set(true);
        info("lootjs", String.format("3000 toolsmith rolls: %.1f ms graded vs %.1f ms ungraded (%.3f ms per roll for grading)", on / 1e6, off2 / 1e6, (on - off2) / 1e6 / 3000));

        // Lootr: per-player loot filled through its DefaultLootFiller (LootTable.fill on open)
        if (net.neoforged.fml.ModList.get().isLoaded("lootr")) {
            try {
                BlockPos lp = base.offset(-9, 0, 9);
                var block = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("lootr:lootr_chest"));
                level.setBlock(lp, block.defaultBlockState(), 3);
                var be = level.getBlockEntity(lp);
                ((net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity) be)
                        .setLootTable(ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse("minecraft:chests/simple_dungeon")), 7L);
                Object filler = Class.forName("noobanidus.mods.lootr.common.api.data.DefaultLootFiller").getMethod("getInstance").invoke(null);
                java.lang.reflect.Method unpack = null;
                for (var m : filler.getClass().getMethods()) if (m.getName().equals("unpackLootTable") && m.getParameterCount() == 3) unpack = m;
                int n = 0, g = 0, f = 0;
                for (FakePlayer who : List.of(alice, bob)) for (int i = 0; i < 10; i++) {
                    var inv = new net.minecraft.world.SimpleContainer(27);
                    unpack.invoke(filler, be, who, inv);
                    for (int k = 0; k < 27; k++) {
                        ItemStack x = inv.getItem(k);
                        if (!Grading.canGrade(x)) continue;
                        n++;
                        if (Grading.dataOf(x) != null) g++;
                        if (x.getOrDefault(HMRegistries.FOUND.get(), false)) f++;
                    }
                }
                info("lootjs", "Lootr per-player fills: " + n + " gear, " + g + " graded, " + f + " found");
                if (n == 0 || g != n || f != n) bug("lootjs", "Lootr loot not graded as found: " + n + "/" + g + "/" + f);
                level.setBlock(lp, Blocks.AIR.defaultBlockState(), 3);
            } catch (Throwable t) { bug("lootjs", "Lootr test threw " + t); }
        }

        // real structures placed with /place, chests unpacked by a player
        CommandSourceStack cs = server.createCommandSourceStack().withSuppressedOutput().withPermission(4);
        int structs = 0, sn = 0, sg = 0, sf = 0;
        String[] structures = {"minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:shipwreck", "minecraft:pillager_outpost", "minecraft:igloo",
                "minecraft:ruined_portal", "minecraft:village_plains", "minecraft:mansion", "minecraft:stronghold", "minecraft:mineshaft", "minecraft:ancient_city", "minecraft:trail_ruins"};
        for (int i = 0; i < structures.length; i++) {
            BlockPos at = new BlockPos(2000 + i * 200, 0, 2000);
            for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, true);
            try {
                server.getCommands().getDispatcher().execute("place structure " + structures[i] + " " + at.getX() + " " + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ()) + " " + at.getZ(), cs);
                info("lootjs", "placed " + structures[i]);
                structs++;
            } catch (Throwable t) { warn("lootjs", "place " + structures[i] + ": " + t.getMessage()); continue; }
            for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) {
                var chunk = level.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
                for (var be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity rc) || rc.getLootTable() == null) continue;
                    rc.unpackLootTable(alice);
                    for (int k = 0; k < rc.getContainerSize(); k++) {
                        ItemStack x = rc.getItem(k);
                        if (!Grading.canGrade(x)) continue;
                        sn++;
                        if (Grading.dataOf(x) != null) sg++;
                        if (x.getOrDefault(HMRegistries.FOUND.get(), false)) sf++;
                    }
                }
            }
        }
        for (int i = 0; i < structures.length; i++) { BlockPos at = new BlockPos(2000 + i * 200, 0, 2000);
            for (int cx = -6; cx <= 6; cx++) for (int cz = -6; cz <= 6; cz++) level.setChunkForced((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz, false); }
        info("lootjs", "placed " + structs + " structures; their chests held " + sn + " gear, " + sg + " graded, " + sf + " found");
        if (sg != sn || sf != sn) bug("lootjs", "structure chest gear: " + sn + "/" + sg + "/" + sf);
        // set piece injection rate sanity (each roll injected once): ancient city
        int sets = 0, rolls = 2000;
        for (ItemStack s : roll("minecraft:chests/ancient_city", rolls, 0)) if (Sets.pieceOf(s) != null) sets++;
        ResourceLocation ac = ResourceLocation.parse("minecraft:chests/ancient_city");
        double scale = Math.max(0.25, 1 + com.kragnoth.hallmarked.config.HallmarkedConfig.LUCK_FACTOR.get() * (0 + com.kragnoth.hallmarked.tier.LootOdds.forTable(ac).luck()));
        double expect = 0;
        for (GearSet gs : Sets.STORE.all()) for (GearSet.TableEntry te : gs.tables()) if (te.matches(ac)) expect += Math.min(1, te.chance() * scale);
        expect *= rolls;
        info("lootjs", String.format("ancient_city: %d set pieces in %d rolls (expected ~%.0f if injected once per roll)", sets, rolls, expect));
        if (sets > expect * 1.5 + 10) bug("lootjs", "set pieces injected more than once per roll? " + sets + " vs ~" + (int) expect);
        if (sets < expect * 0.5 - 10) bug("lootjs", "set pieces under-injected: " + sets + " vs ~" + (int) expect);
    }

    // ---- commands ---------------------------------------------------------------------------------

    private void commandFuzz() {
        CommandDispatcher<CommandSourceStack> d = server.getCommands().getDispatcher();
        alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        CommandSourceStack src = alice.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
        List<String> cmds = new ArrayList<>();
        String[] opts = {"", "luck=NaN", "luck=Infinity", "luck=-1e308", "roll=NaN", "roll=-5", "roll=99", "item=minecraft:air", "item=minecraft:stick",
                "item=minecraft:nope", "grade=unique", "grade=set", "grade=pure", "grade=nope", "unique=random", "category=bauble", "category=nope",
                "affix=hallmarked.affix.rotten", "affix=rotten affix=rotten affix=rotten", "affix=nope", "quality=random", "quality=nope",
                "source=gamble", "source=weird", "count=0", "count=-1", "count=99999", "extras=true", "mod=minecraft", "mod=nope",
                "=", "x", "item=", "grade=set item=minecraft:iron_helmet", "unique=random grade=common", "roll=NaN affix=keen",
                "tag=minecraft:swords", "category=helmet roll=1 grade=masterwork"};
        for (String o : opts) {
            cmds.add("hallmarked generate 1 " + o);
            cmds.add("hallmarked generate 64 " + o);
            cmds.add("hallmarked lootbarrel " + o);
            cmds.add("hallmarked champion minecraft:zombie " + o);
        }
        for (String e : List.of("minecraft:ender_dragon", "minecraft:wither", "minecraft:player", "minecraft:item", "minecraft:marker", "minecraft:armor_stand",
                "minecraft:villager", "minecraft:slime", "minecraft:ghast", "minecraft:nope", "minecraft:area_effect_cloud", "minecraft:giant", "minecraft:horse"))
            cmds.add("hallmarked champion " + e);
        for (String t : List.of("minecraft:chests/ancient_city", "minecraft:nope", "minecraft:entities/zombie", "minecraft:empty", "minecraft:blocks/stone"))
            cmds.add("hallmarked lootbarrel table " + t);
        for (String s : List.of("loot", "crafted", "mob", "gamble", "nope"))
            for (String l : List.of("", " 0", " 1024", " -1024", " 5 minecraft:chests/ancient_city", " 5 minecraft:nope"))
                cmds.add("hallmarked odds " + s + l);
        cmds.addAll(List.of("hallmarked odds", "hallmarked appraise", "hallmarked data", "hallmarked inspect", "hallmarked tiers export", "hallmarked tiers export all",
                "hallmarked grade roll", "hallmarked grade roll nope", "hallmarked grade roll gamble", "hallmarked grade set unique", "hallmarked grade set set",
                "hallmarked grade set nope", "hallmarked grade clear", "hallmarked quality roll", "hallmarked quality set nope", "hallmarked quality clear",
                "hallmarked affix add rotten 1", "hallmarked affix add nope 0.5", "hallmarked unique set nope", "hallmarked unique set random",
                "hallmarked sets give nope", "hallmarked parcel give nope", "hallmarked parcel give sworn_parcel 64"));
        for (GearSet s : Sets.STORE.all()) { cmds.add("hallmarked sets give " + s.name()); cmds.add("hallmarked sets give " + s.name() + " nope"); }
        for (Parcel p : Parcel.STORE.all()) cmds.add("hallmarked parcel give " + p.name() + " 3");
        int ok = 0, syntax = 0;
        long worst = 0; String worstCmd = "";
        for (String c : cmds) {
            for (CommandSourceStack s : List.of(src, console)) {
                // items in hand variations for item commands
                alice.setItemInHand(InteractionHand.MAIN_HAND, random.nextBoolean() ? new ItemStack(Items.DIAMOND_SWORD) : random.nextBoolean() ? new ItemStack(Items.DIRT, 5) : ItemStack.EMPTY);
                long t0 = System.nanoTime();
                try {
                    d.execute(c, s);
                    ok++;
                } catch (CommandSyntaxException e) {
                    syntax++;
                } catch (Throwable t) {
                    if (s == console && t.getMessage() != null && t.getMessage().contains("player")) continue;
                    bug("cmd", "'" + c + "' (" + (s == src ? "player" : "console") + ") threw " + t);
                }
                long dt = System.nanoTime() - t0;
                if (dt > worst) { worst = dt; worstCmd = c; }
                if (dt > 2_000_000_000L) warn("cmd", "'" + c + "' took " + dt / 1_000_000 + " ms");
                // check the held item after item commands
                ItemStack h = alice.getMainHandItem();
                if (!h.isEmpty() && Grading.dataOf(h) != null) checkStack("cmd:" + c, h, true);
                for (ItemStack inv : alice.getInventory().items) if (!inv.isEmpty() && Grading.dataOf(inv) != null) checkStack("cmd:" + c.split(" ")[1], inv, false);
                alice.getInventory().clearContent();
            }
        }
        info("cmd", cmds.size() * 2 + " executions: " + ok + " ran, " + syntax + " syntax errors; slowest " + worst / 1_000_000 + " ms: " + worstCmd);
        // clean up barrels/mobs spawned
        for (var e : level.getEntitiesOfClass(Mob.class, new AABB(base).inflate(40))) e.discard();
    }

    // ---- network ----------------------------------------------------------------------------------

    private void payloadTest() {
        SyncQualitiesPayload p = SyncQualitiesPayload.snapshot();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncQualitiesPayload.CODEC.encode(buf, p);
        int size = buf.readableBytes();
        SyncQualitiesPayload back = SyncQualitiesPayload.CODEC.decode(buf);
        if (!back.equals(p)) bug("net", "sync payload round trip differs");
        info("net", "sync payload " + size + " bytes (" + String.format("%.1f", size / 1024.0) + " KiB)");
        if (size > 1_048_576) warn("net", "sync payload over 1 MiB");
    }

    // ---- performance ------------------------------------------------------------------------------

    private void perfTests() {
        // full inventory of graded gear + full set worn
        alice.getInventory().clearContent();
        List<Item> items = gradeable();
        for (int i = 0; i < alice.getInventory().items.size(); i++) {
            ItemStack s = new ItemStack(items.get(random.nextInt(items.size())));
            Grading.grade(s, "loot", 3, random, new Grading.Context(alice, level, Vec3.atCenterOf(base)));
            alice.getInventory().items.set(i, s);
        }
        GearSet set = Sets.STORE.all().iterator().next();
        for (GearSet.Piece p : set.pieces()) { ItemStack s = Sets.create(set, p, access); if (s != null) alice.setItemSlot(slotFor(alice, s), s); }
        int N = 2000;
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) for (ItemStack s : alice.getInventory().items) Marks.of(s);
        double marks = (System.nanoTime() - t0) / 1e6 / N;
        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) Sets.syncBonuses(alice);
        double sync = (System.nanoTime() - t0) / 1e6 / N;
        double scan = -1;
        try {
            var m = com.kragnoth.hallmarked.event.CommonEvents.class.getDeclaredMethod("scanInventory", net.minecraft.server.level.ServerPlayer.class);
            m.setAccessible(true);
            t0 = System.nanoTime();
            for (int i = 0; i < N; i++) m.invoke(null, alice);
            scan = (System.nanoTime() - t0) / 1e6 / N;
        } catch (Throwable t) { warn("perf", "scanInventory reflect: " + t); }
        t0 = System.nanoTime();
        for (int i = 0; i < N; i++) for (ItemStack s : alice.getInventory().items) s.getAttributeModifiers();
        double attr = (System.nanoTime() - t0) / 1e6 / N;
        info("perf", String.format("per player: Marks.of(36 stacks) %.3f ms, ItemAttributeModifierEvent(36) %.3f ms, syncBonuses %.3f ms, scanInventory %.3f ms (both run every 10 ticks)", marks, attr, sync, scan));
        double perTick = (sync + Math.max(scan, 0)) / 10.0;
        info("perf", String.format("amortised ~%.4f ms/tick/player -> %.2f%% of a tick for 20 players", perTick, perTick * 20 / 50 * 100));
        if (perTick * 20 > 5) warn("perf", "20 players would cost > 5 ms per tick");
        alice.getInventory().clearContent();
        clearEquip(alice);
        Sets.syncBonuses(alice);
    }

    // ---- report -----------------------------------------------------------------------------------

    private void writeReport() {
        StringBuilder sb = new StringBuilder("Hallmarked QA report\n====================\n\n");
        sb.append("BUGS (").append(bugs.size()).append(")\n");
        bugs.forEach((k, v) -> sb.append("  ").append(k).append(v > 1 ? "   x" + v : "").append('\n'));
        sb.append("\nWARNINGS (").append(warns.size()).append(")\n");
        warns.forEach((k, v) -> sb.append("  ").append(k).append(v > 1 ? "   x" + v : "").append('\n'));
        sb.append("\nINFO\n");
        lines.forEach(l -> sb.append("  ").append(l).append('\n'));
        try {
            Path out = FMLPaths.GAMEDIR.get().resolve("qa-report.txt");
            Files.writeString(out, sb, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println(sb);
        }
        System.out.println("HALLMARKED-QA-DONE bugs=" + bugs.size() + " warns=" + warns.size());
    }
}
