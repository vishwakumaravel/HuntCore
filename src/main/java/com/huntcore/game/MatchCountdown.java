package com.huntcore.game;

import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class MatchCountdown {

    private final JavaPlugin plugin;
    private BukkitTask activeTask;

    public MatchCountdown(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start(int seconds, IntConsumer onTick, Runnable onComplete) {
        cancel();

        if (seconds <= 0) {
            onComplete.run();
            return;
        }

        final int[] remaining = {seconds};
        onTick.accept(remaining[0]);

        activeTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                cancel();
                onComplete.run();
                return;
            }

            onTick.accept(remaining[0]);
        }, 20L, 20L);
    }

    public boolean isRunning() {
        return activeTask != null;
    }

    public void cancel() {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }
    }
}

