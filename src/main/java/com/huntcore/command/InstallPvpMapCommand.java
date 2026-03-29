package com.huntcore.command;

import com.huntcore.world.PvpMapInstaller;
import java.io.IOException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class InstallPvpMapCommand implements CommandExecutor {

    private static final String PERMISSION = "huntcore.admin";

    private final PvpMapInstaller pvpMapInstaller;

    public InstallPvpMapCommand(PvpMapInstaller pvpMapInstaller) {
        this.pvpMapInstaller = pvpMapInstaller;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("[HuntCore] You do not have permission to install PvP maps.");
            return true;
        }

        if (args.length > 2) {
            sender.sendMessage("[HuntCore] Usage: /installpvpmap [zip-path] [world-name]");
            return true;
        }

        String zipPath = args.length >= 1 ? args[0] : pvpMapInstaller.getConfiguredZipPath();
        String worldName = args.length >= 2 ? args[1] : pvpMapInstaller.getConfiguredWorldName();
        if (zipPath.isBlank()) {
            sender.sendMessage("[HuntCore] No PvP map zip path is configured. Use /installpvpmap <zip-path> [world-name].");
            return true;
        }

        sender.sendMessage("[HuntCore] Importing PvP arena world. This may take a moment...");
        try {
            PvpMapInstaller.InstallResult result = pvpMapInstaller.installFromZip(zipPath, worldName);
            sender.sendMessage("[HuntCore] PvP arena world imported as " + result.worldName() + ".");
            sender.sendMessage("[HuntCore] The PvP arena now points to that world's spawn.");
            sender.sendMessage("[HuntCore] Use /setpvpspawn if you want an exact combat spawn point.");
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            sender.sendMessage("[HuntCore] PvP map import failed: " + exception.getMessage());
        }
        return true;
    }
}
