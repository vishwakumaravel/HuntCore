package com.huntcore.command;

import com.huntcore.config.PluginConfig;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SetPvpSpawnCommand implements CommandExecutor {

    private static final String PERMISSION = "huntcore.admin";

    private final PluginConfig pluginConfig;

    public SetPvpSpawnCommand(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("[HuntCore] You do not have permission to change PvP arena settings.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /setpvpspawn.");
            return true;
        }

        Location location = player.getLocation().clone();
        pluginConfig.setPvpSpawn(location);
        player.sendMessage(
            "[HuntCore] PvP spawn updated to "
                + location.getWorld().getName()
                + " at "
                + String.format("%.1f, %.1f, %.1f", location.getX(), location.getY(), location.getZ())
                + "."
        );
        return true;
    }
}
