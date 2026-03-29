package com.huntcore.command;

import com.huntcore.game.GameManager;
import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ResetCommand implements CommandExecutor {

    private final GameManager gameManager;
    private final PvpArenaManager pvpArenaManager;

    public ResetCommand(GameManager gameManager, PvpArenaManager pvpArenaManager) {
        this.gameManager = gameManager;
        this.pvpArenaManager = pvpArenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (pvpArenaManager.isPvpParticipant(player.getUniqueId())) {
            return pvpArenaManager.leavePvpArena(player, true);
        }

        return gameManager.resetPlayerToLobby(player);
    }
}
