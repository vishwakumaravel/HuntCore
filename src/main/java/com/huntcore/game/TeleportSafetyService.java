package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TeleportSafetyService {

    private static final long TELEPORT_GRACE_TICKS = 20L;

    private final HuntCorePlugin plugin;
    private final Map<UUID, BukkitTask> restoreTasks = new HashMap<>();

    public TeleportSafetyService(HuntCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void teleport(Player player, Location destination, boolean allowFlightAfter, boolean flyingAfter) {
        if (player == null || destination == null) {
            return;
        }

        applyGrace(player, allowFlightAfter, flyingAfter);
        player.teleport(destination);
        player.setFallDistance(0.0f);
    }

    public void stabilize(Player player, boolean allowFlightAfter, boolean flyingAfter) {
        if (player == null) {
            return;
        }

        applyGrace(player, allowFlightAfter, flyingAfter);
    }

    private void applyGrace(Player player, boolean allowFlightAfter, boolean flyingAfter) {
        cancelRestoreTask(player.getUniqueId());
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setAllowFlight(true);
        player.setFlying(false);

        BukkitTask restoreTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restoreTasks.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }

            player.setFireTicks(0);
            player.setFallDistance(0.0f);
            player.setAllowFlight(allowFlightAfter);
            player.setFlying(allowFlightAfter && flyingAfter);
        }, TELEPORT_GRACE_TICKS);

        restoreTasks.put(player.getUniqueId(), restoreTask);
    }

    private void cancelRestoreTask(UUID playerId) {
        BukkitTask existingTask = restoreTasks.remove(playerId);
        if (existingTask != null) {
            existingTask.cancel();
        }
    }
}
