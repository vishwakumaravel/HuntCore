package com.huntcore.world;

public final class MatchWorldSet {

    private final String baseName;
    private final String overworldName;
    private final String netherName;
    private final String endName;

    public MatchWorldSet(String baseName) {
        this.baseName = baseName;
        this.overworldName = baseName;
        this.netherName = baseName + "_nether";
        this.endName = baseName + "_the_end";
    }

    public String getBaseName() {
        return baseName;
    }

    public String getOverworldName() {
        return overworldName;
    }

    public String getNetherName() {
        return netherName;
    }

    public String getEndName() {
        return endName;
    }

    public boolean containsWorld(String worldName) {
        return overworldName.equals(worldName) || netherName.equals(worldName) || endName.equals(worldName);
    }
}
