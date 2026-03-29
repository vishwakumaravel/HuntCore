package com.huntcore.command;

import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class PvpCommand implements CommandExecutor {

    private final PvpArenaManager pvpArenaManager;

    public PvpCommand(PvpArenaManager pvpArenaManager) {
        this.pvpArenaManager = pvpArenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /pvp.");
            return true;
        }

        return pvpArenaManager.enterPvpArena(player);
    }
}
