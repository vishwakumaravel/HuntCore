package com.huntcore.backendapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huntcore.backendapi.config.PublicApiProperties;
import com.huntcore.backendapi.ingest.CompletedMatchPayload;
import com.huntcore.backendapi.ingest.HeartbeatPayload;
import com.huntcore.backendapi.repository.PublicMatchQueryRepository;
import com.huntcore.backendapi.repository.PublicPlayerSort;
import com.huntcore.backendapi.repository.PublicPlayerStatsQueryRepository;
import com.huntcore.backendapi.repository.PublicQueryRows;
import com.huntcore.backendapi.repository.PublicServerQueryRepository;
import com.huntcore.backendapi.web.PublicApiResponses;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicStatsService {

    private final PublicServerQueryRepository publicServerQueryRepository;
    private final PublicMatchQueryRepository publicMatchQueryRepository;
    private final PublicPlayerStatsQueryRepository publicPlayerStatsQueryRepository;
    private final ObjectMapper objectMapper;
    private final PublicApiProperties publicApiProperties;

    public PublicStatsService(
        PublicServerQueryRepository publicServerQueryRepository,
        PublicMatchQueryRepository publicMatchQueryRepository,
        PublicPlayerStatsQueryRepository publicPlayerStatsQueryRepository,
        ObjectMapper objectMapper,
        PublicApiProperties publicApiProperties
    ) {
        this.publicServerQueryRepository = publicServerQueryRepository;
        this.publicMatchQueryRepository = publicMatchQueryRepository;
        this.publicPlayerStatsQueryRepository = publicPlayerStatsQueryRepository;
        this.objectMapper = objectMapper;
        this.publicApiProperties = publicApiProperties;
    }

    public PublicApiResponses.ServerListResponse listServers() {
        List<PublicApiResponses.ServerSummary> servers = publicServerQueryRepository.findAllSummaries().stream()
            .map(this::toServerSummary)
            .toList();
        return new PublicApiResponses.ServerListResponse(servers);
    }

    public PublicApiResponses.ServerDetail getServer(String serverId) {
        PublicQueryRows.ServerDetailRow row = publicServerQueryRepository.findByServerId(serverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No heartbeat found for server."));
        return new PublicApiResponses.ServerDetail(
            row.serverId(),
            row.receivedAtMillis(),
            isOnline(row.receivedAtMillis()),
            lastHeartbeatAgeSeconds(row.receivedAtMillis()),
            parseHeartbeat(row.rawHeartbeatJson())
        );
    }

    public PublicApiResponses.MatchListResponse listMatches(int limit, int offset) {
        int clampedLimit = clampLimit(limit);
        int clampedOffset = clampOffset(offset);
        List<PublicApiResponses.MatchSummary> items = publicMatchQueryRepository.findRecent(clampedLimit, clampedOffset).stream()
            .map(this::toMatchSummary)
            .toList();
        return new PublicApiResponses.MatchListResponse(
            items,
            clampedLimit,
            clampedOffset,
            publicMatchQueryRepository.countAll()
        );
    }

    public PublicApiResponses.PlayerListResponse listPlayers(String sortValue, int limit, int offset) {
        PublicPlayerSort sort = PublicPlayerSort.fromQueryValue(sortValue);
        int clampedLimit = clampLimit(limit);
        int clampedOffset = clampOffset(offset);
        List<PublicApiResponses.PlayerLeaderboardEntry> items = publicPlayerStatsQueryRepository
            .findLeaderboard(sort, clampedLimit, clampedOffset).stream()
            .map(this::toPlayerLeaderboardEntry)
            .toList();
        return new PublicApiResponses.PlayerListResponse(
            items,
            clampedLimit,
            clampedOffset,
            publicPlayerStatsQueryRepository.countPlayers(),
            sort.queryValue()
        );
    }

    public PublicApiResponses.PlayerDetail getPlayer(String playerName) {
        PublicQueryRows.PlayerStatsRow row = publicPlayerStatsQueryRepository.findByPlayerName(playerName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No stats found for player."));
        return new PublicApiResponses.PlayerDetail(
            row.playerName(),
            row.matchesPlayed(),
            row.wins(),
            row.matchesPlayed() - row.wins(),
            row.kills(),
            winRatePercent(row.wins(), row.matchesPlayed()),
            row.runnerMatches(),
            row.hunterMatches(),
            row.runnerWins(),
            row.hunterWins(),
            publicPlayerStatsQueryRepository.findPlayerMatches(playerName, 10, 0).stream()
                .map(this::toPlayerMatch)
                .toList()
        );
    }

    public PublicApiResponses.PlayerMatchHistoryResponse getPlayerMatches(String playerName, int limit, int offset) {
        if (publicPlayerStatsQueryRepository.findByPlayerName(playerName).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No stats found for player.");
        }

        int clampedLimit = clampLimit(limit);
        int clampedOffset = clampOffset(offset);
        List<PublicApiResponses.PlayerMatch> items = publicPlayerStatsQueryRepository
            .findPlayerMatches(playerName, clampedLimit, clampedOffset).stream()
            .map(this::toPlayerMatch)
            .toList();
        return new PublicApiResponses.PlayerMatchHistoryResponse(
            playerName,
            items,
            clampedLimit,
            clampedOffset,
            publicPlayerStatsQueryRepository.countPlayerMatches(playerName)
        );
    }

    private PublicApiResponses.ServerSummary toServerSummary(PublicQueryRows.ServerSummaryRow row) {
        return new PublicApiResponses.ServerSummary(
            row.serverId(),
            row.receivedAtMillis(),
            row.gameState(),
            row.queuedCount(),
            row.readyCount(),
            isOnline(row.receivedAtMillis()),
            lastHeartbeatAgeSeconds(row.receivedAtMillis()),
            row.hasActiveMatch(),
            parseCompletedMatch(row.latestCompletedMatchJson())
        );
    }

    private PublicApiResponses.MatchSummary toMatchSummary(PublicQueryRows.MatchSummaryRow row) {
        CompletedMatchPayload payload = parseCompletedMatchRequired(row.rawPayload());
        Map<String, Integer> playerKills = payload.playerKills() == null ? Map.of() : payload.playerKills();
        return new PublicApiResponses.MatchSummary(
            row.matchId(),
            row.serverId(),
            row.receivedAtMillis(),
            row.endedAtMillis(),
            row.durationMillis(),
            row.winner(),
            row.reason(),
            row.runnerName(),
            row.hunterCount(),
            row.poiName(),
            row.poiDistanceBlocks(),
            row.matchWorldBaseName(),
            playerKills
        );
    }

    private PublicApiResponses.PlayerLeaderboardEntry toPlayerLeaderboardEntry(PublicQueryRows.PlayerStatsRow row) {
        return new PublicApiResponses.PlayerLeaderboardEntry(
            row.playerName(),
            row.matchesPlayed(),
            row.wins(),
            row.matchesPlayed() - row.wins(),
            row.kills(),
            winRatePercent(row.wins(), row.matchesPlayed()),
            row.runnerMatches(),
            row.hunterMatches(),
            row.runnerWins(),
            row.hunterWins()
        );
    }

    private PublicApiResponses.PlayerMatch toPlayerMatch(PublicQueryRows.PlayerMatchRow row) {
        return new PublicApiResponses.PlayerMatch(
            row.matchId(),
            row.serverId(),
            row.endedAtMillis(),
            row.winner(),
            row.reason(),
            row.role(),
            row.kills(),
            wasWin(row.role(), row.winner()),
            row.runnerName(),
            row.hunterCount(),
            row.poiName(),
            row.matchWorldBaseName()
        );
    }

    private boolean isOnline(long receivedAtMillis) {
        return lastHeartbeatAgeSeconds(receivedAtMillis) <= publicApiProperties.getStaleThresholdSeconds();
    }

    private long lastHeartbeatAgeSeconds(long receivedAtMillis) {
        long ageMillis = Math.max(0L, System.currentTimeMillis() - receivedAtMillis);
        return ageMillis / 1000L;
    }

    private double winRatePercent(long wins, long matchesPlayed) {
        if (matchesPlayed <= 0) {
            return 0.0D;
        }

        double percentage = (wins * 100.0D) / matchesPlayed;
        return Math.round(percentage * 10.0D) / 10.0D;
    }

    private boolean wasWin(String role, String winner) {
        if ("RUNNER".equalsIgnoreCase(role)) {
            return "Runner".equalsIgnoreCase(winner);
        }
        return "Hunters".equalsIgnoreCase(winner);
    }

    private int clampLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private int clampOffset(int offset) {
        return Math.max(0, offset);
    }

    private HeartbeatPayload parseHeartbeat(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, HeartbeatPayload.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored heartbeat JSON could not be parsed.", exception);
        }
    }

    private CompletedMatchPayload parseCompletedMatch(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank() || "null".equalsIgnoreCase(rawPayload.trim())) {
            return null;
        }
        return parseCompletedMatchRequired(rawPayload);
    }

    private CompletedMatchPayload parseCompletedMatchRequired(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, CompletedMatchPayload.class);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored match JSON could not be parsed.", exception);
        }
    }
}
