package com.huntcore.backendapi.ingest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record CompletedMatchPayload(
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

    public CompletedMatchPayload {
        playerKills = playerKills == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(playerKills));
    }
}
