package com.huntcore.world;

import com.huntcore.config.PluginConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public final class StructureHintService {

    private static final double MIN_HINT_DISTANCE_BLOCKS = 64.0;
    private static final double CLOSE_HINT_DISTANCE_BLOCKS = 140.0;
    private static final double PREFERRED_HINT_DISTANCE_BLOCKS = 250.0;
    private static final double PREFERRED_HINT_RANGE_BLOCKS = 70.0;
    private static final double MAX_HINT_DISTANCE_BLOCKS = 400.0;

    @SuppressWarnings("unused")
    private final PluginConfig pluginConfig;

    public StructureHintService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public Optional<StructureHint> findNearestHint(Location origin) {
        return Optional.empty();
    }

    public Optional<StructureHint> findNearestStructureHint(Location origin, List<Chunk> chunks, List<String> discouragedStructureTypes) {
        if (origin == null || origin.getWorld() == null || chunks == null || chunks.isEmpty()) {
            return Optional.empty();
        }

        List<StructureHint> hints = new ArrayList<>();
        Set<String> seenStructures = new HashSet<>();
        for (Chunk chunk : chunks) {
            for (GeneratedStructure structure : chunk.getWorld().getStructures(chunk.getX(), chunk.getZ())) {
                String structureName = toSupportedStructureName(structure.getStructure());
                if (structureName == null) {
                    continue;
                }

                Location candidate = new Location(
                    chunk.getWorld(),
                    structure.getBoundingBox().getCenterX(),
                    origin.getY(),
                    structure.getBoundingBox().getCenterZ()
                );
                if (!isUsableCandidate(origin, candidate)) {
                    continue;
                }

                String structureKey = structureName
                    + ":"
                    + Math.round(structure.getBoundingBox().getCenterX() / 16.0)
                    + ":"
                    + Math.round(structure.getBoundingBox().getCenterZ() / 16.0);
                if (!seenStructures.add(structureKey)) {
                    continue;
                }

                int targetYaw = toTargetYawDegrees(origin, candidate);
                hints.add(
                    new StructureHint(
                        structureName,
                        candidate,
                        toDirection(origin, candidate),
                        (int) Math.round(horizontalDistance(origin, candidate)),
                        targetYaw
                    )
                );
            }
        }

        return hints.stream().min(Comparator.comparingDouble(hint -> scoreStructureHint(hint, discouragedStructureTypes)));
    }

    public double scorePreparedStructureHint(StructureHint hint, List<String> discouragedStructureTypes) {
        if (hint == null) {
            return Double.MAX_VALUE;
        }

        return scoreStructureHint(hint, discouragedStructureTypes);
    }

    public Optional<StructureHint> findNearestHint(Location origin, List<Location> candidates) {
        if (origin == null || origin.getWorld() == null || candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        List<StructureHint> hints = new ArrayList<>();
        for (Location candidate : candidates) {
            if (!isUsableCandidate(origin, candidate)) {
                continue;
            }

            String landmarkName = classifyLandmark(candidate);
            if (landmarkName == null) {
                continue;
            }

            int targetYaw = toTargetYawDegrees(origin, candidate);
            hints.add(
                new StructureHint(
                    landmarkName,
                    candidate,
                    toDirection(origin, candidate),
                    (int) Math.round(horizontalDistance(origin, candidate)),
                    targetYaw
                )
            );
        }

        return hints.stream().min(Comparator.comparingDouble(this::scoreHint));
    }

    public ItemStack createHintBook(StructureHint structureHint) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        bookMeta.setTitle("Scout Report");
        bookMeta.setAuthor("HuntCore");
        bookMeta.addPage(
            "A nearby lead has been found.\n\n"
                + "Head roughly "
                + structureHint.roughDirection()
                + ".\n"
                + "About "
                + structureHint.approximateDistanceBlocks()
                + " blocks away.\n"
                + "Face about yaw "
                + structureHint.targetYawDegrees()
                + " deg.\n\n"
                + "Nearby POI:\n"
                + structureHint.displayName()
                + "."
        );
        book.setItemMeta(bookMeta);
        return book;
    }

    private boolean isUsableCandidate(Location origin, Location candidate) {
        if (candidate == null || candidate.getWorld() == null || !candidate.getWorld().equals(origin.getWorld())) {
            return false;
        }

        double distance = horizontalDistance(origin, candidate);
        return distance >= MIN_HINT_DISTANCE_BLOCKS && distance <= MAX_HINT_DISTANCE_BLOCKS;
    }

    private String classifyLandmark(Location candidate) {
        World world = candidate.getWorld();
        if (world == null || !world.getWorldBorder().isInside(candidate)) {
            return null;
        }

        Material ground = candidate.clone().add(0.0, -1.0, 0.0).getBlock().getType();
        if (!ground.isSolid() || ground == Material.LAVA || ground == Material.MAGMA_BLOCK) {
            return null;
        }

        String biomeName = world.getBiome(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ()).name();
        if (isSnowyOrOceanicBiome(biomeName)) {
            return null;
        }

        boolean nearWater = isNearWater(candidate, 6);
        if (nearWater && (ground == Material.SAND || ground == Material.RED_SAND || ground == Material.GRAVEL)) {
            return ground == Material.GRAVEL ? "stony shoreline" : "shoreline";
        }

        if (biomeName.contains("RIVER")) {
            return "river crossing";
        }

        if (biomeName.contains("DESERT")) {
            return "desert edge";
        }

        if (biomeName.contains("JUNGLE")) {
            return "jungle edge";
        }

        if (biomeName.contains("CHERRY")) {
            return "cherry grove";
        }

        if (biomeName.contains("BIRCH")) {
            return "birch grove";
        }

        if (biomeName.contains("FLOWER") || biomeName.contains("SUNFLOWER")) {
            return "flower field";
        }

        if (biomeName.contains("FOREST") || biomeName.contains("TAIGA")) {
            return "forest edge";
        }

        if (biomeName.contains("PLAINS") || biomeName.contains("SAVANNA") || biomeName.contains("MEADOW")) {
            return biomeName.contains("MEADOW") ? "meadow clearing" : "open plains";
        }

        if (biomeName.contains("PEAK") || biomeName.contains("SLOPE") || biomeName.contains("GROVE") || biomeName.contains("HILLS")) {
            return "stony ridge";
        }

        if (biomeName.contains("SWAMP") || biomeName.contains("MANGROVE")) {
            return "swamp edge";
        }

        if (biomeName.contains("BADLANDS")) {
            return "badlands rise";
        }

        if (nearWater) {
            return "river crossing";
        }

        return "landmark";
    }

    private double scoreHint(StructureHint hint) {
        return scoreDistance(hint.approximateDistanceBlocks()) + landmarkPenalty(hint.displayName());
    }

    private double scoreStructureHint(StructureHint hint, List<String> discouragedStructureTypes) {
        return scoreDistance(hint.approximateDistanceBlocks())
            + structurePenalty(hint.displayName())
            + varietyPenalty(hint.displayName(), discouragedStructureTypes);
    }

    private double scoreDistance(int distanceBlocks) {
        double distance = distanceBlocks;
        if (distance < MIN_HINT_DISTANCE_BLOCKS) {
            return 600.0 + (MIN_HINT_DISTANCE_BLOCKS - distance) * 10.0;
        }

        if (distance > MAX_HINT_DISTANCE_BLOCKS) {
            return 4000.0 + (distance - MAX_HINT_DISTANCE_BLOCKS) * 12.0;
        }

        double preferredDelta = Math.abs(distance - PREFERRED_HINT_DISTANCE_BLOCKS);
        if (preferredDelta <= PREFERRED_HINT_RANGE_BLOCKS) {
            return preferredDelta;
        }

        if (distance < CLOSE_HINT_DISTANCE_BLOCKS) {
            return 220.0 + (CLOSE_HINT_DISTANCE_BLOCKS - distance) * 3.0;
        }

        return 120.0 + preferredDelta * 1.4;
    }

    private double landmarkPenalty(String landmarkName) {
        return switch (landmarkName) {
            case "shoreline", "stony shoreline", "river crossing", "open plains", "flower field", "meadow clearing" -> 0.0;
            case "forest edge", "desert edge", "birch grove", "cherry grove" -> 8.0;
            case "jungle edge", "stony ridge", "swamp edge", "badlands rise" -> 15.0;
            default -> 20.0;
        };
    }

    private double structurePenalty(String structureName) {
        return switch (structureName) {
            case "village" -> 0.0;
            case "shipwreck" -> 6.0;
            case "pillager outpost" -> 10.0;
            case "ruined portal" -> 12.0;
            case "desert temple", "jungle temple" -> 16.0;
            default -> 20.0;
        };
    }

    private double varietyPenalty(String structureName, List<String> discouragedStructureTypes) {
        if (discouragedStructureTypes == null || discouragedStructureTypes.isEmpty()) {
            return 0.0;
        }

        double penalty = 0.0;
        int duplicateCount = 0;
        for (String discouragedType : discouragedStructureTypes) {
            if (structureName.equalsIgnoreCase(discouragedType)) {
                duplicateCount++;
            }
        }

        if (duplicateCount == 0) {
            return 0.0;
        }

        penalty += duplicateCount * 18.0;
        String mostRecent = discouragedStructureTypes.get(0);
        if (structureName.equalsIgnoreCase(mostRecent)) {
            penalty += 18.0;
        }

        return penalty;
    }

    private boolean isSnowyOrOceanicBiome(String biomeName) {
        return biomeName.contains("OCEAN")
            || biomeName.contains("FROZEN")
            || biomeName.contains("SNOWY")
            || biomeName.contains("ICE");
    }

    private boolean isNearWater(Location location, int radius) {
        World world = location.getWorld();
        for (int offsetX = -radius; offsetX <= radius; offsetX++) {
            for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                Material type = world.getBlockAt(location.getBlockX() + offsetX, location.getBlockY() - 1, location.getBlockZ() + offsetZ).getType();
                if (type == Material.WATER) {
                    return true;
                }
            }
        }

        return false;
    }

    private int toTargetYawDegrees(Location origin, Location target) {
        double deltaX = target.getX() - origin.getX();
        double deltaZ = target.getZ() - origin.getZ();
        double yaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
        if (yaw > 180.0) {
            yaw -= 360.0;
        }
        if (yaw <= -180.0) {
            yaw += 360.0;
        }
        return (int) Math.round(yaw);
    }

    private String toDirection(Location origin, Location target) {
        double deltaX = target.getX() - origin.getX();
        double deltaZ = target.getZ() - origin.getZ();
        double angle = Math.toDegrees(Math.atan2(deltaX, -deltaZ));
        if (angle < 0.0) {
            angle += 360.0;
        }

        String[] directions = {
            "N", "NNE", "NE", "ENE",
            "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW",
            "W", "WNW", "NW", "NNW"
        };
        int index = (int) Math.round(angle / 22.5) % directions.length;
        return directions[index];
    }

    private double horizontalDistance(Location origin, Location target) {
        double deltaX = target.getX() - origin.getX();
        double deltaZ = target.getZ() - origin.getZ();
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private String toSupportedStructureName(Structure structure) {
        if (structure == Structure.VILLAGE_DESERT
            || structure == Structure.VILLAGE_PLAINS
            || structure == Structure.VILLAGE_SAVANNA
            || structure == Structure.VILLAGE_SNOWY
            || structure == Structure.VILLAGE_TAIGA) {
            return "village";
        }

        if (structure == Structure.SHIPWRECK || structure == Structure.SHIPWRECK_BEACHED) {
            return "shipwreck";
        }

        if (structure == Structure.PILLAGER_OUTPOST) {
            return "pillager outpost";
        }

        if (structure == Structure.DESERT_PYRAMID) {
            return "desert temple";
        }

        if (structure == Structure.JUNGLE_PYRAMID) {
            return "jungle temple";
        }

        if (structure == Structure.RUINED_PORTAL
            || structure == Structure.RUINED_PORTAL_DESERT
            || structure == Structure.RUINED_PORTAL_JUNGLE
            || structure == Structure.RUINED_PORTAL_MOUNTAIN
            || structure == Structure.RUINED_PORTAL_OCEAN
            || structure == Structure.RUINED_PORTAL_SWAMP) {
            return "ruined portal";
        }

        return null;
    }
}
