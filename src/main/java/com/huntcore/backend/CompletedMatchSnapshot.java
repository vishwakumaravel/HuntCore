package com.huntcore.backend;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CompletedMatchSnapshot(
    long endedAtMillis,
    long durationMillis,
    String winner,
    String reason,
    String runnerName,
    int hunterCount,
    String poiName,
    int poiDistanceBlocks,
    String matchWorldBaseName,
    Map<String, Integer> playerKills
) {

    public CompletedMatchSnapshot {
        playerKills = Collections.unmodifiableMap(new LinkedHashMap<>(playerKills));
    }
}
