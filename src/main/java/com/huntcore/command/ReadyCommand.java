package com.huntcore.command;

import com.huntcore.game.GameManager;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ReadyCommand implements CommandExecutor {

    private final PlayerRegistry playerRegistry;
    private final GameManager gameManager;
    private final PvpArenaManager pvpArenaManager;
    private final boolean readyValue;

    public ReadyCommand(PlayerRegistry playerRegistry, GameManager gameManager, PvpArenaManager pvpArenaManager, boolean readyValue) {
        this.playerRegistry = playerRegistry;
        this.gameManager = gameManager;
        this.pvpArenaManager = pvpArenaManager;
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

        if (pvpArenaManager.isPvpParticipant(player.getUniqueId())) {
            player.sendMessage("[HuntCore] Leave the PvP arena with /pvpleave before changing ready status.");
            return true;
        }

        playerRegistry.registerPlayer(player);
        PlayerRole role = playerRegistry.getRole(player.getUniqueId());
        if (role == PlayerRole.SPECTATOR) {
            player.sendMessage("[HuntCore] Spectators do not need /ready. Use /spectate again if you want to rejoin the queue.");
            return true;
        }

        if (readyValue && role == PlayerRole.NONE) {
            player.sendMessage("[HuntCore] Pick /runner or /hunter before using /ready.");
            return true;
        }

        if (playerRegistry.isReady(player.getUniqueId()) == readyValue) {
            player.sendMessage("[HuntCore] You are already marked as " + (readyValue ? "ready." : "not ready."));
            return true;
        }

        playerRegistry.setReady(player.getUniqueId(), readyValue);
        player.sendMessage("[HuntCore] You are now " + (readyValue ? "ready." : "not ready."));
        player.sendMessage("[HuntCore] " + gameManager.getLobbyStatusSummary());
        gameManager.handleLobbyStateChange();
        return true;
    }
}
