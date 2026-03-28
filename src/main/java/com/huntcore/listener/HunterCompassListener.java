package com.huntcore.listener;

import com.huntcore.game.GameManager;
import com.huntcore.tracking.CompassTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class HunterCompassListener implements Listener {

    private final GameManager gameManager;
    private final CompassTracker compassTracker;

    public HunterCompassListener(GameManager gameManager, CompassTracker compassTracker) {
        this.gameManager = gameManager;
        this.compassTracker = compassTracker;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCompassDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isActiveHunter(player.getUniqueId())) {
            return;
        }

        if (!compassTracker.isHunterCompass(event.getItemDrop().getItemStack())) {
            return;
        }

        event.setCancelled(true);
        player.sendMessage("[HuntCore] Keep your tracker compass with you.");
    }
}
