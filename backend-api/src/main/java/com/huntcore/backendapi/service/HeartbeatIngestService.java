package com.huntcore.backendapi.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huntcore.backendapi.ingest.HeartbeatPayload;
import com.huntcore.backendapi.model.StoredHeartbeatReceipt;
import com.huntcore.backendapi.repository.ServerStateRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class HeartbeatIngestService {

    private final ServerStateRepository serverStateRepository;
    private final ObjectMapper objectMapper;

    public HeartbeatIngestService(ServerStateRepository serverStateRepository, ObjectMapper objectMapper) {
        this.serverStateRepository = serverStateRepository;
        this.objectMapper = objectMapper;
    }

    public StoredHeartbeatReceipt ingest(String serverId, HeartbeatPayload payload, String rawPayload) {
        String normalizedServerId = normalizeServerId(serverId);
        Instant receivedAt = Instant.now();
        long receivedAtMillis = serverStateRepository.upsert(
            normalizedServerId,
            payload,
            rawPayload,
            writeJson(payload.activeMatch()),
            writeJson(payload.latestCompletedMatch()),
            receivedAt
        );
        return new StoredHeartbeatReceipt(normalizedServerId, receivedAtMillis);
    }

    private String normalizeServerId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return "unknown-server";
        }
        return serverId.trim();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize heartbeat payload.", exception);
        }
    }
}
