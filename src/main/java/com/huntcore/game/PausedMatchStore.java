package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import com.huntcore.world.MatchWorldSet;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PausedMatchStore {

    private final File storageFile;

    public PausedMatchStore(HuntCorePlugin plugin) {
        this.storageFile = new File(plugin.getDataFolder(), "paused-match.yml");
    }

    public void save(PausedMatchSnapshot snapshot) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        config.set("runner-id", snapshot.runnerId().toString());
        config.set("hunter-ids", snapshot.hunterIds().stream().map(UUID::toString).toList());
        config.set("match-world-base-name", snapshot.matchWorldSet().getBaseName());
        config.set("started-at-millis", snapshot.startedAtMillis());
        config.set("resume-state", snapshot.resumeState().name());
        config.set("head-start-seconds-remaining", snapshot.headStartSecondsRemaining());
        writeLocation(config.createSection("match-spawn"), snapshot.matchSpawn());
        writeLocation(config.createSection("runner-last-known"), snapshot.runnerLastKnownLocation());

        ConfigurationSection huntersSection = config.createSection("hunter-last-known");
        for (var entry : snapshot.hunterLastKnownLocations().entrySet()) {
            StoredLocation location = entry.getValue();
            if (location == null || location.worldName().isBlank()) {
                continue;
            }

            writeLocation(huntersSection.createSection(entry.getKey().toString()), location);
        }

        config.save(storageFile);
    }

    public PausedMatchSnapshot load() {
        if (!storageFile.exists()) {
            return null;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
            String runnerIdText = config.getString("runner-id", "");
            List<String> hunterIdTexts = config.getStringList("hunter-ids");
            String baseName = config.getString("match-world-base-name", "");
            String resumeStateText = config.getString("resume-state", GameState.IN_GAME.name());
            if (runnerIdText.isBlank() || hunterIdTexts.isEmpty() || baseName.isBlank()) {
                return null;
            }

            UUID runnerId = UUID.fromString(runnerIdText);
            List<UUID> hunterIds = new ArrayList<>();
            for (String hunterIdText : hunterIdTexts) {
                hunterIds.add(UUID.fromString(hunterIdText));
            }

            StoredLocation matchSpawn = readLocation(config.getConfigurationSection("match-spawn"));
            if (matchSpawn == null || matchSpawn.worldName().isBlank()) {
                return null;
            }

            MatchWorldSet worldSet = new MatchWorldSet(baseName);
            var hunterLastKnown = new java.util.HashMap<UUID, StoredLocation>();
            ConfigurationSection huntersSection = config.getConfigurationSection("hunter-last-known");
            if (huntersSection != null) {
                for (String key : huntersSection.getKeys(false)) {
                    StoredLocation location = readLocation(huntersSection.getConfigurationSection(key));
                    if (location != null && !location.worldName().isBlank()) {
                        hunterLastKnown.put(UUID.fromString(key), location);
                    }
                }
            }

            return new PausedMatchSnapshot(
                runnerId,
                hunterIds,
                worldSet,
                matchSpawn,
                readLocation(config.getConfigurationSection("runner-last-known")),
                hunterLastKnown,
                config.getLong("started-at-millis", System.currentTimeMillis()),
                GameState.valueOf(resumeStateText),
                Math.max(0, config.getInt("head-start-seconds-remaining", 0))
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public void delete() {
        if (storageFile.exists()) {
            storageFile.delete();
        }
    }

    public boolean exists() {
        return storageFile.exists();
    }

    private void writeLocation(ConfigurationSection section, StoredLocation location) {
        if (section == null || location == null || location.worldName().isBlank()) {
            return;
        }

        section.set("world", location.worldName());
        section.set("x", location.x());
        section.set("y", location.y());
        section.set("z", location.z());
        section.set("yaw", location.yaw());
        section.set("pitch", location.pitch());
    }

    private StoredLocation readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }

        return new StoredLocation(
            worldName,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch")
        );
    }

    public record PausedMatchSnapshot(
        UUID runnerId,
        List<UUID> hunterIds,
        MatchWorldSet matchWorldSet,
        StoredLocation matchSpawn,
        StoredLocation runnerLastKnownLocation,
        java.util.Map<UUID, StoredLocation> hunterLastKnownLocations,
        long startedAtMillis,
        GameState resumeState,
        int headStartSecondsRemaining
    ) {
    }

    public record StoredLocation(String worldName, double x, double y, double z, float yaw, float pitch) {
    }
}
