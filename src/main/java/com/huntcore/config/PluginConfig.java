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

    public String getMatchWorldPrefix() {
        return plugin.getConfig().getString("match.world-prefix", "huntcore_match");
    }

    public int getDisconnectGraceSeconds() {
        return plugin.getConfig().getInt("match.disconnect-grace-seconds", 60);
    }

    public boolean shouldHuntersKeepInventory() {
        return plugin.getConfig().getBoolean("match.hunters-keep-inventory", false);
    }

    public void setHuntersKeepInventory(boolean keepInventory) {
        plugin.getConfig().set("match.hunters-keep-inventory", keepInventory);
        plugin.saveConfig();
    }

    public int getCompassUpdateTicks() {
        return plugin.getConfig().getInt("tracking.compass-update-ticks", 20);
    }

    public int getTrackingPortalMemorySeconds() {
        return plugin.getConfig().getInt("tracking.portal-memory-seconds", 180);
    }

    public int getReturnToLobbySeconds() {
        return plugin.getConfig().getInt("end-of-match.return-to-lobby-seconds", 5);
    }

    public Location getLobbySpawn(Server server) {
        FileConfiguration config = plugin.getConfig();
        World world = server.getWorld(getLobbyWorldName());
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

    public String getLobbyMapZipPath() {
        return plugin.getConfig().getString("lobby.map-zip-path", "").trim();
    }

    public String getLobbyMapWorldName() {
        return plugin.getConfig().getString("lobby.map-world-name", "huntcore_parkour_lobby");
    }

    public String getLobbyWorldName() {
        return plugin.getConfig().getString("lobby.world", "world");
    }

    public Location getPvpSpawn(Server server) {
        FileConfiguration config = plugin.getConfig();
        World world = server.getWorld(getPvpWorldName());
        if (world == null) {
            throw new IllegalStateException("The configured PvP world is not loaded: " + getPvpWorldName());
        }

        if (config.getBoolean("pvp.use-world-spawn", true)) {
            return world.getSpawnLocation().clone().add(0.5, 0.0, 0.5);
        }

        return new Location(
            world,
            config.getDouble("pvp.x", 0.5),
            config.getDouble("pvp.y", 64.0),
            config.getDouble("pvp.z", 0.5),
            (float) config.getDouble("pvp.yaw", 0.0),
            (float) config.getDouble("pvp.pitch", 0.0)
        );
    }

    public String getPvpMapZipPath() {
        return plugin.getConfig().getString("pvp.map-zip-path", "").trim();
    }

    public String getPvpMapWorldName() {
        return plugin.getConfig().getString("pvp.map-world-name", "huntcore_pvp_arena");
    }

    public String getPvpWorldName() {
        return plugin.getConfig().getString("pvp.world", "world");
    }

    public void setLobbySpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Lobby spawn location must include a world.");
        }

        FileConfiguration config = plugin.getConfig();
        config.set("lobby.world", location.getWorld().getName());
        config.set("lobby.use-world-spawn", false);
        config.set("lobby.x", location.getX());
        config.set("lobby.y", location.getY());
        config.set("lobby.z", location.getZ());
        config.set("lobby.yaw", location.getYaw());
        config.set("lobby.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public void setLobbyToWorldSpawn(World world) {
        if (world == null) {
            throw new IllegalArgumentException("Lobby world must not be null.");
        }

        setLobbySpawn(world.getSpawnLocation().clone().add(0.5, 0.0, 0.5));
    }

    public void setPvpSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("PvP spawn location must include a world.");
        }

        FileConfiguration config = plugin.getConfig();
        config.set("pvp.world", location.getWorld().getName());
        config.set("pvp.use-world-spawn", false);
        config.set("pvp.x", location.getX());
        config.set("pvp.y", location.getY());
        config.set("pvp.z", location.getZ());
        config.set("pvp.yaw", location.getYaw());
        config.set("pvp.pitch", location.getPitch());
        plugin.saveConfig();
    }

    public void setPvpToWorldSpawn(World world) {
        if (world == null) {
            throw new IllegalArgumentException("PvP world must not be null.");
        }

        setPvpSpawn(world.getSpawnLocation().clone().add(0.5, 0.0, 0.5));
    }

    private World getFallbackWorld(Server server) {
        if (server.getWorlds().isEmpty()) {
            return null;
        }

        return server.getWorlds().get(0);
    }
}
