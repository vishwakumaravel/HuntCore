package com.huntcore.listener;

import com.huntcore.HuntCorePlugin;
import com.huntcore.game.GameManager;
import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class MatchEventListener implements Listener {

    private static final NamespacedKey KILL_DRAGON_ADVANCEMENT = NamespacedKey.minecraft("end/kill_dragon");

    private final HuntCorePlugin plugin;
    private final GameManager gameManager;
    private final PvpArenaManager pvpArenaManager;

    public MatchEventListener(HuntCorePlugin plugin, GameManager gameManager, PvpArenaManager pvpArenaManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.pvpArenaManager = pvpArenaManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
            && !pvpArenaManager.isPvpParticipant(player.getUniqueId())
            && gameManager.shouldPreventHunger(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        if (killer != null) {
            gameManager.handleParticipantKill(killer, player);
        }
        if (gameManager.isActiveRunner(player.getUniqueId())) {
            gameManager.handleRunnerDeath(player);
            return;
        }

        if (gameManager.isActiveHunter(player.getUniqueId())) {
            if (gameManager.shouldHuntersKeepInventory()) {
                event.setKeepInventory(true);
                event.setKeepLevel(true);
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            gameManager.handleHunterDeath(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawnLocation = gameManager.getRespawnLocation(player, event.getRespawnLocation());
        if (respawnLocation == null) {
            return;
        }

        event.setRespawnLocation(respawnLocation);
        plugin.getServer().getScheduler().runTask(plugin, () -> gameManager.handlePostRespawn(player));
    }

    @EventHandler
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        if (!event.getAdvancement().getKey().equals(KILL_DRAGON_ADVANCEMENT)) {
            return;
        }

        gameManager.handleRunnerDragonKill(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunterMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!gameManager.isMovementLocked(player) || event.getTo() == null) {
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
