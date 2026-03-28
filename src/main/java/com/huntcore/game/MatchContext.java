package com.huntcore.game;

import com.huntcore.world.StructureHint;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;

public final class MatchContext {

    private final UUID runnerId;
    private final Set<UUID> hunterIds;
    private final Set<UUID> eliminatedHunters = new HashSet<>();
    private final Location matchSpawn;
    private final StructureHint structureHint;

    public MatchContext(UUID runnerId, Set<UUID> hunterIds, Location matchSpawn, StructureHint structureHint) {
        this.runnerId = runnerId;
        this.hunterIds = new HashSet<>(hunterIds);
        this.matchSpawn = matchSpawn.clone();
        this.structureHint = structureHint;
    }

    public UUID getRunnerId() {
        return runnerId;
    }

    public Set<UUID> getHunterIds() {
        return Collections.unmodifiableSet(hunterIds);
    }

    public Location getMatchSpawn() {
        return matchSpawn.clone();
    }

    public StructureHint getStructureHint() {
        return structureHint;
    }

    public boolean isRunner(UUID playerId) {
        return runnerId.equals(playerId);
    }

    public boolean isHunter(UUID playerId) {
        return hunterIds.contains(playerId);
    }

    public boolean involves(UUID playerId) {
        return isRunner(playerId) || isHunter(playerId);
    }

    public boolean isHunterEliminated(UUID playerId) {
        return eliminatedHunters.contains(playerId);
    }

    public void eliminateHunter(UUID playerId) {
        eliminatedHunters.add(playerId);
    }
}

