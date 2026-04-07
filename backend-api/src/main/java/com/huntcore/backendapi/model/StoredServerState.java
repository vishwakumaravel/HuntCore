package com.huntcore.backendapi.model;

public record StoredServerState(
    String serverId,
    long receivedAtMillis,
    String gameState,
    String rawPayload
) {
}
