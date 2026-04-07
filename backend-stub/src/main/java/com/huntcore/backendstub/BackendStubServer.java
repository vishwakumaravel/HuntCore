package com.huntcore.backendstub;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;

public final class BackendStubServer {

    private final String host;
    private final int port;
    private final String expectedApiKey;
    private final InMemoryBackendStore store;
    private final HttpServer httpServer;

    public BackendStubServer(String host, int port, String expectedApiKey, InMemoryBackendStore store) throws IOException {
        this.host = host;
        this.port = port;
        this.expectedApiKey = expectedApiKey == null ? "" : expectedApiKey.trim();
        this.store = store;
        this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        this.httpServer.setExecutor(Executors.newCachedThreadPool());
        registerContexts();
    }

    public void start() {
        httpServer.start();
        System.out.println("HuntCore backend stub listening on http://" + host + ":" + port);
    }

    public void stop() {
        httpServer.stop(0);
    }

    private void registerContexts() {
        httpServer.createContext("/health", this::handleHealth);
        httpServer.createContext("/api/v1/servers", this::handleServers);
        httpServer.createContext("/api/v1/matches", this::handleMatches);
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!allowMethod(exchange, "GET")) {
            return;
        }
        writeJson(exchange, 200, JsonResponses.healthResponse(port, store));
    }

    private void handleServers(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange, "GET, PUT, OPTIONS")) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        List<String> segments = pathSegments(path);
        if (segments.size() == 3) {
            if (!allowMethod(exchange, "GET")) {
                return;
            }
            writeJson(exchange, 200, JsonResponses.heartbeatsListResponse(store.listHeartbeats()));
            return;
        }

        if (segments.size() == 4) {
            if (!allowMethod(exchange, "GET")) {
                return;
            }
            InMemoryBackendStore.StoredHeartbeat heartbeat = store.getHeartbeat(segments.get(3));
            if (heartbeat == null) {
                writeJson(exchange, 404, JsonResponses.errorResponse(404, "No heartbeat found for server."));
                return;
            }

            writeJson(exchange, 200, JsonResponses.heartbeatDetailResponse(heartbeat));
            return;
        }

        if (segments.size() == 5 && "heartbeat".equals(segments.get(4))) {
            if (!allowMethod(exchange, "PUT")) {
                return;
            }
            if (!authorize(exchange)) {
                return;
            }

            String body = readBody(exchange);
            if (body.isBlank()) {
                writeJson(exchange, 400, JsonResponses.errorResponse(400, "Heartbeat body must not be blank."));
                return;
            }

            InMemoryBackendStore.StoredHeartbeat heartbeat = store.putHeartbeat(segments.get(3), body);
            writeJson(exchange, 200, JsonResponses.heartbeatAccepted(heartbeat.serverId(), heartbeat.receivedAtMillis()));
            return;
        }

        writeJson(exchange, 404, JsonResponses.errorResponse(404, "Unknown server endpoint."));
    }

    private void handleMatches(HttpExchange exchange) throws IOException {
        if (handlePreflight(exchange, "GET, POST, OPTIONS")) {
            return;
        }

        if ("GET".equals(exchange.getRequestMethod())) {
            int limit = parseLimit(exchange.getRequestURI().getQuery());
            writeJson(exchange, 200, JsonResponses.matchesListResponse(store.listMatches(limit)));
            return;
        }

        if ("POST".equals(exchange.getRequestMethod())) {
            if (!authorize(exchange)) {
                return;
            }

            String body = readBody(exchange);
            if (body.isBlank()) {
                writeJson(exchange, 400, JsonResponses.errorResponse(400, "Match body must not be blank."));
                return;
            }

            InMemoryBackendStore.StoredMatch storedMatch = store.appendMatch(body);
            writeJson(exchange, 201, JsonResponses.matchAccepted(storedMatch.matchId(), storedMatch.receivedAtMillis()));
            return;
        }

        writeJson(exchange, 405, JsonResponses.errorResponse(405, "Method not allowed."));
    }

    private boolean authorize(HttpExchange exchange) throws IOException {
        if (expectedApiKey.isBlank()) {
            return true;
        }

        String actualApiKey = exchange.getRequestHeaders().getFirst("X-HuntCore-Api-Key");
        if (expectedApiKey.equals(actualApiKey)) {
            return true;
        }

        writeJson(exchange, 401, JsonResponses.errorResponse(401, "Unauthorized."));
        return false;
    }

    private boolean allowMethod(HttpExchange exchange, String allowedMethod) throws IOException {
        if (handlePreflight(exchange, allowedMethod + ", OPTIONS")) {
            return false;
        }

        if (allowedMethod.equals(exchange.getRequestMethod())) {
            return true;
        }

        writeJson(exchange, 405, JsonResponses.errorResponse(405, "Method not allowed."));
        return false;
    }

    private boolean handlePreflight(HttpExchange exchange, String allowedMethods) throws IOException {
        addCorsHeaders(exchange.getResponseHeaders(), allowedMethods);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] responseBody = JsonResponses.jsonBytes(body);
        Headers headers = exchange.getResponseHeaders();
        addCorsHeaders(headers, "GET, PUT, POST, OPTIONS");
        headers.set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private void addCorsHeaders(Headers headers, String allowedMethods) {
        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Headers", "Content-Type, X-HuntCore-Api-Key");
        headers.set("Access-Control-Allow-Methods", allowedMethods);
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> pathSegments(String path) {
        return java.util.Arrays.stream(path.split("/"))
            .filter(segment -> !segment.isBlank())
            .toList();
    }

    private int parseLimit(String query) {
        if (query == null || query.isBlank()) {
            return 50;
        }

        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && "limit".equals(pair[0])) {
                try {
                    return Integer.parseInt(pair[1]);
                } catch (NumberFormatException ignored) {
                    return 50;
                }
            }
        }

        return 50;
    }
}
