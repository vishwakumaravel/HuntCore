package com.huntcore.command;

import com.huntcore.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class PauseCommand implements CommandExecutor {

    private final GameManager gameManager;

    public PauseCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return gameManager.pauseMatch(sender);
    }
}
