package bm.minecraft.the.end.plus;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Resets The End and restores the first-kill portal after the dragon dies. */
public final class BmMinecraftTheEndPlusPlugin extends JavaPlugin implements Listener {
    private static final int MAX_COOLDOWN_MINUTES = 10_080;
    private static final int DRAGON_EGG_OFFSET_Y = 4;
    private static final int MAX_RESTORE_WAIT_TICKS = 600;
    private static final int EXIT_PORTAL_RADIUS = 4;
    private static final int EXIT_PORTAL_CLEAR_HEIGHT = 32;
    private static final double EXIT_PORTAL_INNER_DISTANCE_SQUARED = 2.5D * 2.5D;

    private final Map<String, Long> lastResetUses = new HashMap<>();
    private final Set<UUID> pendingRestores = new HashSet<>();
    private final BmMinecraftTheEndPlusWorldReset worldReset = new BmMinecraftTheEndPlusWorldReset(this);
    private File stateFile;
    private BmMinecraftTheEndPlusLanguageManager languageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new BmMinecraftTheEndPlusLanguageManager(this);
        languageManager.reload();
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();

        registerCommand("the-end-reset");
        registerCommand("bm-minecraft-the-end-plus");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(languageManager.getConsole("enabled").replace("{version}", getPluginMeta().getVersion()));
    }

    @Override
    public void onDisable() {
        worldReset.cancel();
        saveState();
        if (languageManager != null) {
            getLogger().info(languageManager.getConsole("disabled"));
        }
    }

    public BmMinecraftTheEndPlusLanguageManager language() {
        return languageManager;
    }

    public boolean isFeatureEnabled() {
        return getConfig().getBoolean("enabled", true);
    }

    public int getCooldownMinutes() {
        return Math.clamp(getConfig().getInt("reset-cooldown-minutes", 30), 0, MAX_COOLDOWN_MINUTES);
    }

    public void setCooldownMinutes(int minutes) {
        getConfig().set("reset-cooldown-minutes", Math.clamp(minutes, 0, MAX_COOLDOWN_MINUTES));
        saveConfig();
    }

    public void reloadPluginConfig() {
        reloadConfig();
        languageManager.reload();
    }

    public boolean canManage(CommandSender sender) {
        return !(sender instanceof Player)
                || !getConfig().getBoolean("admin-require-op", true)
                || sender.isOp()
                || sender.hasPermission("bm-minecraft-the-end-plus.admin");
    }

    public long getRemainingMinutes(World world) {
        Long lastResetUse = lastResetUses.get(world.getName());
        if (lastResetUse == null) {
            return 0;
        }
        long remainingMillis = (long) getCooldownMinutes() * 60_000L - (System.currentTimeMillis() - lastResetUse);
        return remainingMillis <= 0 ? 0 : Math.ceilDiv(remainingMillis, 60_000L);
    }

    public void markReset(String worldName) {
        lastResetUses.put(worldName, System.currentTimeMillis());
        saveState();
    }

    public ResetResult reset(World world, CommandSender sender) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return ResetResult.NOT_IN_END;
        }
        if (!isFeatureEnabled() && !canManage(sender)) {
            return ResetResult.DISABLED;
        }
        if (!canManage(sender) && getRemainingMinutes(world) > 0) {
            return ResetResult.COOLDOWN;
        }
        if (worldReset.isRunning(world)) {
            return ResetResult.IN_PROGRESS;
        }
        return worldReset.start(world, sender);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDragonPhaseChange(EnderDragonChangePhaseEvent event) {
        if (event.getCurrentPhase() != EnderDragon.Phase.DYING) {
            return;
        }
        scheduleFirstKillRestore(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        scheduleFirstKillRestore(dragon);
    }

    public boolean isRestoreFirstExperienceEnabled() {
        return getConfig().getBoolean("restore-first-experience", true);
    }

    private void scheduleFirstKillRestore(EnderDragon dragon) {
        if (!isFeatureEnabled() || !isRestoreFirstExperienceEnabled()) {
            return;
        }
        World world = dragon.getWorld();
        if (world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        UUID worldId = world.getUID();
        if (!pendingRestores.add(worldId)) {
            return;
        }
        final int[] waited = {0};
        getServer().getScheduler().runTaskTimer(this, task -> {
            waited[0]++;
            if (dragon.isValid() && waited[0] < MAX_RESTORE_WAIT_TICKS) {
                return;
            }
            task.cancel();
            pendingRestores.remove(worldId);
            restoreFirstKillWorld(world);
        }, 1L, 1L);
    }

    private void restoreFirstKillWorld(World world) {
        DragonBattle dragonBattle = world.getEnderDragonBattle();
        if (dragonBattle == null) {
            return;
        }
        dragonBattle.setPreviouslyKilled(true);
        dragonBattle.generateEndPortal(true);
        Location portalLocation = dragonBattle.getEndPortalLocation();
        if (portalLocation == null) {
            portalLocation = new Location(world, 0.5D, world.getHighestBlockYAt(0, 0, HeightMap.MOTION_BLOCKING), 0.5D);
        }
        restoreActiveExitPortal(portalLocation);
        dragonBattle.spawnNewGateway();
    }

    private void restoreActiveExitPortal(Location origin) {
        World world = origin.getWorld();
        if (world == null) {
            return;
        }
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        for (int y = -1; y <= EXIT_PORTAL_CLEAR_HEIGHT; y++) {
            int blockY = originY + y;
            if (blockY < minY || blockY > maxY) {
                continue;
            }
            for (int x = -EXIT_PORTAL_RADIUS; x <= EXIT_PORTAL_RADIUS; x++) {
                for (int z = -EXIT_PORTAL_RADIUS; z <= EXIT_PORTAL_RADIUS; z++) {
                    boolean inner = (x * x) + (y * y) + (z * z) < EXIT_PORTAL_INNER_DISTANCE_SQUARED;
                    if (!inner && blockY > originY) {
                        continue;
                    }
                    Block block = world.getBlockAt(originX + x, blockY, originZ + z);
                    if (blockY < originY) {
                        if (inner) {
                            block.setType(Material.BEDROCK, false);
                        }
                    } else if (blockY > originY) {
                        block.setType(Material.AIR, false);
                    } else if (inner) {
                        block.setType(Material.END_PORTAL, false);
                    } else {
                        block.setType(Material.BEDROCK, false);
                    }
                }
            }
        }
        for (int pillar = 0; pillar < DRAGON_EGG_OFFSET_Y; pillar++) {
            world.getBlockAt(originX, originY + pillar, originZ).setType(Material.BEDROCK, false);
        }
        setWallTorch(world, originX, originY + 2, originZ - 1, BlockFace.NORTH);
        setWallTorch(world, originX, originY + 2, originZ + 1, BlockFace.SOUTH);
        setWallTorch(world, originX - 1, originY + 2, originZ, BlockFace.WEST);
        setWallTorch(world, originX + 1, originY + 2, originZ, BlockFace.EAST);
        world.getBlockAt(originX, originY + DRAGON_EGG_OFFSET_Y, originZ).setType(Material.DRAGON_EGG, false);
    }

    private void setWallTorch(World world, int x, int y, int z, BlockFace facing) {
        Block block = world.getBlockAt(x, y, z);
        if (!(Material.WALL_TORCH.createBlockData() instanceof Directional torch)) {
            block.setType(Material.TORCH, false);
            return;
        }
        torch.setFacing(facing);
        block.setBlockData(torch, false);
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException(languageManager.getConsole("missing-command")
                    .replace("{command}", name));
        }
        BmMinecraftTheEndPlusCommand executor = new BmMinecraftTheEndPlusCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void loadState() {
        lastResetUses.clear();
        if (!stateFile.isFile()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        ConfigurationSection section = state.getConfigurationSection("reset-uses");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            lastResetUses.put(key, section.getLong(key));
        }
    }

    private void saveState() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe(languageManager.getConsole("data-folder-create-failed"));
            return;
        }
        YamlConfiguration state = new YamlConfiguration();
        for (Map.Entry<String, Long> entry : lastResetUses.entrySet()) {
            state.set("reset-uses." + entry.getKey(), entry.getValue());
        }
        try {
            state.save(stateFile);
        } catch (IOException exception) {
            getLogger().severe(languageManager.getConsole("state-save-failed")
                    .replace("{error}", exception.getMessage()));
        }
    }

    public enum ResetResult {
        DISABLED,
        NOT_IN_END,
        COOLDOWN,
        IN_PROGRESS,
        FAILED,
        STARTED
    }
}
