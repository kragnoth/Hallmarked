package com.kragnoth.hallmarked.command;

import com.kragnoth.hallmarked.config.HallmarkedConfig;
import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.grade.Unique;
import com.kragnoth.hallmarked.inlay.Inlays;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.quality.Qualities;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.kragnoth.hallmarked.tempering.Tempering;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * Testing tools for pack and mod developers (op level 2, inherited from /hallmarked).
 * <pre>
 * /hallmarked generate [count] [options]              random equipment into your inventory
 * /hallmarked generate for &lt;players&gt; &lt;count&gt; [options]
 * /hallmarked lootbarrel [options]                     barrel in front of you, filled with generated loot
 * /hallmarked lootbarrel table &lt;loot_table&gt;          barrel that rolls a real loot table when opened
 * /hallmarked champion &lt;entity&gt; [options]             mob in front of you in generated gear (drops it all)
 * /hallmarked odds [source] [luck]                     grade odds for the held item (or an iron sword)
 * /hallmarked appraise                                 reveal the held item
 * /hallmarked data                                     what's loaded (after mod gating and enabled:false)
 * </pre>
 * Options are space-separated key=value pairs; with none, items roll exactly as natural loot would.
 * <pre>
 * item=minecraft:diamond_sword | item=#minecraft:swords   base item or item tag
 * mod=create                    only items from this namespace
 * category=tool|bow|shield|armor|helmet|chestplate|leggings|boots|bauble
 * source=loot|crafted|mob       which odds column to use (default loot)
 * luck=2.5                      luck for the roll (default: your Luck attribute)
 * grade=masterwork | grade=pure force a grade, or pick one uniformly (ignores weights)
 * roll=0..1                     set every affix roll (1 = best values)
 * affix=of_bleeding             add an affix (repeatable)
 * unique=lamplighters_hook | unique=random
 * quality=keen | quality=random | quality=none   (default: the config's auto-reforge chance)
 * appraised=true|false          force the Unappraised state either way
 * count=n                       (lootbarrel / champion: items per barrel)
 * extras=false                  (lootbarrel) leave out stamps, scrap and a seal
 * </pre>
 */
final class TestCommands {
    private TestCommands() {}

    private static final int BARREL_SLOTS = 27;

    // ---- Options -------------------------------------------------------------------------------

    static final class Spec {
        @Nullable ResourceLocation item;
        @Nullable TagKey<Item> tag;
        @Nullable String mod;
        @Nullable Category category;
        String source = Grading.SOURCE_LOOT;
        @Nullable Double luck;
        @Nullable String grade;
        boolean pure;
        @Nullable Float roll;
        final List<String> affixes = new ArrayList<>();
        @Nullable String unique;
        @Nullable String quality;
        @Nullable Boolean appraised;
        int count = -1;
        boolean extras = true;

        Spec copy() {
            Spec s = new Spec();
            s.item = item; s.tag = tag; s.mod = mod; s.category = category; s.source = source; s.luck = luck;
            s.grade = grade; s.pure = pure; s.roll = roll; s.affixes.addAll(affixes); s.unique = unique;
            s.quality = quality; s.appraised = appraised; s.count = count; s.extras = extras;
            return s;
        }
    }

    private static final List<String> KEYS = List.of("item", "mod", "category", "source", "luck", "grade", "roll",
            "affix", "unique", "quality", "appraised", "count", "extras");

    static Spec parse(String input) {
        Spec s = new Spec();
        if (input == null || input.isBlank()) return s;
        for (String tok : input.trim().split("\\s+")) {
            int eq = tok.indexOf('=');
            if (eq <= 0) throw new IllegalArgumentException("expected key=value, got '" + tok + "'");
            String k = tok.substring(0, eq).toLowerCase(Locale.ROOT), v = tok.substring(eq + 1);
            switch (k) {
                case "item" -> {
                    if (v.startsWith("#")) s.tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(v.substring(1)));
                    else s.item = ResourceLocation.parse(v);
                }
                case "mod" -> s.mod = v;
                case "category" -> s.category = Category.valueOf(v.toUpperCase(Locale.ROOT));
                case "source" -> s.source = v;
                case "luck" -> s.luck = Double.parseDouble(v);
                case "grade" -> { if (v.equals("pure")) s.pure = true; else s.grade = v; }
                case "roll" -> s.roll = Math.max(0f, Math.min(1f, Float.parseFloat(v)));
                case "affix" -> s.affixes.add(v);
                case "unique" -> s.unique = v;
                case "quality" -> s.quality = v;
                case "appraised" -> s.appraised = Boolean.parseBoolean(v);
                case "count" -> s.count = Integer.parseInt(v);
                case "extras" -> s.extras = Boolean.parseBoolean(v);
                default -> throw new IllegalArgumentException("unknown option '" + k + "' (use " + String.join(", ", KEYS) + ")");
            }
        }
        return s;
    }

    /** Suggests keys, then values for the key being typed. */
    private static final SuggestionProvider<CommandSourceStack> OPTIONS = (c, b) -> {
        String rem = b.getRemaining();
        int start = rem.lastIndexOf(' ') + 1;
        String tok = rem.substring(start);
        SuggestionsBuilder sb = b.createOffset(b.getStart() + start);
        int eq = tok.indexOf('=');
        if (eq < 0) return SharedSuggestionProvider.suggest(KEYS.stream().map(k -> k + "="), sb);
        String key = tok.substring(0, eq);
        Stream<String> values = switch (key) {
            case "category" -> Stream.of(Category.values()).map(Category::key);
            case "source" -> Stream.of(Grading.SOURCE_LOOT, Grading.SOURCE_CRAFTED, Grading.SOURCE_MOB);
            case "grade" -> Stream.concat(Stream.of("pure"), Grading.GRADES.all().stream().map(g -> shortId(g.name())));
            case "affix" -> Grading.AFFIXES.all().stream().map(a -> shortId(a.name()));
            case "unique" -> Stream.concat(Stream.of("random"), Grading.UNIQUES.all().stream().map(u -> shortId(u.name())));
            case "quality" -> Stream.concat(Stream.of("random", "none"), Qualities.all().stream().map(q -> shortId(q.name())));
            case "appraised", "extras" -> Stream.of("true", "false");
            case "roll" -> Stream.of("0", "0.5", "1");
            case "mod" -> net.neoforged.fml.ModList.get().getMods().stream().map(m -> m.getModId());
            case "item" -> BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString);
            default -> Stream.empty();
        };
        String prefix = key + "=";
        return SharedSuggestionProvider.suggest(values.map(v -> prefix + v), sb);
    };

    private static String shortId(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(i + 1);
    }

    // ---- Generation ----------------------------------------------------------------------------

    /** Every gradeable item that passes the spec's item filters. */
    static List<Item> candidates(Spec s) {
        List<Item> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (s.item != null && !s.item.equals(id)) continue;
            if (s.mod != null && !s.mod.equals(id.getNamespace())) continue;
            ItemStack stack = new ItemStack(item);
            if (s.tag != null && !stack.is(s.tag)) continue;
            if (!Grading.canGrade(stack) && !Reforging.isEligible(stack)) continue;
            if (s.category != null && !inCategory(stack, s.category)) continue;
            out.add(item);
        }
        return out;
    }

    private static boolean inCategory(ItemStack stack, Category want) {
        Category c = Equipment.categoryOf(stack);
        if (c == null) return false;
        if (c == Category.ARMOR && want != Category.ARMOR) return want == Equipment.armorPiece(stack);
        return c == want;
    }

    /**
     * Builds one item from the spec. Throws IllegalArgumentException with a readable message on bad input.
     * {@code pool} is the candidate list (computed once per command), used unless a unique picks its own base.
     */
    static ItemStack generate(Spec s, List<Item> pool, RandomSource random, Grading.Context ctx, double defaultLuck) {
        double luck = s.luck != null ? s.luck : defaultLuck;

        // A named or random unique picks its own base item.
        Unique unique = null;
        if (s.unique != null) {
            List<Unique> all = new ArrayList<>(Grading.UNIQUES.all());
            if (s.unique.equals("random")) {
                if (all.isEmpty()) throw new IllegalArgumentException("no uniques are loaded");
                unique = all.get(random.nextInt(all.size()));
            } else {
                unique = Grading.UNIQUES.get(s.unique);
                if (unique == null) unique = Grading.UNIQUES.get("hallmarked.unique." + s.unique);
                if (unique == null) throw new IllegalArgumentException("unknown unique '" + s.unique + "'");
            }
        }

        ItemStack stack;
        if (unique != null) {
            ItemStack[] bases = unique.items().getItems();
            if (bases.length == 0) throw new IllegalArgumentException("unique " + unique.name() + " has no base items");
            stack = bases[random.nextInt(bases.length)].copy();
        } else {
            if (pool.isEmpty()) throw new IllegalArgumentException("no equipment matches those filters");
            stack = new ItemStack(pool.get(random.nextInt(pool.size())));
        }

        // Grade.
        if (unique != null) {
            Grade g = Grading.GRADES.get(Grading.UNIQUE_GRADE);
            if (g == null) throw new IllegalArgumentException("the unique grade is disabled");
            stack.set(HMRegistries.UNIQUE.get(), unique.name());
            stack.set(HMRegistries.GRADE.get(), new GradeData(g.name(), List.of(), "", ""));
            seal(stack, g, s.source);
        } else if (s.grade != null || s.pure) {
            Grade g;
            if (s.pure) {
                List<Grade> grades = Grading.GRADES.all().stream().filter(x -> !x.name().equals(Grading.UNIQUE_GRADE)).toList();
                if (grades.isEmpty()) throw new IllegalArgumentException("no grades are loaded");
                g = grades.get(random.nextInt(grades.size()));
            } else {
                g = Grading.findGrade(s.grade);
                if (g == null) throw new IllegalArgumentException("unknown grade '" + s.grade + "'");
            }
            if (Grading.canGrade(stack)) {
                stack.set(HMRegistries.GRADE.get(), Grading.rollData(stack, g, random));
                seal(stack, g, s.source);
            }
        } else if (Grading.canGrade(stack)) {
            Grading.grade(stack, s.source, luck, random, ctx);
        }

        // Affix tuning.
        GradeData d = Grading.dataOf(stack);
        if (d != null && (s.roll != null || !s.affixes.isEmpty())) {
            List<GradeData.Rolled> list = new ArrayList<>();
            for (GradeData.Rolled r : d.affixes()) list.add(s.roll != null ? new GradeData.Rolled(r.affix(), s.roll) : r);
            for (String name : s.affixes) {
                Affix a = Grading.AFFIXES.get(name);
                if (a == null) a = Grading.AFFIXES.get("hallmarked.affix." + name);
                if (a == null) throw new IllegalArgumentException("unknown or not-loaded affix '" + name + "'");
                list.add(new GradeData.Rolled(a.name(), s.roll != null ? s.roll : random.nextFloat()));
            }
            stack.set(HMRegistries.GRADE.get(), new GradeData(d.grade(), list, d.titleFirst(), d.titleSecond()));
        }

        // Quality. ROLLED is always set so the inventory scan doesn't roll again on pickup.
        if (Reforging.isEligible(stack)) {
            String q = s.quality;
            if (q == null) {
                if (HallmarkedConfig.AUTO_REFORGE.get() && random.nextDouble() < HallmarkedConfig.AUTO_REFORGE_CHANCE.get()) {
                    Reforging.reforge(stack, random);
                }
            } else if (q.equals("random")) {
                Reforging.reforge(stack, random);
            } else if (!q.equals("none")) {
                Quality quality = Qualities.get(q);
                if (quality == null) quality = Qualities.get(Quality.PREFIX + q);
                if (quality == null) throw new IllegalArgumentException("unknown quality '" + q + "'");
                stack.set(HMRegistries.QUALITY.get(), quality.name());
            }
            stack.set(HMRegistries.ROLLED.get(), true);
        }

        if (s.appraised != null && stack.has(HMRegistries.GRADE.get())) {
            if (s.appraised) stack.remove(HMRegistries.UNAPPRAISED.get());
            else stack.set(HMRegistries.UNAPPRAISED.get(), true);
        }
        Grading.refreshName(stack);
        return stack;
    }

    /** Same sealing rule as natural grading. */
    private static void seal(ItemStack stack, Grade g, String source) {
        if (g.appraise() && HallmarkedConfig.APPRAISAL.get() && !Grading.SOURCE_CRAFTED.equals(source)) {
            stack.set(HMRegistries.UNAPPRAISED.get(), true);
        }
    }

    // ---- Commands ------------------------------------------------------------------------------

    static LiteralArgumentBuilder<CommandSourceStack> generateCommand() {
        return Commands.literal("generate")
                .executes(c -> give(c, List.of(c.getSource().getPlayerOrException()), 1, ""))
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(c -> give(c, List.of(c.getSource().getPlayerOrException()), IntegerArgumentType.getInteger(c, "count"), ""))
                        .then(Commands.argument("options", StringArgumentType.greedyString()).suggests(OPTIONS)
                                .executes(c -> give(c, List.of(c.getSource().getPlayerOrException()),
                                        IntegerArgumentType.getInteger(c, "count"), StringArgumentType.getString(c, "options")))))
                .then(Commands.literal("for")
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(c -> give(c, EntityArgument.getPlayers(c, "players"), IntegerArgumentType.getInteger(c, "count"), ""))
                                        .then(Commands.argument("options", StringArgumentType.greedyString()).suggests(OPTIONS)
                                                .executes(c -> give(c, EntityArgument.getPlayers(c, "players"),
                                                        IntegerArgumentType.getInteger(c, "count"), StringArgumentType.getString(c, "options")))))));
    }

    private static int give(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> players, int count, String options) {
        Spec spec;
        List<Item> pool;
        try {
            spec = parse(options);
            pool = candidates(spec);
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        int made = 0;
        for (ServerPlayer p : players) {
            for (int i = 0; i < count; i++) {
                ItemStack stack;
                try {
                    stack = generate(spec, pool, p.getRandom(), Grading.Context.of(p), p.getLuck());
                } catch (IllegalArgumentException e) {
                    return fail(c, e.getMessage());
                }
                Component name = stack.getHoverName();
                if (!p.getInventory().add(stack)) p.drop(stack, false);
                made++;
                if (count <= 4) c.getSource().sendSuccess(() -> Component.literal("Generated ").append(name), false);
            }
        }
        int total = made;
        c.getSource().sendSuccess(() -> Component.literal("Generated " + total + " item(s) from " + pool.size() + " candidates"), false);
        return made;
    }

    static LiteralArgumentBuilder<CommandSourceStack> lootBarrelCommand() {
        SuggestionProvider<CommandSourceStack> tables = (c, b) ->
                SharedSuggestionProvider.suggestResource(c.getSource().getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE), b);
        return Commands.literal("lootbarrel")
                .executes(c -> lootBarrel(c, ""))
                .then(Commands.literal("table")
                        .then(Commands.argument("table", ResourceLocationArgument.id()).suggests(tables)
                                .executes(c -> tableBarrel(c, ResourceLocationArgument.getId(c, "table")))))
                .then(Commands.argument("options", StringArgumentType.greedyString()).suggests(OPTIONS)
                        .executes(c -> lootBarrel(c, StringArgumentType.getString(c, "options"))));
    }

    @Nullable
    private static BarrelBlockEntity placeBarrel(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        Direction facing = p.getDirection();
        BlockPos pos = p.blockPosition().relative(facing);
        if (!level.getBlockState(pos).canBeReplaced()) pos = pos.above();
        if (!level.getBlockState(pos).canBeReplaced()) {
            fail(c, "No room in front of you for a barrel");
            return null;
        }
        level.setBlock(pos, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing.getOpposite()), 3);
        return level.getBlockEntity(pos) instanceof BarrelBlockEntity be ? be : null;
    }

    private static int lootBarrel(CommandContext<CommandSourceStack> c, String options) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Spec spec;
        List<Item> pool;
        try {
            spec = parse(options);
            pool = candidates(spec);
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        int extras = spec.extras ? 3 : 0;
        int gear = spec.count > 0 ? Math.min(spec.count, BARREL_SLOTS - extras) : BARREL_SLOTS - extras;
        List<ItemStack> items = new ArrayList<>();
        try {
            for (int i = 0; i < gear; i++) items.add(generate(spec, pool, p.getRandom(), new Grading.Context(p, p.level(), p.position()), p.getLuck()));
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        if (spec.extras) {
            items.add(new ItemStack(HMRegistries.SMITHS_STAMP.get(), 4));
            items.add(new ItemStack(HMRegistries.SMITHS_SCRAP.get(), 16));
            items.add(new ItemStack(HMRegistries.GEARWRIGHTS_SEAL.get()));
        }
        BarrelBlockEntity be = placeBarrel(c);
        if (be == null) return 0;
        for (int i = 0; i < items.size(); i++) be.setItem(i, items.get(i));
        be.setChanged();
        int n = gear;
        c.getSource().sendSuccess(() -> Component.literal("Loot barrel: " + n + " generated piece(s)" + (spec.extras ? " + stamps, scrap and a seal" : "")), false);
        return 1;
    }

    /** A barrel carrying a real loot table: it rolls (through every loot modifier) when first opened. */
    private static int tableBarrel(CommandContext<CommandSourceStack> c, ResourceLocation table) throws CommandSyntaxException {
        if (!c.getSource().getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE).contains(table)) {
            return fail(c, "Unknown loot table " + table);
        }
        BarrelBlockEntity be = placeBarrel(c);
        if (be == null) return 0;
        be.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, table), c.getSource().getPlayerOrException().getRandom().nextLong());
        be.setChanged();
        c.getSource().sendSuccess(() -> Component.literal("Loot barrel with table " + table + " (rolls when opened, with your Luck)"), false);
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> championCommand() {
        SuggestionProvider<CommandSourceStack> mobs = (c, b) -> SharedSuggestionProvider.suggestResource(
                BuiltInRegistries.ENTITY_TYPE.keySet().stream().filter(id -> {
                    EntityType<?> t = BuiltInRegistries.ENTITY_TYPE.get(id);
                    return t.canSummon() && t.getCategory() != net.minecraft.world.entity.MobCategory.MISC;
                }), b);
        return Commands.literal("champion")
                .then(Commands.argument("entity", ResourceLocationArgument.id()).suggests(mobs)
                        .executes(c -> champion(c, ResourceLocationArgument.getId(c, "entity"), ""))
                        .then(Commands.argument("options", StringArgumentType.greedyString()).suggests(OPTIONS)
                                .executes(c -> champion(c, ResourceLocationArgument.getId(c, "entity"), StringArgumentType.getString(c, "options")))));
    }

    /**
     * Spawns the mob in front of the player wearing generated gear (source mob unless overridden), with every
     * piece set to drop. Item filters (item, tag, mod, category) apply to the weapon only; armor is any armor
     * that fits the slot.
     */
    private static int champion(CommandContext<CommandSourceStack> c, ResourceLocation id, String options) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null) return fail(c, "Unknown entity " + id);
        if (!(type.create(level) instanceof Mob mob)) return fail(c, id + " isn't a mob that can hold equipment");
        Spec spec;
        try {
            spec = parse(options.contains("source=") ? options : (options + " source=" + Grading.SOURCE_MOB).trim());
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        Vec3 at = p.position().add(Vec3.directionFromRotation(0, p.getYRot()).scale(3));
        mob.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
        net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.COMMAND, null);

        RandomSource random = mob.getRandom();
        Grading.Context ctx = new Grading.Context(null, level, at);
        try {
            // Weapon: a bow for skeletons, otherwise a sword or axe unless the options say otherwise.
            Spec weapon = spec.copy();
            boolean filtered = weapon.item != null || weapon.tag != null || weapon.mod != null || weapon.category != null;
            List<Item> weapons = candidates(weapon);
            if (!filtered) {
                Predicate<Item> fits = mob instanceof AbstractSkeleton
                        ? i -> i instanceof BowItem
                        : i -> new ItemStack(i).is(ItemTags.SWORDS) || new ItemStack(i).is(ItemTags.AXES);
                List<Item> narrowed = weapons.stream().filter(fits).toList();
                if (!narrowed.isEmpty()) weapons = narrowed;
            }
            equip(mob, EquipmentSlot.MAINHAND, generate(weapon, weapons, random, ctx, 0));

            Spec armor = spec.copy();
            armor.item = null; armor.tag = null; armor.mod = null; armor.unique = null; armor.affixes.clear();
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                armor.category = switch (slot) {
                    case HEAD -> Category.HELMET;
                    case CHEST -> Category.CHESTPLATE;
                    case LEGS -> Category.LEGGINGS;
                    default -> Category.BOOTS;
                };
                List<Item> fit = candidates(armor).stream().filter(i -> mob.getEquipmentSlotForItem(new ItemStack(i)) == slot).toList();
                if (!fit.isEmpty()) equip(mob, slot, generate(armor, fit, random, ctx, 0));
            }
        } catch (IllegalArgumentException e) {
            return fail(c, e.getMessage());
        }
        mob.setPersistenceRequired();
        level.addFreshEntityWithPassengers(mob);
        Component name = mob.getMainHandItem().getHoverName();
        c.getSource().sendSuccess(() -> Component.literal("Summoned " + id + " wielding ").append(name), false);
        return 1;
    }

    private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 2.0F); // > 1 drops undamaged
    }

    static LiteralArgumentBuilder<CommandSourceStack> oddsCommand() {
        SuggestionProvider<CommandSourceStack> sources = (c, b) ->
                SharedSuggestionProvider.suggest(List.of(Grading.SOURCE_LOOT, Grading.SOURCE_CRAFTED, Grading.SOURCE_MOB), b);
        return Commands.literal("odds")
                .executes(c -> odds(c, Grading.SOURCE_LOOT, null, null))
                .then(Commands.argument("source", StringArgumentType.word()).suggests(sources)
                        .executes(c -> odds(c, StringArgumentType.getString(c, "source"), null, null))
                        .then(Commands.argument("luck", DoubleArgumentType.doubleArg(-1024, 1024))
                                .executes(c -> odds(c, StringArgumentType.getString(c, "source"), DoubleArgumentType.getDouble(c, "luck"), null))
                                .then(Commands.argument("table", ResourceLocationArgument.id()).suggests((c, b) ->
                                                SharedSuggestionProvider.suggestResource(c.getSource().getServer().reloadableRegistries().getKeys(Registries.LOOT_TABLE), b))
                                        .executes(c -> odds(c, StringArgumentType.getString(c, "source"), DoubleArgumentType.getDouble(c, "luck"),
                                                ResourceLocationArgument.getId(c, "table"))))));
    }

    /** Exact odds at your position (regions apply) with the held item, or an iron sword if you hold no equipment. */
    private static int odds(CommandContext<CommandSourceStack> c, String source, @Nullable Double luckArg,
                            @Nullable ResourceLocation table) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack held = p.getMainHandItem();
        ItemStack probe = Grading.canGrade(held) ? held : new ItemStack(Items.IRON_SWORD);
        double luck = luckArg != null ? luckArg : p.getLuck();
        Grading.Context ctx = Grading.SOURCE_CRAFTED.equals(source) ? Grading.Context.of(p) : new Grading.Context(p, p.level(), p.position(), table);
        Map<Grade, Double> w = new java.util.LinkedHashMap<>(Grading.gradeWeights(probe, source, luck, ctx));
        // Material tier cap: grades above it fold into the cap, as they do when an item is graded.
        Grading.Cap cap = Grading.capFor(probe, ctx);
        if (cap != null) {
            double folded = 0;
            for (var e : w.entrySet()) {
                if (!e.getKey().name().equals(Grading.UNIQUE_GRADE) && e.getKey().rank() > cap.grade().rank()) {
                    folded += e.getValue();
                    e.setValue(0.0);
                }
            }
            w.merge(cap.overflow(), folded, Double::sum);
        }
        double total = w.values().stream().mapToDouble(Double::doubleValue).sum();
        double unique = Grading.uniqueChance(probe, source);
        c.getSource().sendSuccess(() -> Component.literal("Grade odds for ").append(probe.getHoverName())
                .append(" — source " + source + ", luck " + String.format("%.2f", luck) + " (before regions):"), false);
        if (total <= 0) return fail(c, "Every grade has weight 0 for this source");
        w.entrySet().stream()
                .sorted((a, b) -> Integer.compare(a.getKey().rank(), b.getKey().rank()))
                .filter(e -> e.getValue() > 0)
                .forEach(e -> {
                    double pct = e.getValue() / total * 100 * (1 - unique);
                    c.getSource().sendSuccess(() -> Component.literal(String.format("  %6.2f%%  ", pct))
                            .append(Component.translatable(e.getKey().name()).withStyle(e.getKey().color())), false);
                });
        if (unique > 0) c.getSource().sendSuccess(() -> Component.literal(String.format("  %6.2f%%  unique (rolled first)", unique * 100)), false);
        if (cap != null) {
            c.getSource().sendSuccess(() -> Component.literal("  Tier " + shortId(cap.tier().name()) + ": capped at ")
                    .append(Component.translatable(cap.grade().name()).withStyle(cap.grade().color()))
                    .append(cap.maxRoll() < 1f ? String.format(", affix rolls up to %.2f", cap.maxRoll()) : "")
                    .append(cap.shift() > 0 ? " (raised " + cap.shift() + " by this table)" : ""), false);
        }
        var itemTier = com.kragnoth.hallmarked.tier.ItemTier.of(probe);
        if (itemTier != null && (itemTier.luck() != 0 || itemTier.rollBonus() > 0)) {
            c.getSource().sendSuccess(() -> Component.literal(String.format("  Tier %s: luck %+.1f, affix rolls %.0f%% closer to best",
                    shortId(itemTier.name()), itemTier.luck(), itemTier.rollBonus() * 100)), false);
        }
        if (table != null) {
            var eff = com.kragnoth.hallmarked.tier.LootOdds.forTable(table);
            c.getSource().sendSuccess(() -> Component.literal(String.format("  Table %s: luck %+.1f, cap shift %d", table, eff.luck(), eff.capShift())), false);
        }
        return 1;
    }

    static LiteralArgumentBuilder<CommandSourceStack> appraiseCommand() {
        return Commands.literal("appraise").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            ItemStack s = p.getMainHandItem();
            if (!Grading.isUnappraised(s)) return fail(c, "Held item isn't Unappraised");
            Grading.appraise(s, p);
            c.getSource().sendSuccess(() -> Component.literal("Appraised ").append(s.getHoverName()), false);
            return 1;
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> dataCommand() {
        return Commands.literal("data").executes(c -> {
            CommandSourceStack src = c.getSource();
            Map<Affix.Type, Long> byType = new java.util.EnumMap<>(Affix.Type.class);
            for (Affix a : Grading.AFFIXES.all()) byType.merge(a.type(), 1L, Long::sum);
            line(src, "Qualities: " + Qualities.all().size());
            line(src, "Grades: " + Grading.GRADES.all().size());
            line(src, "Affixes: " + Grading.AFFIXES.all().size() + "  " + byType);
            line(src, "Fusions: " + Grading.FUSIONS.all().size());
            line(src, "Inlays: " + Inlays.STORE.all().size());
            line(src, "Uniques: " + Grading.UNIQUES.all().size());
            line(src, "Regions: " + Grading.REGIONS.all().size());
            line(src, "Tempering outcomes: " + Tempering.OUTCOMES.all().size());
            line(src, "Item tiers: " + com.kragnoth.hallmarked.tier.ItemTier.STORE.all().size()
                    + "  Loot odds: " + com.kragnoth.hallmarked.tier.LootOdds.STORE.all().size());
            line(src, "Equipment items: " + candidates(new Spec()).size());
            line(src, "(Entries skipped for missing mods or with errors are listed in the server log.)");
            return 1;
        });
    }

    private static void line(CommandSourceStack src, String s) {
        src.sendSuccess(() -> Component.literal(s), false);
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
