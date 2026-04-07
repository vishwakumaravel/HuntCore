package com.huntcore.backend;

import java.util.List;

public record ActiveMatchSnapshot(
    String runnerName,
    List<String> hunterNames,
    int hunterCount,
    String poiName,
    int poiDistanceBlocks,
    String matchWorldBaseName,
    long startedAtMillis
) {

    public ActiveMatchSnapshot {
        hunterNames = List.copyOf(hunterNames);
    }
}
