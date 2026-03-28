package com.huntcore.command;

import com.huntcore.game.GameManager;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ReadyCommand implements CommandExecutor {

    private final PlayerRegistry playerRegistry;
    private final GameManager gameManager;
    private final boolean readyValue;

    public ReadyCommand(PlayerRegistry playerRegistry, GameManager gameManager, boolean readyValue) {
        this.playerRegistry = playerRegistry;
        this.gameManager = gameManager;
        this.readyValue = readyValue;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!gameManager.isLobbyEditable()) {
            player.sendMessage("[HuntCore] Ready status can only be changed in the lobby.");
            return true;
        }

        playerRegistry.registerPlayer(player);
        if (readyValue && playerRegistry.getRole(player.getUniqueId()) == PlayerRole.NONE) {
            player.sendMessage("[HuntCore] Pick /runner or /hunter before using /ready.");
            return true;
        }

        if (playerRegistry.isReady(player.getUniqueId()) == readyValue) {
            player.sendMessage("[HuntCore] Your ready state is already " + readyValue + ".");
            return true;
        }

        playerRegistry.setReady(player.getUniqueId(), readyValue);
        player.sendMessage("[HuntCore] Ready state updated: " + readyValue);
        gameManager.handleLobbyStateChange();
        return true;
    }
}

