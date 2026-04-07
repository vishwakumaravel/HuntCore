package com.huntcore.backendstub;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryBackendStore {

    private static final int MAX_STORED_MATCHES = 200;

    private final Map<String, StoredHeartbeat> heartbeats = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<StoredMatch> matches = new ConcurrentLinkedDeque<>();
    private final AtomicLong matchSequence = new AtomicLong(1L);
    private final MatchPersistence matchPersistence;

    public InMemoryBackendStore(MatchPersistence matchPersistence) {
        this.matchPersistence = matchPersistence;
    }

    public void loadPersistedMatches() {
        if (matchPersistence == null) {
            return;
        }

        try {
            List<StoredMatch> persistedMatches = matchPersistence.loadMatches();
            matches.clear();

            long maxMatchId = 0L;
            for (StoredMatch match : persistedMatches) {
                matches.add(match);
                maxMatchId = Math.max(maxMatchId, match.matchId());
            }

            trimStoredMatches();
            matchSequence.set(maxMatchId + 1L);
        } catch (java.io.IOException exception) {
            System.err.println("Failed to load persisted matches from " + matchPersistence.storageFile() + ": " + exception.getMessage());
        }
    }

    public StoredHeartbeat putHeartbeat(String serverId, String body) {
        StoredHeartbeat heartbeat = new StoredHeartbeat(serverId, System.currentTimeMillis(), body);
        heartbeats.put(serverId, heartbeat);
        return heartbeat;
    }

    public StoredHeartbeat getHeartbeat(String serverId) {
        return heartbeats.get(serverId);
    }

    public List<StoredHeartbeat> listHeartbeats() {
        return heartbeats.values().stream()
            .sorted(Comparator.comparingLong(StoredHeartbeat::receivedAtMillis).reversed())
            .toList();
    }

    public synchronized StoredMatch appendMatch(String body) {
        StoredMatch storedMatch = new StoredMatch(matchSequence.getAndIncrement(), System.currentTimeMillis(), body);
        matches.addFirst(storedMatch);
        trimStoredMatches();
        persistMatches();

        return storedMatch;
    }

    public List<StoredMatch> listMatches(int limit) {
        int clampedLimit = Math.max(1, Math.min(limit, MAX_STORED_MATCHES));
        List<StoredMatch> results = new ArrayList<>(clampedLimit);
        int index = 0;
        for (StoredMatch match : matches) {
            if (index >= clampedLimit) {
                break;
            }
            results.add(match);
            index++;
        }
        return results;
    }

    public int getHeartbeatCount() {
        return heartbeats.size();
    }

    public int getMatchCount() {
        return matches.size();
    }

    private void trimStoredMatches() {
        while (matches.size() > MAX_STORED_MATCHES) {
            matches.pollLast();
        }
    }

    private void persistMatches() {
        if (matchPersistence == null) {
            return;
        }

        try {
            matchPersistence.saveMatches(matches);
        } catch (java.io.IOException exception) {
            System.err.println("Failed to persist matches to " + matchPersistence.storageFile() + ": " + exception.getMessage());
        }
    }

    public record StoredHeartbeat(String serverId, long receivedAtMillis, String body) {
    }

    public record StoredMatch(long matchId, long receivedAtMillis, String body) {
    }
}
