package com.huntcore.world;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;

public final class MatchWorldService {

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;

    public MatchWorldService(HuntCorePlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
    }

    public MatchWorldSet createFreshWorldSet() {
        MatchWorldSet worldSet = new MatchWorldSet(buildBaseWorldName());
        long seed = ThreadLocalRandom.current().nextLong();

        try {
            createWorld(worldSet.getOverworldName(), World.Environment.NORMAL, seed);
            createWorld(worldSet.getNetherName(), World.Environment.NETHER, seed);
            createWorld(worldSet.getEndName(), World.Environment.THE_END, seed);
            return worldSet;
        } catch (RuntimeException exception) {
            cleanup(worldSet);
            throw exception;
        }
    }

    public World getOverworld(MatchWorldSet worldSet) {
        return Bukkit.getWorld(worldSet.getOverworldName());
    }

    public World getNether(MatchWorldSet worldSet) {
        return Bukkit.getWorld(worldSet.getNetherName());
    }

    public World getEnd(MatchWorldSet worldSet) {
        return Bukkit.getWorld(worldSet.getEndName());
    }

    public void cleanup(MatchWorldSet worldSet) {
        if (worldSet == null) {
            return;
        }

        unloadAndDelete(worldSet.getEndName());
        unloadAndDelete(worldSet.getNetherName());
        unloadAndDelete(worldSet.getOverworldName());
    }

    private String buildBaseWorldName() {
        String prefix = pluginConfig.getMatchWorldPrefix().replaceAll("[^A-Za-z0-9_\\-]", "_");
        String suffix = Long.toHexString(ThreadLocalRandom.current().nextLong()).replace('-', '0');
        return prefix + "_" + suffix.substring(0, Math.min(suffix.length(), 10));
    }

    private World createWorld(String name, World.Environment environment, long seed) {
        WorldCreator creator = new WorldCreator(name);
        creator.environment(environment);
        creator.seed(seed);

        World world = Bukkit.createWorld(creator);
        if (world == null) {
            throw new IllegalStateException("Could not create match world: " + name);
        }

        world.setAutoSave(false);
        return world;
    }

    private void unloadAndDelete(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("Could not unload temporary match world " + worldName + " before deletion.");
                return;
            }
        }

        Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldName);
        if (!Files.exists(worldPath)) {
            return;
        }

        try {
            Files.walkFileTree(worldPath, new RecursiveDeleteVisitor());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to delete temporary match world " + worldName + ": " + exception.getMessage());
        }
    }

    private static final class RecursiveDeleteVisitor extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
            Files.deleteIfExists(file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
            if (exception != null) {
                throw exception;
            }

            Files.deleteIfExists(directory);
            return FileVisitResult.CONTINUE;
        }
    }
}
