package com.huntcore.backendapi.model;

public record StoredMatch(
    long matchId,
    String serverId,
    long receivedAtMillis,
    String rawPayload
) {
}
