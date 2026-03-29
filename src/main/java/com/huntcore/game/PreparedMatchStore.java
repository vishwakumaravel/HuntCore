package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import com.huntcore.world.MatchWorldSet;
import com.huntcore.world.StructureHint;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PreparedMatchStore {

    private final File storageFile;

    public PreparedMatchStore(HuntCorePlugin plugin) {
        this.storageFile = new File(plugin.getDataFolder(), "prepared-matches.yml");
    }

    public void save(List<PreparedMatchSnapshot> snapshots) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection matchesSection = config.createSection("matches");
        for (int index = 0; index < snapshots.size(); index++) {
            PreparedMatchSnapshot snapshot = snapshots.get(index);
            ConfigurationSection section = matchesSection.createSection(Integer.toString(index));
            section.set("match-world-base-name", snapshot.matchWorldSet().getBaseName());
            writeLocation(section.createSection("match-spawn"), snapshot.matchSpawn());
            writeHint(section.createSection("structure-hint"), snapshot.structureHint());
        }

        config.save(storageFile);
    }

    public List<PreparedMatchSnapshot> load() {
        if (!storageFile.exists()) {
            return List.of();
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection matchesSection = config.getConfigurationSection("matches");
            if (matchesSection == null) {
                return List.of();
            }

            List<PreparedMatchSnapshot> snapshots = new ArrayList<>();
            for (String key : matchesSection.getKeys(false)) {
                ConfigurationSection section = matchesSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                String baseName = section.getString("match-world-base-name", "");
                PausedMatchStore.StoredLocation matchSpawn = readLocation(section.getConfigurationSection("match-spawn"));
                StructureHintSnapshot structureHint = readHint(section.getConfigurationSection("structure-hint"));
                if (baseName.isBlank() || matchSpawn == null || structureHint == null) {
                    continue;
                }

                snapshots.add(new PreparedMatchSnapshot(new MatchWorldSet(baseName), matchSpawn, structureHint));
            }

            return snapshots;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public List<MatchWorldSet> loadWorldSetsForCleanup() {
        return load().stream().map(PreparedMatchSnapshot::matchWorldSet).toList();
    }

    public void delete() {
        if (storageFile.exists()) {
            storageFile.delete();
        }
    }

    public boolean exists() {
        return storageFile.exists();
    }

    private void writeLocation(ConfigurationSection section, PausedMatchStore.StoredLocation location) {
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

    private PausedMatchStore.StoredLocation readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world", "");
        if (worldName.isBlank()) {
            return null;
        }

        return new PausedMatchStore.StoredLocation(
            worldName,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch")
        );
    }

    private void writeHint(ConfigurationSection section, StructureHintSnapshot hint) {
        if (section == null || hint == null) {
            return;
        }

        section.set("landmark-name", hint.landmarkName());
        section.set("rough-direction", hint.roughDirection());
        section.set("approximate-distance-blocks", hint.approximateDistanceBlocks());
        section.set("target-yaw-degrees", hint.targetYawDegrees());
        writeLocation(section.createSection("location"), hint.location());
    }

    private StructureHintSnapshot readHint(ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        String landmarkName = section.getString("landmark-name", "");
        String roughDirection = section.getString("rough-direction", "");
        PausedMatchStore.StoredLocation location = readLocation(section.getConfigurationSection("location"));
        if (landmarkName.isBlank() || roughDirection.isBlank() || location == null) {
            return null;
        }

        return new StructureHintSnapshot(
            landmarkName,
            location,
            roughDirection,
            section.getInt("approximate-distance-blocks"),
            section.getInt("target-yaw-degrees")
        );
    }

    public record PreparedMatchSnapshot(
        MatchWorldSet matchWorldSet,
        PausedMatchStore.StoredLocation matchSpawn,
        StructureHintSnapshot structureHint
    ) {
    }

    public record StructureHintSnapshot(
        String landmarkName,
        PausedMatchStore.StoredLocation location,
        String roughDirection,
        int approximateDistanceBlocks,
        int targetYawDegrees
    ) {
        public static StructureHintSnapshot fromStructureHint(StructureHint structureHint, PausedMatchStore.StoredLocation location) {
            return new StructureHintSnapshot(
                structureHint.landmarkName(),
                location,
                structureHint.roughDirection(),
                structureHint.approximateDistanceBlocks(),
                structureHint.targetYawDegrees()
            );
        }
    }
}
