package com.huntcore.world;

import org.bukkit.Location;

public record StructureHint(
    String landmarkName,
    Location location,
    String roughDirection,
    int approximateDistanceBlocks,
    int targetYawDegrees
) {

    public String displayName() {
        return landmarkName == null || landmarkName.isBlank() ? "landmark" : landmarkName;
    }
}
