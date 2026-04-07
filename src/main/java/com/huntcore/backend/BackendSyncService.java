package com.huntcore.backend;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import com.huntcore.game.GameManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class BackendSyncService implements BackendSyncSink {

    private static final long HEARTBEAT_ERROR_LOG_INTERVAL_MILLIS = 60_000L;

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;
    private final GameManager gameManager;
    private final Object heartbeatLock = new Object();

    private HttpClient httpClient;
    private BukkitTask heartbeatTask;
    private boolean enabled;
    private boolean heartbeatInFlight;
    private boolean heartbeatPending;
    private String normalizedBaseUrl = "";
    private String serverId = "";
    private String apiKey = "";
    private int timeoutMillis;
    private String lastHeartbeatFailureMessage;
    private long lastHeartbeatFailureAtMillis;

    public BackendSyncService(HuntCorePlugin plugin, PluginConfig pluginConfig, GameManager gameManager) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.gameManager = gameManager;
    }

    public void start() {
        if (heartbeatTask != null || enabled) {
            return;
        }

        if (!pluginConfig.isBackendEnabled()) {
            return;
        }

        String baseUrl = pluginConfig.getBackendBaseUrl();
        String configuredServerId = pluginConfig.getBackendServerId();
        if (baseUrl.isBlank() || configuredServerId.isBlank()) {
            plugin.getLogger().warning("Backend sync is enabled, but backend.base-url or backend.server-id is blank. Sync is disabled.");
            return;
        }

        this.enabled = true;
        this.normalizedBaseUrl = stripTrailingSlash(baseUrl);
        this.serverId = configuredServerId;
        this.apiKey = pluginConfig.getBackendApiKey();
        this.timeoutMillis = pluginConfig.getBackendTimeoutMillis();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMillis))
            .build();

        long intervalTicks = Math.max(20L, pluginConfig.getBackendHeartbeatSeconds() * 20L);
        this.heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, this::requestHeartbeat, intervalTicks, intervalTicks);
        plugin.getLogger().info("Backend sync enabled for server '" + serverId + "'.");
        Bukkit.getScheduler().runTask(plugin, this::requestHeartbeat);
    }

    public void shutdown() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }

        enabled = false;
        synchronized (heartbeatLock) {
            heartbeatInFlight = false;
            heartbeatPending = false;
        }
    }

    @Override
    public void requestHeartbeat() {
        if (!enabled) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, this::requestHeartbeat);
            }
            return;
        }

        synchronized (heartbeatLock) {
            if (heartbeatInFlight) {
                heartbeatPending = true;
                return;
            }
            heartbeatInFlight = true;
        }

        ServerStatusSnapshot snapshot = gameManager.buildServerStatusSnapshot();
        java.util.concurrent.CompletableFuture<Void> request;
        try {
            request = sendHeartbeat(snapshot);
        } catch (RuntimeException exception) {
            completeHeartbeat(exception);
            return;
        }

        request.whenComplete((ignored, throwable) -> {
            if (!plugin.isEnabled()) {
                synchronized (heartbeatLock) {
                    heartbeatInFlight = false;
                    heartbeatPending = false;
                }
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> completeHeartbeat(throwable));
        });
    }

    @Override
    public void publishCompletedMatch(CompletedMatchSnapshot snapshot) {
        if (!enabled || snapshot == null) {
            return;
        }

        java.util.concurrent.CompletableFuture<Void> request;
        try {
            request = sendCompletedMatch(snapshot);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Failed to publish completed match: " + summarizeFailure(exception));
            return;
        }

        request.whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                plugin.getLogger().warning("Failed to publish completed match: " + summarizeFailure(throwable));
            }
        });
    }

    private void completeHeartbeat(Throwable throwable) {
        if (throwable == null) {
            lastHeartbeatFailureMessage = null;
            lastHeartbeatFailureAtMillis = 0L;
        } else {
            logHeartbeatFailure("Failed to sync backend heartbeat: " + summarizeFailure(throwable));
        }

        boolean shouldFlushPending;
        synchronized (heartbeatLock) {
            heartbeatInFlight = false;
            shouldFlushPending = heartbeatPending;
            heartbeatPending = false;
        }

        if (shouldFlushPending) {
            requestHeartbeat();
        }
    }

    private java.util.concurrent.CompletableFuture<Void> sendHeartbeat(ServerStatusSnapshot snapshot) {
        return sendJsonRequest(
            buildHeartbeatUri(),
            "PUT",
            toJson(snapshot)
        );
    }

    private java.util.concurrent.CompletableFuture<Void> sendCompletedMatch(CompletedMatchSnapshot snapshot) {
        return sendJsonRequest(
            buildMatchesUri(),
            "POST",
            toJson(snapshot)
        );
    }

    private java.util.concurrent.CompletableFuture<Void> sendJsonRequest(URI uri, String method, String body) {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMillis(timeoutMillis))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-HuntCore-Server-Id", serverId);

        if (!apiKey.isBlank()) {
            requestBuilder.header("X-HuntCore-Api-Key", apiKey);
        }

        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8);
        if ("PUT".equals(method)) {
            requestBuilder.PUT(publisher);
        } else {
            requestBuilder.POST(publisher);
        }

        return httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
            .thenAccept(response -> {
                if (response.statusCode() >= 300) {
                    throw new IllegalStateException("HTTP " + response.statusCode() + " from " + uri);
                }
            });
    }

    private URI buildHeartbeatUri() {
        return URI.create(normalizedBaseUrl + "/api/v1/servers/" + encodePathSegment(serverId) + "/heartbeat");
    }

    private URI buildMatchesUri() {
        return URI.create(normalizedBaseUrl + "/api/v1/matches");
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String stripTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private void logHeartbeatFailure(String message) {
        long now = System.currentTimeMillis();
        if (message.equals(lastHeartbeatFailureMessage)
            && now - lastHeartbeatFailureAtMillis < HEARTBEAT_ERROR_LOG_INTERVAL_MILLIS) {
            return;
        }

        lastHeartbeatFailureMessage = message;
        lastHeartbeatFailureAtMillis = now;
        plugin.getLogger().warning(message);
    }

    private String summarizeFailure(Throwable throwable) {
        Throwable failure = throwable instanceof java.util.concurrent.CompletionException && throwable.getCause() != null
            ? throwable.getCause()
            : throwable;
        return failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
    }

    private String toJson(ServerStatusSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendJsonField(json, "capturedAtMillis", snapshot.capturedAtMillis());
        appendJsonField(json, "pluginVersion", snapshot.pluginVersion());
        appendJsonField(json, "serverSoftware", snapshot.serverSoftware());
        appendJsonField(json, "serverVersion", snapshot.serverVersion());
        appendJsonField(json, "gameState", snapshot.gameState());
        appendJsonField(json, "preparedMatchCount", snapshot.preparedMatchCount());
        appendJsonField(json, "scoutingActive", snapshot.scoutingActive());
        appendJsonField(json, "queuedRunnerCount", snapshot.queuedRunnerCount());
        appendJsonField(json, "queuedHunterCount", snapshot.queuedHunterCount());
        appendJsonField(json, "spectatorCount", snapshot.spectatorCount());
        appendJsonField(json, "queuedCount", snapshot.queuedCount());
        appendJsonField(json, "readyCount", snapshot.readyCount());
        appendJsonField(json, "headStartSecondsRemaining", snapshot.headStartSecondsRemaining());
        appendJsonField(json, "pausedResumeState", snapshot.pausedResumeState());
        appendJsonField(json, "activeMatch", snapshot.activeMatch());
        appendJsonField(json, "latestCompletedMatch", snapshot.latestCompletedMatch());
        closeJsonObject(json);
        return json.toString();
    }

    private String toJson(CompletedMatchSnapshot snapshot) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        appendJsonField(json, "endedAtMillis", snapshot.endedAtMillis());
        appendJsonField(json, "durationMillis", snapshot.durationMillis());
        appendJsonField(json, "winner", snapshot.winner());
        appendJsonField(json, "reason", snapshot.reason());
        appendJsonField(json, "runnerName", snapshot.runnerName());
        appendJsonField(json, "hunterCount", snapshot.hunterCount());
        appendJsonField(json, "poiName", snapshot.poiName());
        appendJsonField(json, "poiDistanceBlocks", snapshot.poiDistanceBlocks());
        appendJsonField(json, "matchWorldBaseName", snapshot.matchWorldBaseName());
        appendJsonField(json, "playerKills", snapshot.playerKills());
        closeJsonObject(json);
        return json.toString();
    }

    private void appendJsonField(StringBuilder json, String key, Object value) {
        if (json.charAt(json.length() - 1) != '{') {
            json.append(',');
        }

        json.append('"').append(escapeJson(key)).append('"').append(':');
        appendJsonValue(json, value);
    }

    private void appendJsonValue(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
            return;
        }

        if (value instanceof String stringValue) {
            json.append('"').append(escapeJson(stringValue)).append('"');
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            json.append(value);
            return;
        }

        if (value instanceof ActiveMatchSnapshot activeMatchSnapshot) {
            json.append('{');
            appendJsonField(json, "runnerName", activeMatchSnapshot.runnerName());
            appendJsonField(json, "hunterNames", activeMatchSnapshot.hunterNames());
            appendJsonField(json, "hunterCount", activeMatchSnapshot.hunterCount());
            appendJsonField(json, "poiName", activeMatchSnapshot.poiName());
            appendJsonField(json, "poiDistanceBlocks", activeMatchSnapshot.poiDistanceBlocks());
            appendJsonField(json, "matchWorldBaseName", activeMatchSnapshot.matchWorldBaseName());
            appendJsonField(json, "startedAtMillis", activeMatchSnapshot.startedAtMillis());
            closeJsonObject(json);
            return;
        }

        if (value instanceof CompletedMatchSnapshot completedMatchSnapshot) {
            json.append(toJson(completedMatchSnapshot));
            return;
        }

        if (value instanceof Iterable<?> iterable) {
            json.append('[');
            Iterator<?> iterator = iterable.iterator();
            while (iterator.hasNext()) {
                appendJsonValue(json, iterator.next());
                if (iterator.hasNext()) {
                    json.append(',');
                }
            }
            json.append(']');
            return;
        }

        if (value instanceof Map<?, ?> map) {
            json.append('{');
            Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<?, ?> entry = iterator.next();
                json.append('"').append(escapeJson(String.valueOf(entry.getKey()))).append('"').append(':');
                appendJsonValue(json, entry.getValue());
                if (iterator.hasNext()) {
                    json.append(',');
                }
            }
            json.append('}');
            return;
        }

        json.append('"').append(escapeJson(String.valueOf(value))).append('"');
    }

    private void closeJsonObject(StringBuilder json) {
        json.append('}');
    }

    private String escapeJson(String value) {
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
