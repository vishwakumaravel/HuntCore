package com.huntcore.backendapi.service;

import com.huntcore.backendapi.ingest.CompletedMatchPayload;
import com.huntcore.backendapi.model.MatchPlayerRow;
import com.huntcore.backendapi.model.StoredMatch;
import com.huntcore.backendapi.model.StoredMatchReceipt;
import com.huntcore.backendapi.repository.MatchRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchIngestService {

    private final MatchRepository matchRepository;

    public MatchIngestService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Transactional
    public StoredMatchReceipt ingest(String serverId, CompletedMatchPayload payload, String rawPayload) {
        String normalizedServerId = normalizeServerId(serverId);
        StoredMatch storedMatch = matchRepository.insert(
            normalizedServerId,
            payload,
            rawPayload,
            Instant.now(),
            buildMatchPlayers(payload)
        );
        return new StoredMatchReceipt(storedMatch.matchId(), storedMatch.serverId(), storedMatch.receivedAtMillis());
    }

    private String normalizeServerId(String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return "unknown-server";
        }
        return serverId.trim();
    }

    private List<MatchPlayerRow> buildMatchPlayers(CompletedMatchPayload payload) {
        List<MatchPlayerRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : payload.playerKills().entrySet()) {
            String role = entry.getKey().equals(payload.runnerName()) ? "RUNNER" : "HUNTER";
            rows.add(new MatchPlayerRow(entry.getKey(), role, Math.max(0, entry.getValue())));
        }
        return rows;
    }
}
