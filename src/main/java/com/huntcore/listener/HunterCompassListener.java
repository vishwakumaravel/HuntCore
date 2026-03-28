package com.huntcore.listener;

import com.huntcore.game.GameManager;
import com.huntcore.tracking.CompassTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
        compassTracker.reconcileHunterCompass(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !gameManager.isActiveHunter(player.getUniqueId())) {
            return;
        }

        if (compassTracker.isHunterCompass(event.getCurrentItem()) || compassTracker.isHunterCompass(event.getCursor())) {
            event.setCancelled(true);
            compassTracker.reconcileHunterCompass(player);
            return;
        }

        if (event.getHotbarButton() >= 0 && compassTracker.isHunterCompass(player.getInventory().getItem(event.getHotbarButton()))) {
            event.setCancelled(true);
            compassTracker.reconcileHunterCompass(player);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !gameManager.isActiveHunter(player.getUniqueId())) {
            return;
        }

        if (!compassTracker.isHunterCompass(event.getOldCursor())) {
            return;
        }

        event.setCancelled(true);
        compassTracker.reconcileHunterCompass(player);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!gameManager.isActiveHunter(player.getUniqueId())) {
            return;
        }

        compassTracker.removeHunterCompasses(event.getDrops());
    }
}
