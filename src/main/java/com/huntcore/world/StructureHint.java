package com.huntcore.world;

import org.bukkit.Location;
import org.bukkit.StructureType;

@SuppressWarnings("deprecation")
public record StructureHint(StructureType structureType, Location location, String roughDirection) {

    public String displayName() {
        if (structureType == StructureType.VILLAGE) {
            return "village";
        }

        if (structureType == StructureType.RUINED_PORTAL) {
            return "ruined portal";
        }

        if (structureType == StructureType.PILLAGER_OUTPOST) {
            return "pillager outpost";
        }

        return "structure";
    }
}
