package com.huntcore.backendapi.model;

public record StoredMatchReceipt(long matchId, String serverId, long receivedAtMillis) {
}
