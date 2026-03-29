package com.huntcore.listener;

import com.huntcore.HuntCorePlugin;
import com.huntcore.pvp.PvpArenaManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PvpEventListener implements Listener {

    private final HuntCorePlugin plugin;
    private final PvpArenaManager pvpArenaManager;

    public PvpEventListener(HuntCorePlugin plugin, PvpArenaManager pvpArenaManager) {
        this.plugin = plugin;
        this.pvpArenaManager = pvpArenaManager;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!pvpArenaManager.isPvpParticipant(player.getUniqueId())) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location respawnLocation = pvpArenaManager.getRespawnLocation(player);
        if (respawnLocation == null) {
            return;
        }

        event.setRespawnLocation(respawnLocation);
        plugin.getServer().getScheduler().runTask(plugin, () -> pvpArenaManager.handlePostRespawn(player));
    }
}
