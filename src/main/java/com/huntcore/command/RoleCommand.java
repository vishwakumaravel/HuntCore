package com.huntcore.command;

import com.huntcore.game.GameManager;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RoleCommand implements CommandExecutor {

    private final PlayerRegistry playerRegistry;
    private final GameManager gameManager;
    private final PlayerRole role;

    public RoleCommand(PlayerRegistry playerRegistry, GameManager gameManager, PlayerRole role) {
        this.playerRegistry = playerRegistry;
        this.gameManager = gameManager;
        this.role = role;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (gameManager.isRoleSelectionLocked(player)) {
            player.sendMessage("[HuntCore] You are already part of the active round.");
            return true;
        }

        if (!gameManager.isLobbyEditable()) {
            player.sendMessage("[HuntCore] Role selection is only available in the lobby.");
            return true;
        }

        if (role == PlayerRole.RUNNER && !gameManager.canUseRunnerRole(player)) {
            player.sendMessage("[HuntCore] v1 only supports one runner at a time.");
            return true;
        }

        if (playerRegistry.getRole(player.getUniqueId()) == role) {
            player.sendMessage("[HuntCore] You are already set as " + role.name().toLowerCase() + ".");
            return true;
        }

        playerRegistry.registerPlayer(player);
        playerRegistry.setRole(player.getUniqueId(), role);
        player.sendMessage("[HuntCore] Role set to " + role.name().toLowerCase() + ". Use /ready when you are set.");
        player.sendMessage("[HuntCore] " + gameManager.getLobbyStatusSummary());
        gameManager.handleLobbyStateChange();
        return true;
    }
}
