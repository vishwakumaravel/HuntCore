package com.huntcore.backendapi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huntcore.backendapi.model.StoredMatch;
import com.huntcore.backendapi.model.StoredServerState;
import com.huntcore.backendapi.repository.MatchRepository;
import com.huntcore.backendapi.repository.ServerStateRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class BackendReadController {

    private final ServerStateRepository serverStateRepository;
    private final MatchRepository matchRepository;
    private final ObjectMapper objectMapper;

    public BackendReadController(
        ServerStateRepository serverStateRepository,
        MatchRepository matchRepository,
        ObjectMapper objectMapper
    ) {
        this.serverStateRepository = serverStateRepository;
        this.matchRepository = matchRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/v1/servers")
    public ServerListResponse listServers() {
        List<ServerSummaryResponse> servers = serverStateRepository.findAll().stream()
            .map(server -> new ServerSummaryResponse(server.serverId(), server.receivedAtMillis(), server.gameState()))
            .toList();
        return new ServerListResponse(servers);
    }

    @GetMapping("/api/v1/servers/{serverId}")
    public ServerDetailResponse getServer(@PathVariable String serverId) {
        StoredServerState storedServerState = serverStateRepository.findByServerId(serverId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No heartbeat found for server."));
        return new ServerDetailResponse(
            storedServerState.serverId(),
            storedServerState.receivedAtMillis(),
            parseJson(storedServerState.rawPayload())
        );
    }

    @GetMapping("/api/v1/matches")
    public MatchListResponse listMatches(@RequestParam(defaultValue = "50") int limit) {
        List<MatchResponse> matches = matchRepository.findRecent(limit).stream()
            .map(match -> new MatchResponse(
                match.matchId(),
                match.serverId(),
                match.receivedAtMillis(),
                parseJson(match.rawPayload())
            ))
            .toList();
        return new MatchListResponse(matches);
    }

    private JsonNode parseJson(String rawPayload) {
        try {
            return objectMapper.readTree(rawPayload);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Stored JSON could not be parsed.", exception);
        }
    }

    public record ServerListResponse(List<ServerSummaryResponse> servers) {
    }

    public record ServerSummaryResponse(String serverId, long receivedAtMillis, String gameState) {
    }

    public record ServerDetailResponse(String serverId, long receivedAtMillis, JsonNode heartbeat) {
    }

    public record MatchListResponse(List<MatchResponse> matches) {
    }

    public record MatchResponse(long matchId, String serverId, long receivedAtMillis, JsonNode match) {
    }
}
