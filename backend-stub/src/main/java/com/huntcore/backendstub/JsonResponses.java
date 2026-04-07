package com.huntcore.backendstub;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public final class JsonResponses {

    private JsonResponses() {
    }

    public static byte[] jsonBytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    public static String healthResponse(int port, InMemoryBackendStore store) {
        return "{"
            + "\"status\":\"ok\","
            + "\"service\":\"huntcore-backend-stub\","
            + "\"port\":" + port + ","
            + "\"heartbeatCount\":" + store.getHeartbeatCount() + ","
            + "\"matchCount\":" + store.getMatchCount()
            + "}";
    }

    public static String heartbeatAccepted(String serverId, long receivedAtMillis) {
        return "{"
            + "\"status\":\"stored\","
            + "\"serverId\":\"" + escape(serverId) + "\","
            + "\"receivedAtMillis\":" + receivedAtMillis
            + "}";
    }

    public static String matchAccepted(long matchId, long receivedAtMillis) {
        return "{"
            + "\"status\":\"stored\","
            + "\"matchId\":" + matchId + ","
            + "\"receivedAtMillis\":" + receivedAtMillis
            + "}";
    }

    public static String heartbeatsListResponse(Iterable<InMemoryBackendStore.StoredHeartbeat> heartbeats) {
        StringBuilder json = new StringBuilder();
        json.append("{\"servers\":[");
        Iterator<InMemoryBackendStore.StoredHeartbeat> iterator = heartbeats.iterator();
        while (iterator.hasNext()) {
            InMemoryBackendStore.StoredHeartbeat heartbeat = iterator.next();
            json.append("{")
                .append("\"serverId\":\"").append(escape(heartbeat.serverId())).append("\",")
                .append("\"receivedAtMillis\":").append(heartbeat.receivedAtMillis())
                .append("}");
            if (iterator.hasNext()) {
                json.append(',');
            }
        }
        json.append("]}");
        return json.toString();
    }

    public static String heartbeatDetailResponse(InMemoryBackendStore.StoredHeartbeat heartbeat) {
        return "{"
            + "\"serverId\":\"" + escape(heartbeat.serverId()) + "\","
            + "\"receivedAtMillis\":" + heartbeat.receivedAtMillis() + ","
            + "\"heartbeat\":" + rawJsonOrQuotedString(heartbeat.body())
            + "}";
    }

    public static String matchesListResponse(Iterable<InMemoryBackendStore.StoredMatch> matches) {
        StringBuilder json = new StringBuilder();
        json.append("{\"matches\":[");
        Iterator<InMemoryBackendStore.StoredMatch> iterator = matches.iterator();
        while (iterator.hasNext()) {
            InMemoryBackendStore.StoredMatch match = iterator.next();
            json.append("{")
                .append("\"matchId\":").append(match.matchId()).append(',')
                .append("\"receivedAtMillis\":").append(match.receivedAtMillis()).append(',')
                .append("\"match\":").append(rawJsonOrQuotedString(match.body()))
                .append("}");
            if (iterator.hasNext()) {
                json.append(',');
            }
        }
        json.append("]}");
        return json.toString();
    }

    public static String errorResponse(int status, String message) {
        return "{"
            + "\"status\":" + status + ","
            + "\"error\":\"" + escape(message) + "\""
            + "}";
    }

    private static String rawJsonOrQuotedString(String value) {
        String trimmed = value == null ? "" : value.trim();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return trimmed;
        }

        return "\"" + escape(trimmed) + "\"";
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
