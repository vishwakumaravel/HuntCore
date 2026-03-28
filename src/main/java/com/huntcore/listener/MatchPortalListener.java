package com.huntcore.listener;

import com.huntcore.game.GameManager;
import com.huntcore.tracking.PortalTrackingService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

public final class MatchPortalListener implements Listener {

    private final GameManager gameManager;
    private final PortalTrackingService portalTrackingService;

    public MatchPortalListener(GameManager gameManager, PortalTrackingService portalTrackingService) {
        this.gameManager = gameManager;
        this.portalTrackingService = portalTrackingService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        Location destination = gameManager.getPortalDestination(player, event.getCause(), event.getFrom());
        if (destination == null) {
            return;
        }

        event.setTo(destination);
        if (gameManager.isActiveRunner(player.getUniqueId())) {
            portalTrackingService.recordTransition(event.getFrom(), destination, event.getCause());
        }
    }
}
