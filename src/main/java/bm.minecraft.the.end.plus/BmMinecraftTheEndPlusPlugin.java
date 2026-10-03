package bm.minecraft.the.end.plus;

import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Resets the central End and restores vanilla first-kill rewards for respawned dragons. */
public final class BmMinecraftTheEndPlusPlugin extends JavaPlugin implements Listener {
    private static final int MAX_COOLDOWN_MINUTES = 10_080;
    private final Map<String, Long> lastResetUses = new HashMap<>();
    private final BmMinecraftTheEndPlusWorldReset worldReset = new BmMinecraftTheEndPlusWorldReset(this);
    private File stateFile;
    private BmMinecraftTheEndPlusLanguageManager languageManager;
    private BmMinecraftTheEndPlusGateways gateways;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        languageManager = new BmMinecraftTheEndPlusLanguageManager(this);
        languageManager.reload();
        stateFile = new File(getDataFolder(), "state.yml");
        loadState();
        try {
            gateways = new BmMinecraftTheEndPlusGateways(this);
        } catch (IOException exception) {
            getLogger().log(java.util.logging.Level.SEVERE, "Could not load saved End gateway links", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        registerCommand("the-end-reset");
        registerCommand("bm-minecraft-the-end-plus");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(worldReset, this);
        getServer().getPluginManager().registerEvents(gateways, this);
        getLogger().info(languageManager.getConsole("enabled").replace("{version}", getPluginMeta().getVersion()));
    }

    @Override
    public void onDisable() {
        worldReset.cancel();
        if (gateways != null) {
            try {
                gateways.save();
            } catch (IOException exception) {
                getLogger().log(java.util.logging.Level.SEVERE, "Could not save End gateway links", exception);
            }
        }
        saveState();
        if (languageManager != null) {
            getLogger().info(languageManager.getConsole("disabled"));
        }
    }

    public BmMinecraftTheEndPlusLanguageManager language() {
        return languageManager;
    }

    boolean isResetRunning(World world) { return worldReset.isRunning(world); }

    BmMinecraftTheEndPlusGateways gateways() { return gateways; }

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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        World world = dragon.getWorld();
        if (worldReset.isRunning(world)) {
            event.setDroppedExp(0);
            event.getDrops().clear();
            return;
        }
        if (!isFeatureEnabled() || !isRestoreFirstExperienceEnabled()
                || world.getEnvironment() != World.Environment.THE_END) {
            return;
        }
        DragonBattle battle = dragon.getDragonBattle();
        if (battle == null || battle.getEnderDragon() == null
                || !battle.getEnderDragon().getUniqueId().equals(dragon.getUniqueId())
                || !battle.hasBeenPreviouslyKilled()) {
            return; // The genuine first kill already has all vanilla first-kill effects.
        }
        // Paper fires this event before the death animation/final battle settlement.
        // Vanilla then opens the portal, places the egg and generates ONE gateway.
        battle.setPreviouslyKilled(false);
        event.setDroppedExp(Boolean.TRUE.equals(world.getGameRuleValue(GameRules.MOB_DROPS)) ? 12_000 : 0);
    }

    public boolean isRestoreFirstExperienceEnabled() {
        return getConfig().getBoolean("restore-first-experience", true);
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
