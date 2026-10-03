package bm.minecraft.the.end.plus;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Unloads an End world, deletes its region data, and recreates it from the same seed. */
final class BmMinecraftTheEndPlusWorldReset {
    private static final int DELETE_RETRY_TICKS = 20;

    private final BmMinecraftTheEndPlusPlugin plugin;
    private final Set<String> runningWorlds = new HashSet<>();

    BmMinecraftTheEndPlusWorldReset(BmMinecraftTheEndPlusPlugin plugin) {
        this.plugin = plugin;
    }

    boolean isRunning(World world) {
        return runningWorlds.contains(world.getName());
    }

    void cancel() {
        runningWorlds.clear();
    }

    BmMinecraftTheEndPlusPlugin.ResetResult start(World world, CommandSender sender) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return BmMinecraftTheEndPlusPlugin.ResetResult.NOT_IN_END;
        }
        if (!runningWorlds.add(world.getName())) {
            return BmMinecraftTheEndPlusPlugin.ResetResult.IN_PROGRESS;
        }

        World fallback = fallbackWorld(world);
        if (fallback == null) {
            runningWorlds.remove(world.getName());
            return BmMinecraftTheEndPlusPlugin.ResetResult.FAILED;
        }

        Snapshot snapshot = Snapshot.capture(world);
        plugin.language().send(sender, "end-reset-started", Map.of("world", snapshot.name()));
        evacuate(world, fallback);
        if (!Bukkit.unloadWorld(world, false)) {
            runningWorlds.remove(snapshot.name());
            plugin.getLogger().warning(plugin.language().getConsole("world-unload-failed")
                    .replace("{world}", snapshot.name()));
            return BmMinecraftTheEndPlusPlugin.ResetResult.FAILED;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> deleteThenCreate(snapshot, sender, 0));
        return BmMinecraftTheEndPlusPlugin.ResetResult.STARTED;
    }

    private void deleteThenCreate(Snapshot snapshot, CommandSender sender, int attempt) {
        deleteRecursively(snapshot.folder());
        if (Files.exists(snapshot.folder()) && attempt < DELETE_RETRY_TICKS) {
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> deleteThenCreate(snapshot, sender, attempt + 1), 1L);
            return;
        }

        World created = snapshot.creator().createWorld();
        runningWorlds.remove(snapshot.name());
        if (created == null) {
            plugin.getLogger().severe(plugin.language().getConsole("world-create-failed")
                    .replace("{world}", snapshot.name()));
            plugin.language().send(sender, "end-reset-failed", Map.of("world", snapshot.name()));
            return;
        }
        snapshot.apply(created);
        plugin.markReset(snapshot.name());
        plugin.language().send(sender, "end-reset", Map.of("world", snapshot.name()));
    }

    private World fallbackWorld(World current) {
        World overworld = null;
        World other = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.equals(current)) {
                continue;
            }
            if (candidate.getEnvironment() == World.Environment.NORMAL && overworld == null) {
                overworld = candidate;
            } else if (other == null) {
                other = candidate;
            }
        }
        return overworld != null ? overworld : other;
    }

    private void evacuate(World world, World fallback) {
        Location fallbackSpawn = fallback.getSpawnLocation();
        for (Player player : List.copyOf(world.getPlayers())) {
            Location destination = player.getRespawnLocation();
            if (destination == null || destination.getWorld() == null || destination.getWorld().equals(world)) {
                destination = fallbackSpawn;
            }
            player.teleport(destination);
        }
    }

    private void deleteRecursively(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Region files may still be briefly locked after unload.
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning(plugin.language().getConsole("world-delete-failed")
                    .replace("{path}", folder.toString())
                    .replace("{error}", exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage()));
        }
    }

    private record Snapshot(
            String name,
            Path folder,
            WorldCreator creator,
            Difficulty difficulty,
            boolean pvp,
            boolean spawnMonsters,
            boolean spawnAnimals,
            boolean hardcore,
            List<RuleValue> gameRules) {

        static Snapshot capture(World world) {
            WorldCreator creator = new WorldCreator(world.getName());
            creator.copy(world);
            creator.seed(world.getSeed());
            creator.environment(World.Environment.THE_END);
            creator.hardcore(world.isHardcore());
            List<RuleValue> gameRules = new ArrayList<>();
            for (String name : world.getGameRules()) {
                GameRule<?> rule = GameRule.getByName(name);
                if (rule == null) {
                    continue;
                }
                Object value;
                try {
                    value = world.getGameRuleValue(rule);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                if (value != null) {
                    gameRules.add(new RuleValue(rule, value));
                }
            }
            return new Snapshot(
                    world.getName(),
                    world.getWorldFolder().toPath(),
                    creator,
                    world.getDifficulty(),
                    world.getPVP(),
                    world.getAllowMonsters(),
                    world.getAllowAnimals(),
                    world.isHardcore(),
                    List.copyOf(gameRules));
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        void apply(World world) {
            world.setDifficulty(difficulty);
            world.setPVP(pvp);
            world.setSpawnFlags(spawnMonsters, spawnAnimals);
            world.setHardcore(hardcore);
            for (RuleValue rule : gameRules) {
                try {
                    world.setGameRule((GameRule) rule.rule(), rule.value());
                } catch (IllegalArgumentException ignored) {
                    // Some game rules are not valid in The End.
                }
            }
        }
    }

    private record RuleValue(GameRule<?> rule, Object value) {
    }
}
