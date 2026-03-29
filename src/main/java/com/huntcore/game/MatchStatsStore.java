package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class MatchStatsStore {

    private static final int MAX_STORED_MATCHES = 50;

    private final File storageFile;

    public MatchStatsStore(HuntCorePlugin plugin) {
        this.storageFile = new File(plugin.getDataFolder(), "match-history.yml");
    }

    public void append(MatchStatSnapshot snapshot) throws IOException {
        List<MatchStatSnapshot> history = new ArrayList<>(load());
        history.add(0, snapshot);
        if (history.size() > MAX_STORED_MATCHES) {
            history = new ArrayList<>(history.subList(0, MAX_STORED_MATCHES));
        }
        save(history);
    }

    public List<MatchStatSnapshot> load() {
        if (!storageFile.exists()) {
            return List.of();
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(storageFile);
            ConfigurationSection matchesSection = config.getConfigurationSection("matches");
            if (matchesSection == null) {
                return List.of();
            }

            List<MatchStatSnapshot> snapshots = new ArrayList<>();
            for (String key : matchesSection.getKeys(false)) {
                ConfigurationSection section = matchesSection.getConfigurationSection(key);
                if (section == null) {
                    continue;
                }

                snapshots.add(
                    new MatchStatSnapshot(
                        section.getLong("ended-at-millis", System.currentTimeMillis()),
                        section.getLong("duration-millis", 0L),
                        section.getString("winner", "Unknown"),
                        section.getString("reason", "Unknown"),
                        section.getString("runner-name", "Unknown"),
                        section.getInt("hunter-count", 0),
                        section.getString("poi-name", "Unknown"),
                        section.getInt("poi-distance-blocks", 0),
                        section.getString("match-world-base-name", ""),
                        readKillCounts(section.getConfigurationSection("player-kills"))
                    )
                );
            }

            return snapshots;
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    public MatchStatSnapshot loadLatest() {
        List<MatchStatSnapshot> history = load();
        return history.isEmpty() ? null : history.get(0);
    }

    private void save(List<MatchStatSnapshot> snapshots) throws IOException {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection matchesSection = config.createSection("matches");
        for (int index = 0; index < snapshots.size(); index++) {
            MatchStatSnapshot snapshot = snapshots.get(index);
            ConfigurationSection section = matchesSection.createSection(Integer.toString(index));
            section.set("ended-at-millis", snapshot.endedAtMillis());
            section.set("duration-millis", snapshot.durationMillis());
            section.set("winner", snapshot.winner());
            section.set("reason", snapshot.reason());
            section.set("runner-name", snapshot.runnerName());
            section.set("hunter-count", snapshot.hunterCount());
            section.set("poi-name", snapshot.poiName());
            section.set("poi-distance-blocks", snapshot.poiDistanceBlocks());
            section.set("match-world-base-name", snapshot.matchWorldBaseName());
            ConfigurationSection killsSection = section.createSection("player-kills");
            for (var entry : snapshot.playerKills().entrySet()) {
                killsSection.set(entry.getKey(), entry.getValue());
            }
        }

        config.save(storageFile);
    }

    private java.util.Map<String, Integer> readKillCounts(ConfigurationSection section) {
        if (section == null) {
            return java.util.Map.of();
        }

        java.util.Map<String, Integer> kills = new java.util.LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            kills.put(key, section.getInt(key, 0));
        }
        return kills;
    }

    public record MatchStatSnapshot(
        long endedAtMillis,
        long durationMillis,
        String winner,
        String reason,
        String runnerName,
        int hunterCount,
        String poiName,
        int poiDistanceBlocks,
        String matchWorldBaseName,
        java.util.Map<String, Integer> playerKills
    ) {
    }
}
