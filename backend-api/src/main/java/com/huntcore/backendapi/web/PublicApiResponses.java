package com.huntcore.backendapi.web;

import com.huntcore.backendapi.ingest.CompletedMatchPayload;
import com.huntcore.backendapi.ingest.HeartbeatPayload;
import java.util.List;
import java.util.Map;

public final class PublicApiResponses {

    private PublicApiResponses() {
    }

    public record ServerListResponse(List<ServerSummary> servers) {
    }

    public record ServerSummary(
        String serverId,
        long receivedAtMillis,
        String gameState,
        int queuedCount,
        int readyCount,
        boolean isOnline,
        long lastHeartbeatAgeSeconds,
        boolean hasActiveMatch,
        CompletedMatchPayload latestCompletedMatch
    ) {
    }

    public record ServerDetail(
        String serverId,
        long receivedAtMillis,
        boolean isOnline,
        long lastHeartbeatAgeSeconds,
        HeartbeatPayload heartbeat
    ) {
    }

    public record MatchListResponse(
        List<MatchSummary> items,
        int limit,
        int offset,
        long total
    ) {
    }

    public record MatchSummary(
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
        Map<String, Integer> playerKills
    ) {
    }

    public record PlayerListResponse(
        List<PlayerLeaderboardEntry> items,
        int limit,
        int offset,
        long total,
        String sort
    ) {
    }

    public record PlayerLeaderboardEntry(
        String playerName,
        long matchesPlayed,
        long wins,
        long losses,
        long kills,
        double winRatePercent,
        long runnerMatches,
        long hunterMatches,
        long runnerWins,
        long hunterWins
    ) {
    }

    public record PlayerDetail(
        String playerName,
        long matchesPlayed,
        long wins,
        long losses,
        long kills,
        double winRatePercent,
        long runnerMatches,
        long hunterMatches,
        long runnerWins,
        long hunterWins,
        List<PlayerMatch> recentMatches
    ) {
    }

    public record PlayerMatchHistoryResponse(
        String playerName,
        List<PlayerMatch> items,
        int limit,
        int offset,
        long total
    ) {
    }

    public record PlayerMatch(
        long matchId,
        String serverId,
        long endedAtMillis,
        String winner,
        String reason,
        String role,
        int kills,
        boolean wasWin,
        String runnerName,
        int hunterCount,
        String poiName,
        String matchWorldBaseName
    ) {
    }
}
