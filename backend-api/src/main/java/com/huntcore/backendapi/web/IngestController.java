package com.huntcore.backendapi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huntcore.backendapi.ingest.CompletedMatchPayload;
import com.huntcore.backendapi.ingest.HeartbeatPayload;
import com.huntcore.backendapi.model.StoredHeartbeatReceipt;
import com.huntcore.backendapi.model.StoredMatchReceipt;
import com.huntcore.backendapi.service.HeartbeatIngestService;
import com.huntcore.backendapi.service.MatchIngestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class IngestController {

    private final ObjectMapper objectMapper;
    private final HeartbeatIngestService heartbeatIngestService;
    private final MatchIngestService matchIngestService;

    public IngestController(
        ObjectMapper objectMapper,
        HeartbeatIngestService heartbeatIngestService,
        MatchIngestService matchIngestService
    ) {
        this.objectMapper = objectMapper;
        this.heartbeatIngestService = heartbeatIngestService;
        this.matchIngestService = matchIngestService;
    }

    @PutMapping("/api/v1/servers/{serverId}/heartbeat")
    public StoredHeartbeatResponse ingestHeartbeat(@PathVariable String serverId, @RequestBody JsonNode body) {
        HeartbeatPayload payload = readValue(body, HeartbeatPayload.class);
        StoredHeartbeatReceipt receipt = heartbeatIngestService.ingest(serverId, payload, writeValue(body));
        return new StoredHeartbeatResponse("stored", receipt.serverId(), receipt.receivedAtMillis());
    }

    @PostMapping("/api/v1/matches")
    @ResponseStatus(HttpStatus.CREATED)
    public StoredMatchResponse ingestMatch(
        @RequestHeader(name = "X-HuntCore-Server-Id", required = false) String serverId,
        @RequestBody JsonNode body
    ) {
        CompletedMatchPayload payload = readValue(body, CompletedMatchPayload.class);
        StoredMatchReceipt receipt = matchIngestService.ingest(serverId, payload, writeValue(body));
        return new StoredMatchResponse("stored", receipt.matchId(), receipt.serverId(), receipt.receivedAtMillis());
    }

    private <T> T readValue(JsonNode body, Class<T> type) {
        try {
            return objectMapper.treeToValue(body, type);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload.", exception);
        }
    }

    private String writeValue(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Payload could not be serialized.", exception);
        }
    }

    public record StoredHeartbeatResponse(String status, String serverId, long receivedAtMillis) {
    }

    public record StoredMatchResponse(String status, long matchId, String serverId, long receivedAtMillis) {
    }
}
