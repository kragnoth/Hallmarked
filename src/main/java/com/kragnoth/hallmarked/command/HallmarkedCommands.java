package com.kragnoth.hallmarked.command;

import com.kragnoth.hallmarked.Hallmarked;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * {@code /hallmarked defaults rebuild <world|kubejs>}: writes Hallmarked's built-in data out as editable
 * files, after backing up whatever is already there.
 *
 * <ul>
 *   <li>{@code world}  -> {@code <world>/datapacks/hallmarked_defaults/data/hallmarked/...} (with pack.mcmeta)</li>
 *   <li>{@code kubejs} -> {@code <game dir>/kubejs/data/hallmarked/...}</li>
 * </ul>
 * Existing files in those folders are copied to {@code <game dir>/hallmarked_backups/<timestamp>-<target>/}
 * before being replaced. Only Hallmarked's own folders are touched; other namespaces are left alone.
 */
public final class HallmarkedCommands {
    private HallmarkedCommands() {}

    /** Folders under data/hallmarked/ that make up the editable defaults. */
    private static final List<String> FOLDERS = List.of(
            "qualities", "grades", "affixes", "fusions", "titles", "loot_modifiers", "recipe/reforge", "recipe/inlay", "inlays", "tags/item");

    private enum Target { WORLD, KUBEJS }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Hallmarked.MODID)
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("defaults")
                        .then(Commands.literal("rebuild")
                                .then(Commands.literal("world").executes(c -> rebuild(c, Target.WORLD)))
                                .then(Commands.literal("kubejs").executes(c -> rebuild(c, Target.KUBEJS)))))
                .then(ItemCommands.inspect())
                .then(ItemCommands.grade())
                .then(ItemCommands.quality())
                .then(ItemCommands.affix()));
    }

    private static int rebuild(CommandContext<CommandSourceStack> ctx, Target target) {
        CommandSourceStack src = ctx.getSource();
        Path gameDir = FMLPaths.GAMEDIR.get();
        Path packRoot = target == Target.WORLD
                ? src.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("hallmarked_defaults")
                : gameDir.resolve("kubejs");
        Path nsRoot = packRoot.resolve("data").resolve(Hallmarked.MODID);

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupRoot = gameDir.resolve("hallmarked_backups")
                .resolve(stamp + "-" + target.name().toLowerCase())
                .resolve("data").resolve(Hallmarked.MODID);

        Path jarData;
        try {
            jarData = ModList.get().getModFileById(Hallmarked.MODID).getFile().findResource("data", Hallmarked.MODID);
        } catch (Exception e) {
            src.sendFailure(Component.literal("Hallmarked: could not locate the mod's built-in data: " + e.getMessage()));
            return 0;
        }

        int backedUp = 0, written = 0;
        try {
            // 1. Back up and clear what's there now.
            for (String folder : FOLDERS) {
                Path current = nsRoot.resolve(folder);
                if (!Files.isDirectory(current)) continue;
                backedUp += copyTree(current, backupRoot.resolve(folder));
                deleteTree(current);
            }
            // 2. Write the built-in defaults.
            for (String folder : FOLDERS) {
                Path from = jarData.resolve(folder);
                if (Files.isDirectory(from)) written += copyTree(from, nsRoot.resolve(folder));
            }
            // 3. A world datapack needs a pack.mcmeta to be picked up.
            if (target == Target.WORLD) {
                Path meta = packRoot.resolve("pack.mcmeta");
                if (!Files.exists(meta)) {
                    Files.createDirectories(packRoot);
                    Files.writeString(meta, """
                            {
                              "pack": {
                                "pack_format": 48,
                                "description": "Hallmarked defaults (editable)"
                              }
                            }
                            """, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            Hallmarked.LOG.error("Rebuilding Hallmarked defaults failed", e);
            src.sendFailure(Component.literal("Hallmarked: rebuild failed (" + e.getMessage() + "). "
                    + (backedUp > 0 ? "Your previous files are backed up in " + gameDir.relativize(backupRoot.getParent().getParent()) : "")));
            return 0;
        }

        final int w = written, b = backedUp;
        src.sendSuccess(() -> Component.literal("Hallmarked: wrote " + w + " default files to "
                + gameDir.relativize(nsRoot) + "."), true);
        if (b > 0) {
            src.sendSuccess(() -> Component.literal("Backed up " + b + " existing files to "
                    + gameDir.relativize(backupRoot.getParent().getParent()) + "."), true);
        }
        src.sendSuccess(() -> Component.literal("Run /reload to apply."), true);
        return w;
    }

    /** Copies every regular file under {@code from} to the same relative path under {@code to}. Works across filesystems (jar -> disk). */
    private static int copyTree(Path from, Path to) throws IOException {
        int n = 0;
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(p)) continue;
                Path dest = to;
                for (Path part : from.relativize(p)) dest = dest.resolve(part.toString());
                Files.createDirectories(dest.getParent());
                Files.write(dest, Files.readAllBytes(p));
                n++;
            }
        }
        return n;
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }
}
