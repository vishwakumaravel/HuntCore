package com.huntcore.listener;

import com.huntcore.HuntCorePlugin;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import com.huntcore.game.PlayerRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerConnectionListener implements Listener {

    private final HuntCorePlugin plugin;
    private final PlayerRegistry playerRegistry;
    private final LobbyService lobbyService;
    private final GameManager gameManager;

    public PlayerConnectionListener(
        HuntCorePlugin plugin,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        GameManager gameManager
    ) {
        this.plugin = plugin;
        this.playerRegistry = playerRegistry;
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerRegistry.registerPlayer(player);

        Bukkit.getScheduler().runTask(plugin, () -> {
            lobbyService.sendToLobby(player, true);
            if (gameManager.isWaitingState()) {
                gameManager.handleLobbyStateChange();
            }
        });

        if (gameManager.isLiveMatchState()) {
            player.sendMessage("[HuntCore] A match is already running. You are waiting in the lobby.");
            return;
        }

        player.sendMessage("[HuntCore] Welcome. Choose /runner or /hunter, then use /ready.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        gameManager.handlePlayerQuit(player);
        playerRegistry.removePlayer(player.getUniqueId());
        gameManager.handleLobbyStateChange();
    }
}

