package com.kragnoth.hallmarked.command;

import com.kragnoth.hallmarked.grade.Affix;
import com.kragnoth.hallmarked.grade.Grade;
import com.kragnoth.hallmarked.grade.GradeData;
import com.kragnoth.hallmarked.grade.Grading;
import com.kragnoth.hallmarked.grade.Marks;
import com.kragnoth.hallmarked.quality.Qualities;
import com.kragnoth.hallmarked.quality.Quality;
import com.kragnoth.hallmarked.quality.Reforging;
import com.kragnoth.hallmarked.registry.HMRegistries;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Held-item tools for pack testing (op level 2, inherited from /hallmarked):
 * <pre>
 * /hallmarked inspect
 * /hallmarked grade roll [source]        grade naturally, as if from that source
 * /hallmarked grade set &lt;grade&gt;          force a grade, rolling its affixes
 * /hallmarked grade clear
 * /hallmarked quality roll | set &lt;name&gt; | clear
 * /hallmarked affix add &lt;affix&gt; [roll]   append an affix (roll 0..1, default 1)
 * </pre>
 */
final class ItemCommands {
    private ItemCommands() {}

    private static final SuggestionProvider<CommandSourceStack> GRADES = (c, b) ->
            SharedSuggestionProvider.suggest(Grading.GRADES.all().stream().map(g -> shortId(g.name())), b);
    private static final SuggestionProvider<CommandSourceStack> QUALITIES = (c, b) ->
            SharedSuggestionProvider.suggest(Qualities.all().stream().map(q -> shortId(q.name())), b);
    private static final SuggestionProvider<CommandSourceStack> AFFIXES = (c, b) ->
            SharedSuggestionProvider.suggest(Grading.AFFIXES.all().stream().map(a -> shortId(a.name())), b);
    private static final SuggestionProvider<CommandSourceStack> SOURCES = (c, b) ->
            SharedSuggestionProvider.suggest(List.of(Grading.SOURCE_CRAFTED, Grading.SOURCE_LOOT), b);

    private static String shortId(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? name : name.substring(i + 1);
    }

    static LiteralArgumentBuilder<CommandSourceStack> inspect() {
        return Commands.literal("inspect").executes(c -> {
            ItemStack s = held(c);
            CommandSourceStack src = c.getSource();
            src.sendSuccess(() -> s.getHoverName().copy(), false);
            String q = s.get(HMRegistries.QUALITY.get());
            src.sendSuccess(() -> Component.literal("Quality: " + (q == null ? "-" : q)), false);
            GradeData d = Grading.dataOf(s);
            if (d == null) {
                src.sendSuccess(() -> Component.literal("Grade: -"), false);
            } else {
                String title = d.titled() ? "  title: " + d.titleFirst() + " " + d.titleSecond() : "";
                src.sendSuccess(() -> Component.literal("Grade: " + d.grade() + title), false);
                for (GradeData.Rolled r : d.affixes()) {
                    src.sendSuccess(() -> Component.literal("  " + r.affix() + "  roll " + String.format("%.3f", r.roll())), false);
                }
                for (Grading.Fused f : Grading.fusions(d)) {
                    src.sendSuccess(() -> Component.literal("  fused: " + f.fusion().name()), false);
                }
            }
            int mods = Marks.of(s).size();
            src.sendSuccess(() -> Component.literal(mods + " attribute modifiers"), false);
            return 1;
        });
    }

