package com.huntcore.backend;

public record ServerStatusSnapshot(
    long capturedAtMillis,
    String pluginVersion,
    String serverSoftware,
    String serverVersion,
    String gameState,
    int preparedMatchCount,
    boolean scoutingActive,
    int queuedRunnerCount,
    int queuedHunterCount,
    int spectatorCount,
    int queuedCount,
    int readyCount,
    Integer headStartSecondsRemaining,
    String pausedResumeState,
    ActiveMatchSnapshot activeMatch,
    CompletedMatchSnapshot latestCompletedMatch
) {
}
