package com.huntcore.tracking;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PortalTrackingService {

    private final Map<String, PortalMemory> memoriesByWorld = new HashMap<>();

    public void reset() {
        memoriesByWorld.clear();
    }

    public void recordTransition(Location fromLocation, Location toLocation, PlayerTeleportEvent.TeleportCause cause) {
        remember(fromLocation, cause);
        remember(toLocation, cause);
    }

    public Optional<Location> findRecentLocation(World world, long maxAgeMillis) {
        if (world == null) {
            return Optional.empty();
        }

        PortalMemory memory = memoriesByWorld.get(world.getName());
        if (memory == null) {
            return Optional.empty();
        }

        if (System.currentTimeMillis() - memory.timestampMillis() > maxAgeMillis) {
            memoriesByWorld.remove(world.getName());
            return Optional.empty();
        }

        return Optional.of(memory.location().clone());
    }

    private void remember(Location location, PlayerTeleportEvent.TeleportCause cause) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        memoriesByWorld.put(
            location.getWorld().getName(),
            new PortalMemory(location.clone(), System.currentTimeMillis(), cause)
        );
    }

    private record PortalMemory(Location location, long timestampMillis, PlayerTeleportEvent.TeleportCause cause) {
    }
}
