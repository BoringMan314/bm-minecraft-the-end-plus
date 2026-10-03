package bm.minecraft.the.end.plus;

import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Resets only the central four regions and the Paper 26.3 dragon fight data. */
final class BmMinecraftTheEndPlusWorldReset implements Listener {

    private final BmMinecraftTheEndPlusPlugin plugin;
    private final Set<String> runningWorlds = new HashSet<>();
    private final Map<String, Snapshot> queuedWorlds = new HashMap<>();

    BmMinecraftTheEndPlusWorldReset(BmMinecraftTheEndPlusPlugin plugin) {
        this.plugin = plugin;
    }

    boolean isRunning(World world) {
        return runningWorlds.contains(world.getName());
    }

    void cancel() {
        // A scheduled reset has not touched files yet; put its saved world back.
        for (Snapshot snapshot : List.copyOf(queuedWorlds.values())) {
            try {
                World restored = snapshot.creator().createWorld();
                if (restored != null) {
                    snapshot.apply(restored);
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not reload interrupted reset: " + snapshot.name(), exception);
            }
        }
        queuedWorlds.clear();
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

        Snapshot snapshot;
        try {
            plugin.gateways().captureBeforeReset(world);
            snapshot = Snapshot.capture(world);
            plugin.language().send(sender, "end-reset-started", Map.of("world", snapshot.name()));
            if (!evacuate(world, fallback)) {
                runningWorlds.remove(world.getName());
                return BmMinecraftTheEndPlusPlugin.ResetResult.FAILED;
            }
            // No death animation or first-kill rewards during a command reset.
            for (EnderDragon dragon : List.copyOf(world.getEntitiesByClass(EnderDragon.class))) {
                try {
                    dragon.setHealth(0);
                } finally {
                    dragon.remove();
                }
            }
            // Saving is essential: the untouched outer islands must retain recent changes.
            if (!Bukkit.unloadWorld(world, true)) {
                runningWorlds.remove(snapshot.name());
                plugin.getLogger().warning(plugin.language().getConsole("world-unload-failed")
                        .replace("{world}", snapshot.name()));
                return BmMinecraftTheEndPlusPlugin.ResetResult.FAILED;
            }
        } catch (IOException | RuntimeException exception) {
            runningWorlds.remove(world.getName());
            plugin.getLogger().log(Level.SEVERE, "Could not prepare End reset: " + world.getName(), exception);
            return BmMinecraftTheEndPlusPlugin.ResetResult.FAILED;
        }

        queuedWorlds.put(snapshot.name(), snapshot);
        plugin.getServer().getScheduler().runTask(plugin, () -> resetFilesThenLoad(snapshot, sender));
        return BmMinecraftTheEndPlusPlugin.ResetResult.STARTED;
    }

    private void resetFilesThenLoad(Snapshot snapshot, CommandSender sender) {
        if (queuedWorlds.remove(snapshot.name()) == null) {
            return;
        }
        Path backup = plugin.getDataFolder().toPath().resolve("reset-backups")
                .resolve(System.currentTimeMillis() + "-" + UUID.randomUUID());
        BmMinecraftTheEndPlusResetFiles files = null;
        try {
            if (Bukkit.getWorld(snapshot.name()) != null) {
                throw new IOException("Another plugin reloaded the world before the reset");
            }
            files = new BmMinecraftTheEndPlusResetFiles(snapshot.folder(), backup);
            files.backupAndDelete();
            World created = snapshot.creator().createWorld();
            if (created == null || !created.getWorldPath().toRealPath().equals(snapshot.folder().toRealPath())
                    || created.getEnderDragonBattle() == null
                    || created.getEnderDragonBattle().hasBeenPreviouslyKilled()) {
                throw new IOException("The original End dimension did not load with a fresh dragon battle");
            }
            snapshot.apply(created);
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "End reset failed; backup: " + backup, exception);
            // Never restore files underneath a loaded world.
            try {
                World loaded = Bukkit.getWorld(snapshot.name());
                if (files == null && loaded != null) {
                    // Nothing was modified; leave another plugin's loaded world alone.
                    runningWorlds.remove(snapshot.name());
                    plugin.language().send(sender, "end-reset-failed", Map.of("world", snapshot.name()));
                    return;
                }
                if (loaded != null && !Bukkit.unloadWorld(loaded, false)) {
                    throw new IOException("Cannot unload world for rollback");
                }
                if (files != null) {
                    files.rollback();
                }
                World restored = snapshot.creator().createWorld();
                if (restored == null) {
                    throw new IOException("Cannot reload original End world");
                }
                snapshot.apply(restored);
                runningWorlds.remove(snapshot.name());
            } catch (IOException | RuntimeException rollbackFailure) {
                // Keep entry blocked; preserve the backup for operator recovery.
                plugin.getLogger().log(Level.SEVERE,
                        "End rollback failed. Keep players out and restore backup: " + backup, rollbackFailure);
            }
            plugin.language().send(sender, "end-reset-failed", Map.of("world", snapshot.name()));
            return;
        }
        try {
            files.status("complete");
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not mark reset backup complete: " + backup, exception);
        }
        runningWorlds.remove(snapshot.name());
        plugin.markReset(snapshot.name());
        plugin.language().send(sender, "end-reset", Map.of("world", snapshot.name()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && isBlocked(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (event.getTo() != null && isBlocked(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        World destination = event.getRespawnLocation().getWorld();
        if (isBlocked(destination)) {
            World fallback = fallbackWorld(destination);
            if (fallback != null) {
                event.setRespawnLocation(fallback.getSpawnLocation());
            }
        }
    }

    private boolean isBlocked(World world) {
        return world != null && isRunning(world);
    }

    private World fallbackWorld(World current) {
        World overworld = null;
        World other = null;
        for (World candidate : Bukkit.getWorlds()) {
            if (candidate.equals(current) || isRunning(candidate)) {
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

    private boolean evacuate(World world, World fallback) {
        Location fallbackSpawn = fallback.getSpawnLocation();
        for (Player player : List.copyOf(world.getPlayers())) {
            Location destination = player.getRespawnLocation();
            if (destination == null || destination.getWorld() == null
                    || destination.getWorld().equals(world) || isBlocked(destination.getWorld())) {
                destination = fallbackSpawn;
            }
            if (!player.teleport(destination)) {
                return false;
            }
        }
        return world.getPlayers().isEmpty();
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
            // Paper 26.1+ identifies loaded worlds by key; name and key are mutually exclusive.
            WorldCreator creator = WorldCreator.ofKey(world.getKey());
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
                    world.getWorldPath(),
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
