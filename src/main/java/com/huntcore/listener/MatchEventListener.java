package com.huntcore.listener;

import com.huntcore.HuntCorePlugin;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class MatchEventListener implements Listener {

    private final HuntCorePlugin plugin;
    private final LobbyService lobbyService;
    private final GameManager gameManager;

    public MatchEventListener(HuntCorePlugin plugin, LobbyService lobbyService, GameManager gameManager) {
        this.plugin = plugin;
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && gameManager.shouldApplyLobbyProtections(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && gameManager.shouldApplyLobbyProtections(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (gameManager.isActiveRunner(player.getUniqueId())) {
            gameManager.handleRunnerDeath(player);
            return;
        }

        if (gameManager.isActiveHunter(player.getUniqueId())) {
            gameManager.handleHunterElimination(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.shouldRespawnInLobby(player.getUniqueId())) {
            return;
        }

        event.setRespawnLocation(lobbyService.getLobbySpawn(player));
        plugin.getServer().getScheduler().runTask(plugin, () -> lobbyService.prepareForLobby(player, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunterMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isHunterFrozen(player) || event.getTo() == null) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
            return;
        }

        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
    }
}
