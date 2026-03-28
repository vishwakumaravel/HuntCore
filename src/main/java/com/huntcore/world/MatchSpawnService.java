package com.huntcore.world;

import com.huntcore.config.PluginConfig;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class MatchSpawnService {

    private final PluginConfig pluginConfig;

    public MatchSpawnService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public Optional<Location> findSafeSpawn(World world) {
        Location worldSpawn = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int attempt = 0; attempt < pluginConfig.getSpawnAttempts(); attempt++) {
            int x = worldSpawn.getBlockX() + random.nextInt(-pluginConfig.getSpawnRadius(), pluginConfig.getSpawnRadius() + 1);
            int z = worldSpawn.getBlockZ() + random.nextInt(-pluginConfig.getSpawnRadius(), pluginConfig.getSpawnRadius() + 1);
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;

            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (isSafe(candidate)) {
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private boolean isSafe(Location location) {
        if (!location.getWorld().getWorldBorder().isInside(location)) {
            return false;
        }

        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block below = feet.getRelative(0, -1, 0);

        if (!below.getType().isSolid()) {
            return false;
        }

        if (feet.getType().isSolid() || head.getType().isSolid() || feet.isLiquid() || head.isLiquid()) {
            return false;
        }

        return switch (below.getType()) {
            case LAVA, WATER, MAGMA_BLOCK, CAMPFIRE, SOUL_CAMPFIRE, CACTUS -> false;
            default -> true;
        };
    }
}
