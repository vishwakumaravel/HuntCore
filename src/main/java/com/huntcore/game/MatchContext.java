package com.huntcore.game;

import com.huntcore.world.StructureHint;
import com.huntcore.world.MatchWorldSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;

public final class MatchContext {

    private final UUID runnerId;
    private final List<UUID> hunterIds;
    private final Location matchSpawn;
    private final StructureHint structureHint;
    private final MatchWorldSet matchWorldSet;
    private final long startedAtMillis;
    private Location runnerLastKnownLocation;
    private final Map<UUID, Location> hunterLastKnownLocations = new HashMap<>();
    private final Map<UUID, Integer> playerKillCounts = new HashMap<>();

    public MatchContext(
        UUID runnerId,
        List<UUID> hunterIds,
        Location matchSpawn,
        StructureHint structureHint,
        MatchWorldSet matchWorldSet
    ) {
        this(runnerId, hunterIds, matchSpawn, structureHint, matchWorldSet, System.currentTimeMillis());
    }

    public MatchContext(
        UUID runnerId,
        List<UUID> hunterIds,
        Location matchSpawn,
        StructureHint structureHint,
        MatchWorldSet matchWorldSet,
        long startedAtMillis
    ) {
        this.runnerId = runnerId;
        this.hunterIds = new ArrayList<>(hunterIds);
        this.matchSpawn = matchSpawn.clone();
        this.structureHint = structureHint;
        this.matchWorldSet = matchWorldSet;
        this.startedAtMillis = startedAtMillis;
    }

    public UUID getRunnerId() {
        return runnerId;
    }

    public List<UUID> getHunterIds() {
        return Collections.unmodifiableList(hunterIds);
    }

    public Location getMatchSpawn() {
        return matchSpawn.clone();
    }

    public StructureHint getStructureHint() {
        return structureHint;
    }

    public MatchWorldSet getMatchWorldSet() {
        return matchWorldSet;
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

    public int getHunterSpawnIndex(UUID playerId) {
        return hunterIds.indexOf(playerId);
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public void rememberParticipantLocation(UUID playerId, Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        Location clone = location.clone();
        if (isRunner(playerId)) {
            runnerLastKnownLocation = clone;
            return;
        }

        if (isHunter(playerId)) {
            hunterLastKnownLocations.put(playerId, clone);
        }
    }

    public Location getLastKnownLocation(UUID playerId) {
        if (isRunner(playerId)) {
            return runnerLastKnownLocation == null ? null : runnerLastKnownLocation.clone();
        }

        Location hunterLocation = hunterLastKnownLocations.get(playerId);
        return hunterLocation == null ? null : hunterLocation.clone();
    }

    public void recordKill(UUID playerId) {
        if (!involves(playerId)) {
            return;
        }

        playerKillCounts.merge(playerId, 1, Integer::sum);
    }

    public int getKillCount(UUID playerId) {
        return playerKillCounts.getOrDefault(playerId, 0);
    }

    public Map<UUID, Integer> getKillCounts() {
        return Collections.unmodifiableMap(playerKillCounts);
    }
}
