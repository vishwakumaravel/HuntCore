package com.huntcore.world;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import java.io.IOException;
import java.nio.file.Path;

public final class LobbyMapInstaller {

    private final PluginConfig pluginConfig;
    private final WorldZipInstaller worldZipInstaller;

    public LobbyMapInstaller(HuntCorePlugin plugin, PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
        this.worldZipInstaller = new WorldZipInstaller(plugin);
    }

    public String getConfiguredZipPath() {
        return pluginConfig.getLobbyMapZipPath();
    }

    public String getConfiguredWorldName() {
        return worldZipInstaller.sanitizeWorldName(pluginConfig.getLobbyMapWorldName());
    }

    public boolean ensureConfiguredLobbyWorldLoaded() {
        return worldZipInstaller.ensureWorldLoaded(pluginConfig.getLobbyWorldName());
    }

    public InstallResult installFromZip(String zipPathString, String worldName) throws IOException {
        WorldZipInstaller.InstallResult result = worldZipInstaller.installFromZip(zipPathString, worldName);
        pluginConfig.setLobbyToWorldSpawn(result.world());
        return new InstallResult(result.worldName(), result.zipPath(), result.archiveRoot());
    }

    public record InstallResult(String worldName, Path zipPath, String archiveRoot) {
    }
}
