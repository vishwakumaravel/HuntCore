package com.huntcore.world;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import java.io.IOException;

public final class PvpMapInstaller {

    private final PluginConfig pluginConfig;
    private final WorldZipInstaller worldZipInstaller;

    public PvpMapInstaller(HuntCorePlugin plugin, PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
        this.worldZipInstaller = new WorldZipInstaller(plugin);
    }

    public String getConfiguredZipPath() {
        return pluginConfig.getPvpMapZipPath();
    }

    public String getConfiguredWorldName() {
        return worldZipInstaller.sanitizeWorldName(pluginConfig.getPvpMapWorldName());
    }

    public boolean ensureConfiguredPvpWorldLoaded() {
        return worldZipInstaller.ensureWorldLoaded(pluginConfig.getPvpWorldName());
    }

    public InstallResult installFromZip(String zipPath, String worldName) throws IOException {
        WorldZipInstaller.InstallResult result = worldZipInstaller.installFromZip(zipPath, worldName);
        pluginConfig.setPvpToWorldSpawn(result.world());
        return new InstallResult(result.worldName(), result.zipPath(), result.archiveRoot());
    }

    public record InstallResult(String worldName, java.nio.file.Path zipPath, String archiveRoot) {
    }
}
