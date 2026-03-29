package com.huntcore.listener;

import com.huntcore.HuntCorePlugin;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.pvp.PvpArenaManager;
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
    private final PvpArenaManager pvpArenaManager;

    public PlayerConnectionListener(
        HuntCorePlugin plugin,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        GameManager gameManager,
        PvpArenaManager pvpArenaManager
    ) {
        this.plugin = plugin;
        this.playerRegistry = playerRegistry;
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
        this.pvpArenaManager = pvpArenaManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerRegistry.registerPlayer(player);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (pvpArenaManager.handlePlayerJoin(player)) {
                return;
            }

            if (gameManager.handlePlayerJoin(player)) {
                return;
            }

            lobbyService.sendToLobby(player, true);
            if (gameManager.isLiveMatchState()) {
                player.sendMessage("[HuntCore] A match is already running. Use /spectate to watch or wait in the lobby.");
                return;
            }

            if (gameManager.isWaitingState()) {
                gameManager.handleLobbyStateChange();
            }

            player.sendMessage("[HuntCore] Welcome. Choose /runner, /hunter, or /spectate.");
            player.sendMessage("[HuntCore] Active players use /ready when their role is set.");
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        gameManager.handlePlayerQuit(player);
        if (!gameManager.shouldKeepPlayerRegisteredOnQuit(player.getUniqueId())
            && !pvpArenaManager.shouldKeepPlayerRegisteredOnQuit(player.getUniqueId())) {
            playerRegistry.removePlayer(player.getUniqueId());
        }
        gameManager.handleLobbyStateChange();
    }
}
