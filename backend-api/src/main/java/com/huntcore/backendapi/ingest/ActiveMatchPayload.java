package com.huntcore.backendapi.ingest;

import java.util.List;

public record ActiveMatchPayload(
    String runnerName,
    List<String> hunterNames,
    int hunterCount,
    String poiName,
    int poiDistanceBlocks,
    String matchWorldBaseName,
    long startedAtMillis
) {

    public ActiveMatchPayload {
        hunterNames = hunterNames == null ? List.of() : List.copyOf(hunterNames);
    }
}
