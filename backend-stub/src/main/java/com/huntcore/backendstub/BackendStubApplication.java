package com.huntcore.backendstub;

import java.nio.file.Path;

public final class BackendStubApplication {

    private BackendStubApplication() {
    }

    public static void main(String[] args) throws Exception {
        String host = readStringEnv("HUNTCORE_HOST", "0.0.0.0");
        int port = readPort();
        String apiKey = readStringEnv("HUNTCORE_API_KEY", "");
        Path dataDirectory = Path.of(readStringEnv("HUNTCORE_DATA_DIR", "data"));

        InMemoryBackendStore store = new InMemoryBackendStore(new MatchPersistence(dataDirectory.resolve("matches.log")));
        store.loadPersistedMatches();
        BackendStubServer server = new BackendStubServer(host, port, apiKey, store);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }

    private static int readPort() {
        String configured = readStringEnv("HUNTCORE_PORT", readStringEnv("PORT", "8080"));
        try {
            return Integer.parseInt(configured);
        } catch (NumberFormatException exception) {
            System.err.println("Invalid port '" + configured + "', defaulting to 8080.");
            return 8080;
        }
    }

    private static String readStringEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
