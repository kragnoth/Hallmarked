package com.kragnoth.hallmarked.command;

import com.kragnoth.hallmarked.gamble.MountebankTrades;
import com.kragnoth.hallmarked.gamble.Parcel;
import com.kragnoth.hallmarked.sets.GearSet;
import com.kragnoth.hallmarked.sets.Sets;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** {@code /hallmarked sets give <set> [piece]} and {@code /hallmarked parcel give <parcel> [count]}, for testing. */
final class SetCommands {
    private SetCommands() {}

    private static String shortId(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static final SuggestionProvider<CommandSourceStack> SETS = (c, b) ->
            SharedSuggestionProvider.suggest(Sets.STORE.all().stream().map(s -> shortId(s.name())), b);
    private static final SuggestionProvider<CommandSourceStack> PIECES = (c, b) -> {
        GearSet set = find(StringArgumentType.getString(c, "set"));
        return set == null ? b.buildFuture() : SharedSuggestionProvider.suggest(set.pieces().stream().map(GearSet.Piece::key), b);
    };
    private static final SuggestionProvider<CommandSourceStack> PARCELS = (c, b) ->
            SharedSuggestionProvider.suggest(Parcel.STORE.all().stream().map(p -> shortId(p.name())), b);

    private static GearSet find(String name) {
        GearSet s = Sets.STORE.get(name);
        return s != null ? s : Sets.STORE.get("hallmarked.set." + name);
    }

    static LiteralArgumentBuilder<CommandSourceStack> sets() {
        return Commands.literal("sets").then(Commands.literal("give")
                .then(Commands.argument("set", StringArgumentType.word()).suggests(SETS)
                        .executes(c -> give(c, StringArgumentType.getString(c, "set"), null))
                        .then(Commands.argument("piece", StringArgumentType.word()).suggests(PIECES)
                                .executes(c -> give(c, StringArgumentType.getString(c, "set"), StringArgumentType.getString(c, "piece"))))));
    }

    private static int give(CommandContext<CommandSourceStack> c, String setName, String pieceKey) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        GearSet set = find(setName);
        if (set == null) {
            c.getSource().sendFailure(Component.literal("Unknown set " + setName));
            return 0;
        }
        List<GearSet.Piece> pieces = pieceKey == null ? set.pieces()
                : set.pieces().stream().filter(x -> x.key().equals(pieceKey)).toList();
        if (pieces.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Unknown piece " + pieceKey));
            return 0;
        }
        int n = 0;
        for (GearSet.Piece piece : pieces) {
            ItemStack s = Sets.create(set, piece, p.serverLevel().registryAccess());
            if (s == null) continue;
            if (!p.getInventory().add(s)) p.drop(s, false);
            n++;
        }
        int given = n;
        c.getSource().sendSuccess(() -> Component.literal("Gave " + given + " piece(s) of ").append(Component.translatable(set.name())), false);
        return n;
    }

    static LiteralArgumentBuilder<CommandSourceStack> parcels() {
        return Commands.literal("parcel").then(Commands.literal("give")
                .then(Commands.argument("parcel", StringArgumentType.word()).suggests(PARCELS)
                        .executes(c -> giveParcel(c, StringArgumentType.getString(c, "parcel"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(c -> giveParcel(c, StringArgumentType.getString(c, "parcel"), IntegerArgumentType.getInteger(c, "count"))))));
    }

    private static int giveParcel(CommandContext<CommandSourceStack> c, String name, int count) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Parcel parcel = Parcel.STORE.get(name);
        if (parcel == null) parcel = Parcel.STORE.get("hallmarked.parcel." + name);
        if (parcel == null) {
            c.getSource().sendFailure(Component.literal("Unknown parcel " + name));
            return 0;
        }
        ItemStack s = MountebankTrades.parcelStack(parcel);
        s.setCount(count);
        if (!p.getInventory().add(s)) p.drop(s, false);
        Parcel given = parcel;
        c.getSource().sendSuccess(() -> Component.literal("Gave " + count + " ").append(Component.translatable(given.name())), false);
        return count;
    }
}