    static LiteralArgumentBuilder<CommandSourceStack> grade() {
        return Commands.literal("grade")
                .then(Commands.literal("roll")
                        .executes(c -> rollGrade(c, Grading.SOURCE_CRAFTED))
                        .then(Commands.argument("source", StringArgumentType.word()).suggests(SOURCES)
                                .executes(c -> rollGrade(c, StringArgumentType.getString(c, "source")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("grade", StringArgumentType.word()).suggests(GRADES)
                                .executes(c -> {
                                    ItemStack s = held(c);
                                    Grade g = Grading.findGrade(StringArgumentType.getString(c, "grade"));
                                    if (g == null) return fail(c, "Unknown grade");
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    s.set(HMRegistries.GRADE.get(), Grading.rollData(s, g, p.getRandom()));
                                    Grading.refreshName(s);
                                    return ok(c, "Set grade " + g.name());
                                })))
                .then(Commands.literal("clear").executes(c -> {
                    ItemStack s = held(c);
                    s.remove(HMRegistries.GRADE.get());
                    Grading.refreshName(s); // restores any base name another mod had given it
                    return ok(c, "Cleared grade (it will re-roll on the next inventory scan unless grading is off)");
                }));
    }

    private static int rollGrade(CommandContext<CommandSourceStack> c, String source) throws CommandSyntaxException {
        ItemStack s = held(c);
        if (!Grading.canGrade(s)) return fail(c, "Held item isn't equipment");
        ServerPlayer p = c.getSource().getPlayerOrException();
        s.remove(HMRegistries.GRADE.get());
        Grading.grade(s, source, p.getLuck(), p.getRandom(), Grading.Context.of(p));
        GradeData d = Grading.dataOf(s);
        return ok(c, "Rolled " + (d == null ? "nothing" : d.grade()) + " from source '" + source + "'");
    }

    static LiteralArgumentBuilder<CommandSourceStack> quality() {
        return Commands.literal("quality")
                .then(Commands.literal("roll").executes(c -> {
                    ItemStack s = held(c);
                    if (!Reforging.reforge(s, c.getSource().getPlayerOrException().getRandom())) return fail(c, "No quality fits this item");
                    return ok(c, "Rolled " + s.get(HMRegistries.QUALITY.get()));
                }))
                .then(Commands.literal("set")
                        .then(Commands.argument("quality", StringArgumentType.word()).suggests(QUALITIES)
                                .executes(c -> {
                                    String arg = StringArgumentType.getString(c, "quality");
                                    Quality q = Qualities.get(arg);
                                    if (q == null) q = Qualities.get(Quality.PREFIX + arg);
                                    if (q == null) return fail(c, "Unknown quality");
                                    held(c).set(HMRegistries.QUALITY.get(), q.name());
                                    return ok(c, "Set quality " + q.name());
                                })))
                .then(Commands.literal("clear").executes(c -> {
                    held(c).remove(HMRegistries.QUALITY.get());
                    return ok(c, "Cleared quality");
                }));
    }

    private static final SuggestionProvider<CommandSourceStack> UNIQUES = (c, b) ->
            SharedSuggestionProvider.suggest(Grading.UNIQUES.all().stream().map(u -> shortId(u.name())), b);

    static LiteralArgumentBuilder<CommandSourceStack> unique() {
        return Commands.literal("unique").then(Commands.literal("set")
                .then(Commands.argument("unique", StringArgumentType.word()).suggests(UNIQUES).executes(c -> {
                    String arg = StringArgumentType.getString(c, "unique");
                    var u = Grading.UNIQUES.get(arg);
                    if (u == null) u = Grading.UNIQUES.get("hallmarked.unique." + arg);
                    Grade g = Grading.GRADES.get(Grading.UNIQUE_GRADE);
                    if (u == null || g == null) return fail(c, "Unknown unique (or the unique grade is disabled)");
                    ItemStack s = held(c);
                    s.set(HMRegistries.UNIQUE.get(), u.name());
                    s.set(HMRegistries.GRADE.get(), new GradeData(g.name(), List.of(), "", ""));
                    s.remove(HMRegistries.UNAPPRAISED.get());
                    Grading.refreshName(s);
                    return ok(c, "Made unique " + u.name());
                })));
    }

    static LiteralArgumentBuilder<CommandSourceStack> affix() {
        return Commands.literal("affix")
                .then(Commands.literal("add")
                        .then(Commands.argument("affix", StringArgumentType.word()).suggests(AFFIXES)
                                .executes(c -> addAffix(c, 1f))
                                .then(Commands.argument("roll", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(c -> addAffix(c, FloatArgumentType.getFloat(c, "roll"))))));
    }

    private static int addAffix(CommandContext<CommandSourceStack> c, float roll) throws CommandSyntaxException {
        ItemStack s = held(c);
        String arg = StringArgumentType.getString(c, "affix");
        Affix a = Grading.AFFIXES.get(arg);
        if (a == null) a = Grading.AFFIXES.get("hallmarked.affix." + arg);
        if (a == null) return fail(c, "Unknown affix");
        GradeData d = Grading.dataOf(s);
        if (d == null) {
            Grade common = Grading.findGrade("common");
            d = new GradeData(common == null ? "hallmarked.grade.common" : common.name(), List.of(), "", "");
        }
        List<GradeData.Rolled> list = new ArrayList<>(d.affixes());
        list.add(new GradeData.Rolled(a.name(), roll));
        s.set(HMRegistries.GRADE.get(), new GradeData(d.grade(), list, d.titleFirst(), d.titleSecond()));
        Grading.refreshName(s);
        return ok(c, "Added " + a.name() + " at roll " + roll);
    }

    private static ItemStack held(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException().getMainHandItem();
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
