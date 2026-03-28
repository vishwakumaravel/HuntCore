package com.huntcore.config;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginConfig {

    private final JavaPlugin plugin;

    public PluginConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public int getMatchStartSeconds() {
        return plugin.getConfig().getInt("countdowns.match-start-seconds", 15);
    }

    public int getHunterHeadStartSeconds() {
        return plugin.getConfig().getInt("countdowns.hunter-head-start-seconds", 20);
    }

    public int getSpawnRadius() {
        return plugin.getConfig().getInt("match.spawn-radius", 2000);
    }

    public int getSpawnAttempts() {
        return plugin.getConfig().getInt("match.spawn-attempts", 40);
    }

    public int getStructureSearchRadiusChunks() {
        return plugin.getConfig().getInt("match.structure-search-radius-chunks", 80);
    }

    public int getCompassUpdateTicks() {
        return plugin.getConfig().getInt("tracking.compass-update-ticks", 20);
    }

    public int getReturnToLobbySeconds() {
        return plugin.getConfig().getInt("end-of-match.return-to-lobby-seconds", 5);
    }

    public Location getLobbySpawn(Server server) {
        FileConfiguration config = plugin.getConfig();
        World world = server.getWorld(config.getString("lobby.world", "world"));
        if (world == null) {
            world = getFallbackWorld(server);
        }

        if (world == null) {
            throw new IllegalStateException("No worlds are loaded for HuntCore lobby setup.");
        }

        if (config.getBoolean("lobby.use-world-spawn", true)) {
            return world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }

        return new Location(
            world,
            config.getDouble("lobby.x", 0.5),
            config.getDouble("lobby.y", 64.0),
            config.getDouble("lobby.z", 0.5),
            (float) config.getDouble("lobby.yaw", 0.0),
            (float) config.getDouble("lobby.pitch", 0.0)
        );
    }

    public World getMatchWorld(Server server) {
        World configured = server.getWorld(plugin.getConfig().getString("match.world", "world"));
        if (configured != null && configured.getEnvironment() == World.Environment.NORMAL) {
            return configured;
        }

        for (World world : server.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }

        return getFallbackWorld(server);
    }

    private World getFallbackWorld(Server server) {
        if (server.getWorlds().isEmpty()) {
            return null;
        }

        return server.getWorlds().get(0);
    }
}

