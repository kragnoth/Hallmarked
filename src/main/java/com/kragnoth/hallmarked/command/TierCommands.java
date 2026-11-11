package com.kragnoth.hallmarked.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kragnoth.hallmarked.quality.Category;
import com.kragnoth.hallmarked.quality.Equipment;
import com.kragnoth.hallmarked.tier.ItemTier;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.fml.loading.FMLPaths;

/**
 * {@code /hallmarked tiers export [all]}: a pack-maker report of equipment and the material tier each item falls in.
 * By default only items with no tier (uncapped, like iron and up) are listed, each with a suggested tier worked out by
 * comparing its durability, enchantability and armor values to vanilla materials. Writes, under
 * {@code <game dir>/hallmarked_exports/tiers-<timestamp>/}:
 * <ul>
 *   <li>{@code report.csv}: one row per item, for a spreadsheet</li>
 *   <li>{@code suggested_datapack/}: a ready datapack whose {@code hallmarked:tier/*} tags hold the suggestions;
 *       review it, then drop it into a world's datapacks folder (or copy the tag files into your own pack)</li>
 * </ul>
 */
final class TierCommands {
    private TierCommands() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<String> TIERS = List.of("crude", "plain", "golden");

    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("tiers").then(Commands.literal("export")
                .executes(c -> export(c, false))
                .then(Commands.literal("all").executes(c -> export(c, true))));
    }

    record Row(String id, String mod, String category, String material, int durability, int enchantability,
               String stats, String tier, String suggested, String why) {}

    private static int export(CommandContext<CommandSourceStack> c, boolean all) {
        List<Row> rows = new ArrayList<>();
        for (Item item : TestCommands.candidates(new TestCommands.Spec())) {
            ItemStack stack = new ItemStack(item);
            Category cat = Equipment.categoryOf(stack);
            if (cat == null) continue;
            ItemTier tier = ItemTier.of(stack);
            if (tier != null && !all) continue;
            rows.add(row(stack, cat, tier));
        }
        rows.sort((a, b) -> a.id.compareTo(b.id));

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = FMLPaths.GAMEDIR.get().resolve("hallmarked_exports").resolve("tiers-" + stamp);
        Map<String, List<String>> suggestions = new TreeMap<>();
        for (String t : TIERS) suggestions.put(t, new ArrayList<>());
        for (Row r : rows) if (r.tier.isEmpty() && suggestions.containsKey(r.suggested)) suggestions.get(r.suggested).add(r.id);

        try {
            Files.createDirectories(dir);
            StringBuilder csv = new StringBuilder("item,mod,category,material,durability,enchantability,stats,current_tier,suggested_tier,why\n");
            for (Row r : rows) {
                csv.append(String.join(",", r.id, r.mod, r.category, q(r.material), String.valueOf(r.durability),
                        String.valueOf(r.enchantability), q(r.stats), r.tier.isEmpty() ? "uncapped" : r.tier,
                        r.suggested.isEmpty() ? "-" : r.suggested, q(r.why))).append('\n');
            }
            Files.writeString(dir.resolve("report.csv"), csv, StandardCharsets.UTF_8);

            Path pack = dir.resolve("suggested_datapack");
            Path tags = pack.resolve("data/hallmarked/tags/item/tier");
            Files.createDirectories(tags);
            JsonObject meta = new JsonObject(), packInfo = new JsonObject();
            packInfo.addProperty("pack_format", 48);
            packInfo.addProperty("description", "Hallmarked tier suggestions " + stamp + " (review before use)");
            meta.add("pack", packInfo);
            Files.writeString(pack.resolve("pack.mcmeta"), GSON.toJson(meta), StandardCharsets.UTF_8);
            for (String t : TIERS) {
                JsonObject tag = new JsonObject();
                tag.addProperty("replace", false);
                JsonArray values = new JsonArray();
                for (String id : suggestions.get(t)) {
                    JsonObject entry = new JsonObject(); // optional, so the pack still loads if a mod is removed
                    entry.addProperty("id", id);
                    entry.addProperty("required", false);
                    values.add(entry);
                }
                tag.add("values", values);
                Files.writeString(tags.resolve(t + ".json"), GSON.toJson(tag), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            c.getSource().sendFailure(Component.literal("Hallmarked: export failed: " + e.getMessage()));
            return 0;
        }

        Map<String, Integer> byMod = new TreeMap<>();
        for (Row r : rows) byMod.merge(r.mod, 1, Integer::sum);
        String path = dir.toAbsolutePath().toString();
        CommandSourceStack src = c.getSource();
        src.sendSuccess(() -> Component.literal((all ? "Exported " + rows.size() + " equipment items"
                : rows.size() + " equipment items have no tier (uncapped)") + " from " + byMod.size() + " mods."), false);
        for (String t : TIERS) {
            int n = suggestions.get(t).size();
            if (n > 0) src.sendSuccess(() -> Component.literal("  suggested " + t + ": " + n), false);
        }
        src.sendSuccess(() -> Component.literal("  Report and a suggested datapack: ")
                .append(Component.literal(path).withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, path))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Open folder"))))), false);
        return rows.size();
    }

    private static Row row(ItemStack stack, Category cat, ItemTier tier) {
        Item item = stack.getItem();
        var id = BuiltInRegistries.ITEM.getKey(item);
        String category = cat == Category.ARMOR ? String.valueOf(Equipment.armorPiece(stack)).toLowerCase(Locale.ROOT)
                : cat.key();
        if (cat == Category.TOOL) {
            boolean w = Equipment.isWeapon(stack), d = Equipment.isDigger(stack);
            category += w && d ? " (weapon+digger)" : w ? " (weapon)" : d ? " (digger)" : "";
        }
        String tierName = tier == null ? "" : tier.name().substring(tier.name().lastIndexOf('.') + 1);
        int durability = stack.getMaxDamage();
        int ench = item.getEnchantmentValue(stack);

        if (item instanceof TieredItem t) {
            var mat = t.getTier();
            String material = mat instanceof Tiers v ? "vanilla " + v.name().toLowerCase(Locale.ROOT) : mat.getClass().getSimpleName();
            int uses = mat.getUses();
            String stats = "uses " + uses + ", damage bonus " + mat.getAttackDamageBonus() + ", speed " + mat.getSpeed();
            String suggested, why;
            // Vanilla reference: wood 59 uses / 15 ench, stone 131 / 5, gold 32 / 22, iron 250 / 14.
            if (mat.getEnchantmentValue() >= 20 && uses < 100) { suggested = "golden"; why = "high enchantability, low durability (like gold)"; }
            else if (uses <= 80) { suggested = "crude"; why = "durability at or below wood (" + uses + " <= 80)"; }
            else if (uses <= 200) { suggested = "plain"; why = "durability between stone and iron (" + uses + " <= 200)"; }
            else { suggested = ""; why = "iron-class or better (" + uses + " uses)"; }
            return new Row(id.toString(), id.getNamespace(), category, material, durability, mat.getEnchantmentValue(), stats, tierName, suggested, why);
        }
        if (item instanceof ArmorItem a) {
            ArmorMaterial mat = a.getMaterial().value();
            String material = a.getMaterial().unwrapKey().map(k -> k.location().toString()).orElse("unregistered");
            int total = 0;
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE,
                    ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS}) total += mat.defense().getOrDefault(type, 0);
            String stats = "set armor " + total + ", toughness " + mat.toughness();
            String suggested, why;
            // Vanilla full-set armor: leather 7, gold 11, chainmail 12, iron 15, diamond 20, netherite 20.
            if (mat.enchantmentValue() >= 20 && total <= 12) { suggested = "golden"; why = "high enchantability, light armor (like gold)"; }
            else if (total <= 8) { suggested = "crude"; why = "set armor " + total + " <= 8 (like leather)"; }
            else if (total <= 13) { suggested = "plain"; why = "set armor " + total + " <= 13 (like chainmail)"; }
            else { suggested = ""; why = "iron-class or better (set armor " + total + ")"; }
            return new Row(id.toString(), id.getNamespace(), category, material, durability, mat.enchantmentValue(), stats, tierName, suggested, why);
        }
        return new Row(id.toString(), id.getNamespace(), category, "unknown", durability, ench, "", tierName, "",
                "no vanilla material to compare; decide by hand");
    }

    /** CSV-quotes a field. */
    private static String q(String s) {
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
