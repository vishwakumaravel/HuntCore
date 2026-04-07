package com.huntcore.command;

import com.huntcore.game.GameManager;
import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class QuitCommand implements CommandExecutor {

    private final GameManager gameManager;
    private final PvpArenaManager pvpArenaManager;

    public QuitCommand(GameManager gameManager, PvpArenaManager pvpArenaManager) {
        this.gameManager = gameManager;
        this.pvpArenaManager = pvpArenaManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /quit.");
            return true;
        }

        if (pvpArenaManager.isPvpParticipant(player.getUniqueId())) {
            player.sendMessage("[HuntCore] Use /pvpleave to leave the PvP arena.");
            return true;
        }

        return gameManager.quitMatch(player);
    }
}
