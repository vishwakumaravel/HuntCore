package com.huntcore.tracking;

import com.huntcore.config.PluginConfig;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CompassTracker {

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final NamespacedKey trackerKey;
    private BukkitTask trackingTask;

    public CompassTracker(JavaPlugin plugin, PluginConfig pluginConfig) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.trackerKey = new NamespacedKey(plugin, "hunter_tracker");
    }

    public void start(Supplier<Player> runnerSupplier, Supplier<Collection<Player>> huntersSupplier) {
        stop();
        trackingTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            () -> updateTargets(runnerSupplier, huntersSupplier),
            0L,
            pluginConfig.getCompassUpdateTicks()
        );
    }

    public void stop() {
        if (trackingTask != null) {
            trackingTask.cancel();
            trackingTask = null;
        }
    }

    public void giveHunterCompass(Player hunter) {
        ensureHunterCompass(hunter);
    }

    public void removeHunterCompasses(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isHunterCompass(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    public boolean isHunterCompass(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.COMPASS || !itemStack.hasItemMeta()) {
            return false;
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        return itemMeta.getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING);
    }

    private void updateTargets(Supplier<Player> runnerSupplier, Supplier<Collection<Player>> huntersSupplier) {
        Player runner = runnerSupplier.get();
        if (runner == null || !runner.isOnline()) {
            return;
        }

        for (Player hunter : huntersSupplier.get()) {
            if (!hunter.isOnline()) {
                continue;
            }

            ensureHunterCompass(hunter);
            updateHunterTarget(hunter, runner);
        }
    }

    private void updateHunterTarget(Player hunter, Player runner) {
        if (hunter.getWorld().equals(runner.getWorld())) {
            hunter.setCompassTarget(runner.getLocation());
            return;
        }

        // TODO HuntCore v2: support cross-dimension compass tracking between the overworld, Nether, and End.
        hunter.setCompassTarget(hunter.getWorld().getSpawnLocation());
    }

    private void ensureHunterCompass(Player hunter) {
        for (ItemStack content : hunter.getInventory().getContents()) {
            if (isHunterCompass(content)) {
                return;
            }
        }

        hunter.getInventory().addItem(createHunterCompass());
    }

    private ItemStack createHunterCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta itemMeta = compass.getItemMeta();
        itemMeta.displayName(Component.text("Runner Tracker", NamedTextColor.GOLD));
        itemMeta.lore(List.of(Component.text("Tracks the runner in the same dimension.", NamedTextColor.GRAY)));
        itemMeta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, "huntcore-tracker");
        compass.setItemMeta(itemMeta);
        return compass;
    }
}

