package com.huntcore.world;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public final class LobbyMapInstaller {

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;

    public LobbyMapInstaller(HuntCorePlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
    }

    public String getConfiguredZipPath() {
        return pluginConfig.getLobbyMapZipPath();
    }

    public String getConfiguredWorldName() {
        return sanitizeWorldName(pluginConfig.getLobbyMapWorldName());
    }

    public InstallResult installFromZip(String zipPathString, String worldName) throws IOException {
        Path zipPath = toZipPath(zipPathString);
        String sanitizedWorldName = sanitizeWorldName(worldName);
        if (sanitizedWorldName.isBlank()) {
            throw new IllegalArgumentException("Lobby world name cannot be blank.");
        }

        if (Bukkit.getWorld(sanitizedWorldName) != null) {
            throw new IllegalStateException("A world named " + sanitizedWorldName + " is already loaded.");
        }

        Path worldDirectory = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize().resolve(sanitizedWorldName);
        if (Files.exists(worldDirectory)) {
            throw new IllegalStateException("The world folder " + sanitizedWorldName + " already exists.");
        }

        Files.createDirectories(worldDirectory);
        String archiveRoot = null;
        try {
            try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
                archiveRoot = findArchiveRoot(zipFile);
                extractWorldArchive(zipFile, archiveRoot, worldDirectory);
            }

            Files.deleteIfExists(worldDirectory.resolve("uid.dat"));
            Files.deleteIfExists(worldDirectory.resolve("session.lock"));

            World importedWorld = Bukkit.createWorld(new WorldCreator(sanitizedWorldName));
            if (importedWorld == null) {
                throw new IllegalStateException("The imported lobby world could not be loaded.");
            }

            pluginConfig.setLobbyToWorldSpawn(importedWorld);
            return new InstallResult(sanitizedWorldName, zipPath, archiveRoot);
        } catch (Exception exception) {
            deleteDirectoryIfPresent(worldDirectory);
            if (exception instanceof IOException ioException) {
                throw ioException;
            }

            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private Path toZipPath(String zipPathString) {
        if (zipPathString == null || zipPathString.isBlank()) {
            throw new IllegalArgumentException("No lobby map zip path was provided.");
        }

        try {
            Path zipPath = Path.of(zipPathString);
            if (!Files.exists(zipPath)) {
                throw new IllegalArgumentException("Lobby map zip was not found: " + zipPathString);
            }
            if (!Files.isRegularFile(zipPath)) {
                throw new IllegalArgumentException("Lobby map path is not a file: " + zipPathString);
            }
            if (!zipPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                throw new IllegalArgumentException("Lobby map file must be a .zip archive.");
            }
            return zipPath;
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Lobby map zip path is invalid.");
        }
    }

    private String sanitizeWorldName(String worldName) {
        if (worldName == null) {
            return "";
        }

        return worldName.trim().replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String findArchiveRoot(ZipFile zipFile) {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }

            String name = entry.getName();
            if (name.equals("level.dat")) {
                return "";
            }

            if (name.endsWith("/level.dat")) {
                return name.substring(0, name.length() - "level.dat".length());
            }
        }

        throw new IllegalArgumentException("The zip does not contain a Minecraft world with level.dat.");
    }

    private void extractWorldArchive(ZipFile zipFile, String archiveRoot, Path targetDirectory) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryName = entry.getName();
            if (!entryName.startsWith(archiveRoot)) {
                continue;
            }

            String relativeName = entryName.substring(archiveRoot.length());
            if (relativeName.isBlank()) {
                continue;
            }

            if (relativeName.equals("uid.dat") || relativeName.equals("session.lock")) {
                continue;
            }

            Path outputPath = targetDirectory.resolve(relativeName).normalize();
            if (!outputPath.startsWith(targetDirectory)) {
                throw new IOException("Lobby map zip contains an invalid path: " + relativeName);
            }

            if (entry.isDirectory()) {
                Files.createDirectories(outputPath);
                continue;
            }

            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void deleteDirectoryIfPresent(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    plugin.getLogger().warning("Failed to clean up lobby world path " + path + ": " + exception.getMessage());
                }
            });
        }
    }

    public record InstallResult(String worldName, Path zipPath, String archiveRoot) {
    }
}
