package com.huntcore.command;

import com.huntcore.config.PluginConfig;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class HunterKeepInventoryCommand implements CommandExecutor {

    private static final String PERMISSION = "huntcore.admin";

    private final PluginConfig pluginConfig;

    public HunterKeepInventoryCommand(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("[HuntCore] You do not have permission to change match settings.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        String mode = args[0].toLowerCase();
        return switch (mode) {
            case "on", "true", "enable", "enabled" -> {
                setKeepInventory(sender, true);
                yield true;
            }
            case "off", "false", "disable", "disabled" -> {
                setKeepInventory(sender, false);
                yield true;
            }
            case "toggle" -> {
                boolean updatedValue = !pluginConfig.shouldHuntersKeepInventory();
                setKeepInventory(sender, updatedValue);
                yield true;
            }
            default -> {
                sender.sendMessage("[HuntCore] Usage: /hunterkeepinventory <on|off|toggle|status>");
                yield true;
            }
        };
    }

    private void setKeepInventory(CommandSender sender, boolean keepInventory) {
        pluginConfig.setHuntersKeepInventory(keepInventory);
        sender.sendMessage(
            "[HuntCore] Hunter keep inventory is now "
                + (keepInventory ? "enabled" : "disabled")
                + ". Future hunter deaths will "
                + (keepInventory ? "keep items and XP." : "drop items and XP normally.")
        );
    }

    private void sendStatus(CommandSender sender) {
        boolean keepInventory = pluginConfig.shouldHuntersKeepInventory();
        sender.sendMessage(
            "[HuntCore] Hunter keep inventory is currently "
                + (keepInventory ? "enabled" : "disabled")
                + "."
        );
    }
}
