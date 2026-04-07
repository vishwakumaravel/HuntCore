package com.huntcore.backendstub;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class MatchPersistence {

    private static final String FIELD_SEPARATOR = "\t";

    private final Path storageFile;

    public MatchPersistence(Path storageFile) {
        this.storageFile = storageFile;
    }

    public List<InMemoryBackendStore.StoredMatch> loadMatches() throws IOException {
        if (!Files.exists(storageFile)) {
            return List.of();
        }

        List<InMemoryBackendStore.StoredMatch> matches = new ArrayList<>();
        for (String line : Files.readAllLines(storageFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }

            InMemoryBackendStore.StoredMatch match = parseLine(line);
            if (match != null) {
                matches.add(match);
            }
        }
        return matches;
    }

    public void saveMatches(Iterable<InMemoryBackendStore.StoredMatch> matches) throws IOException {
        Path parent = storageFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<String> lines = new ArrayList<>();
        for (InMemoryBackendStore.StoredMatch match : matches) {
            lines.add(serializeLine(match));
        }

        Files.write(
            storageFile,
            lines,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        );
    }

    public Path storageFile() {
        return storageFile;
    }

    private String serializeLine(InMemoryBackendStore.StoredMatch match) {
        String encodedBody = Base64.getEncoder().encodeToString(match.body().getBytes(StandardCharsets.UTF_8));
        return match.matchId() + FIELD_SEPARATOR + match.receivedAtMillis() + FIELD_SEPARATOR + encodedBody;
    }

    private InMemoryBackendStore.StoredMatch parseLine(String line) {
        String[] parts = line.split(FIELD_SEPARATOR, 3);
        if (parts.length != 3) {
            return null;
        }

        try {
            long matchId = Long.parseLong(parts[0]);
            long receivedAtMillis = Long.parseLong(parts[1]);
            String body = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            return new InMemoryBackendStore.StoredMatch(matchId, receivedAtMillis, body);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
