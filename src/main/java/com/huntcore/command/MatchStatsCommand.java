package com.huntcore.command;

import com.huntcore.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class MatchStatsCommand implements CommandExecutor {

    private final GameManager gameManager;

    public MatchStatsCommand(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int limit = 5;
        if (args.length >= 1) {
            try {
                limit = Math.max(1, Math.min(10, Integer.parseInt(args[0])));
            } catch (NumberFormatException exception) {
                sender.sendMessage("[HuntCore] Usage: /matchstats [1-10]");
                return true;
            }
        }

        for (String line : gameManager.getMatchHistoryLines(limit)) {
            sender.sendMessage(line);
        }
        return true;
    }
}
