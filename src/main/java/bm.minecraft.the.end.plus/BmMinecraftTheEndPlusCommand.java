package bm.minecraft.the.end.plus;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Handles End reset and administrative commands. */
public final class BmMinecraftTheEndPlusCommand implements CommandExecutor, TabCompleter {
    private static final String USE_PERMISSION = "bm-minecraft-the-end-plus.use";

    private final BmMinecraftTheEndPlusPlugin plugin;

    public BmMinecraftTheEndPlusCommand(BmMinecraftTheEndPlusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("the-end-reset")) {
            return runResetCommand(sender, args);
        }
        return runManagementCommand(sender, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("the-end-reset")) {
            return List.of();
        }
        if (args.length != 1) {
            return List.of();
        }
        String input = args[0].toLowerCase(Locale.ROOT);
        if (!plugin.canManage(sender)) {
            return List.of();
        }
        return List.of("0", "1", "info", "status", "reload", "set").stream()
                .filter(option -> option.startsWith(input))
                .toList();
    }

    private boolean runResetCommand(CommandSender sender, String[] args) {
        if (args.length != 0) {
            plugin.language().send(sender, "usage-reset");
            return true;
        }
        if (sender instanceof Player player) {
            if (!player.hasPermission(USE_PERMISSION)) {
                plugin.language().send(player, "no-reset-permission");
                return true;
            }
            if (!plugin.isFeatureEnabled() && !plugin.canManage(player)) {
                plugin.language().send(player, "feature-disabled");
                return true;
            }
            sendResetResult(player, player.getWorld());
            return true;
        }
        boolean found = false;
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }
            found = true;
            sendResetResult(sender, world);
        }
        if (!found) {
            plugin.language().send(sender, "no-end-world");
        }
        return true;
    }

    private void sendResetResult(CommandSender sender, World world) {
        Map<String, String> worldName = Map.of("world", world.getName());
        switch (plugin.reset(world, sender)) {
            case DISABLED -> plugin.language().send(sender, "feature-disabled");
            case NOT_IN_END -> plugin.language().send(sender, "not-in-end");
            case COOLDOWN -> plugin.language().send(sender, "end-cooldown", Map.of(
                    "minutes", Long.toString(plugin.getRemainingMinutes(world))));
            case IN_PROGRESS -> plugin.language().send(sender, "end-reset-in-progress", worldName);
            case FAILED -> plugin.language().send(sender, "end-reset-failed", worldName);
            case STARTED -> {
            }
        }
    }

    private boolean runManagementCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        if (!plugin.canManage(sender)) {
            plugin.language().send(sender, "no-permission");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "0" -> setEnabled(sender, false);
            case "1" -> setEnabled(sender, true);
            case "reload" -> {
                plugin.reloadPluginConfig();
                plugin.language().send(sender, "reloaded");
            }
            case "info" -> plugin.language().send(sender, "info", Map.of(
                    "version", plugin.getPluginMeta().getVersion()));
            case "status" -> plugin.language().send(sender, "status", Map.of(
                    "enabled", plugin.isFeatureEnabled() ? "ON" : "OFF",
                    "minutes", Integer.toString(plugin.getCooldownMinutes())));
            case "set" -> setCooldown(sender, args);
            default -> showHelp(sender);
        }
        return true;
    }

    private void showHelp(CommandSender sender) {
        for (String key : List.of("help-header", "help-reset", "help-toggle", "help-reload", "help-info", "help-status", "help-set", "help-footer")) {
            plugin.language().send(sender, key);
        }
    }

    private void setEnabled(CommandSender sender, boolean enabled) {
        plugin.getConfig().set("enabled", enabled);
        plugin.saveConfig();
        plugin.language().send(sender, enabled ? "enabled" : "disabled");
    }

    private void setCooldown(CommandSender sender, String[] args) {
        if (args.length != 2) {
            plugin.language().send(sender, "usage-set");
            return;
        }
        try {
            int minutes = Integer.parseInt(args[1]);
            if (minutes < 0 || minutes > 10_080) {
                throw new NumberFormatException();
            }
            plugin.setCooldownMinutes(minutes);
            plugin.language().send(sender, "cooldown-set", Map.of("minutes", Integer.toString(minutes)));
        } catch (NumberFormatException exception) {
            plugin.language().send(sender, "invalid-minutes");
        }
    }
}
