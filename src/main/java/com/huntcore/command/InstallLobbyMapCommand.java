package com.huntcore.command;

import com.huntcore.world.LobbyMapInstaller;
import java.io.IOException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class InstallLobbyMapCommand implements CommandExecutor {

    private static final String PERMISSION = "huntcore.admin";

    private final LobbyMapInstaller lobbyMapInstaller;

    public InstallLobbyMapCommand(LobbyMapInstaller lobbyMapInstaller) {
        this.lobbyMapInstaller = lobbyMapInstaller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("[HuntCore] You do not have permission to install lobby maps.");
            return true;
        }

        if (args.length > 2) {
            sender.sendMessage("[HuntCore] Usage: /installlobbymap [zip-path] [world-name]");
            return true;
        }

        String zipPath = args.length >= 1 ? args[0] : lobbyMapInstaller.getConfiguredZipPath();
        String worldName = args.length >= 2 ? args[1] : lobbyMapInstaller.getConfiguredWorldName();
        if (zipPath.isBlank()) {
            sender.sendMessage("[HuntCore] No lobby map zip path is configured. Use /installlobbymap <zip-path> [world-name].");
            return true;
        }

        sender.sendMessage("[HuntCore] Importing lobby world. This may take a moment...");
        try {
            LobbyMapInstaller.InstallResult result = lobbyMapInstaller.installFromZip(zipPath, worldName);
            sender.sendMessage("[HuntCore] Lobby world imported as " + result.worldName() + ".");
            sender.sendMessage("[HuntCore] The waiting lobby now points to that world's spawn.");
            sender.sendMessage("[HuntCore] Use /setlobby if you want an exact spawn point on the parkour map.");
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage("[HuntCore] Lobby map import failed: " + exception.getMessage());
        }
        return true;
    }
}
