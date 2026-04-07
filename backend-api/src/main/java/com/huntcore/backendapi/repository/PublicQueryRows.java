package com.huntcore.backendapi.repository;

public final class PublicQueryRows {

    private PublicQueryRows() {
    }

    public record ServerSummaryRow(
        String serverId,
        long receivedAtMillis,
        String gameState,
        int queuedCount,
        int readyCount,
        boolean hasActiveMatch,
        String latestCompletedMatchJson
    ) {
    }

    public record ServerDetailRow(
        String serverId,
        long receivedAtMillis,
        String rawHeartbeatJson
    ) {
    }

    public record MatchSummaryRow(
        long matchId,
        String serverId,
        long receivedAtMillis,
        long endedAtMillis,
        long durationMillis,
        String winner,
        String reason,
        String runnerName,
        int hunterCount,
        String poiName,
        int poiDistanceBlocks,
        String matchWorldBaseName,
        String rawPayload
    ) {
    }

    public record PlayerStatsRow(
        String playerName,
        long matchesPlayed,
        long wins,
        long kills,
        long runnerMatches,
        long hunterMatches,
        long runnerWins,
        long hunterWins
    ) {
    }

    public record PlayerMatchRow(
        long matchId,
        String serverId,
        long endedAtMillis,
        String winner,
        String reason,
        String role,
        int kills,
        String runnerName,
        int hunterCount,
        String poiName,
        String matchWorldBaseName
    ) {
    }
}
