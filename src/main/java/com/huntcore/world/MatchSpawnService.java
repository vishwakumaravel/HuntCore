package com.huntcore.world;

import com.huntcore.config.PluginConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Biome;

public final class MatchSpawnService {

    private static final int NETHER_SAFE_MAX_Y = 120;
    private static final int[][] LOCAL_SAMPLE_OFFSETS = {
        {0, 0},
        {16, 0},
        {-16, 0},
        {0, 16},
        {0, -16},
        {16, 16},
        {16, -16},
        {-16, 16},
        {-16, -16},
        {32, 0},
        {-32, 0},
        {0, 32},
        {0, -32},
        {32, 32},
        {32, -32},
        {-32, 32},
        {-32, -32},
        {48, 0},
        {-48, 0},
        {0, 48},
        {0, -48},
        {48, 48},
        {48, -48},
        {-48, 48},
        {-48, -48},
        {64, 0},
        {-64, 0},
        {0, 64},
        {0, -64},
        {64, 64},
        {64, -64},
        {-64, 64},
        {-64, -64}
    };

    private final PluginConfig pluginConfig;

    public MatchSpawnService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public Optional<Location> findSafeSpawn(World world) {
        List<Location> candidates = findSafeSpawnCandidates(world, 1);
        return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(0));
    }

    public List<Location> findSafeSpawnCandidates(World world, int maxCandidates) {
        Location worldSpawn = world.getSpawnLocation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Location> candidates = new ArrayList<>();

        int anchorCount = Math.max(1, pluginConfig.getSpawnAttempts());
        for (int anchorIndex = 0; anchorIndex < anchorCount; anchorIndex++) {
            int anchorX = worldSpawn.getBlockX() + random.nextInt(-pluginConfig.getSpawnRadius(), pluginConfig.getSpawnRadius() + 1);
            int anchorZ = worldSpawn.getBlockZ() + random.nextInt(-pluginConfig.getSpawnRadius(), pluginConfig.getSpawnRadius() + 1);

            for (int[] offset : LOCAL_SAMPLE_OFFSETS) {
                int x = anchorX + offset[0];
                int z = anchorZ + offset[1];
                int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;

                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (!isSafe(candidate)) {
                    continue;
                }

                if (!containsSimilarCandidate(candidates, candidate)) {
                    candidates.add(candidate);
                }

                if (candidates.size() >= Math.max(maxCandidates * 2, maxCandidates + 2)) {
                    break;
                }
            }

            if (candidates.size() >= Math.max(maxCandidates * 2, maxCandidates + 2)) {
                break;
            }
        }

        candidates.sort(Comparator.comparingDouble(this::scoreCandidate));
        if (candidates.size() > maxCandidates) {
            return new ArrayList<>(candidates.subList(0, maxCandidates));
        }

        return candidates;
    }

    public Optional<Location> findSafeSpawnNear(World world, double centerX, double centerZ, int horizontalRadius) {
        int baseX = (int) Math.floor(centerX);
        int baseZ = (int) Math.floor(centerZ);
        int minHeight = world.getMinHeight() + 1;
        int maxHeight = Math.min(world.getMaxHeight() - 2, getSearchMaxHeight(world));

        for (int radius = 0; radius <= horizontalRadius; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }

                    int x = baseX + offsetX;
                    int z = baseZ + offsetZ;
                    for (int y = maxHeight; y >= minHeight; y--) {
                        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                        if (isSafe(candidate)) {
                            return Optional.of(candidate);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    private boolean isSafe(Location location) {
        if (!location.getWorld().getWorldBorder().isInside(location)) {
            return false;
        }

        if (location.getWorld().getEnvironment() == World.Environment.NETHER && location.getBlockY() > NETHER_SAFE_MAX_Y) {
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

    private boolean containsSimilarCandidate(List<Location> candidates, Location candidate) {
        for (Location existing : candidates) {
            if (existing.getBlockX() == candidate.getBlockX() && existing.getBlockZ() == candidate.getBlockZ()) {
                return true;
            }
        }

        return false;
    }

    private double scoreCandidate(Location location) {
        if (location.getWorld().getEnvironment() != World.Environment.NORMAL) {
            return 0.0;
        }

        double score = 0.0;
        int[][] sampleOffsets = {
            {0, 0},
            {48, 0},
            {-48, 0},
            {0, 48},
            {0, -48},
            {48, 48},
            {48, -48},
            {-48, 48},
            {-48, -48}
        };

        for (int[] sampleOffset : sampleOffsets) {
            Biome biome = location.getWorld().getBiome(
                location.getBlockX() + sampleOffset[0],
                location.getBlockY(),
                location.getBlockZ() + sampleOffset[1]
            );
            score += biomePenalty(biome);
        }

        return score;
    }

    private double biomePenalty(Biome biome) {
        String biomeName = biome.name();
        if (biomeName.contains("DEEP_OCEAN") || biomeName.contains("FROZEN_OCEAN")) {
            return 16.0;
        }

        if (biomeName.endsWith("OCEAN") || biomeName.contains("OCEAN_")) {
            return 11.0;
        }

        if (biomeName.contains("SNOWY") || biomeName.contains("FROZEN") || biomeName.contains("ICE")) {
            return 7.0;
        }

        if (biomeName.contains("RIVER")) {
            return 1.5;
        }

        return 0.0;
    }

    private int getSearchMaxHeight(World world) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return NETHER_SAFE_MAX_Y;
        }

        return world.getMaxHeight() - 2;
    }
}
