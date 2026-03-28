package com.huntcore.tracking;

import com.huntcore.config.PluginConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class CompassTracker {

    private static final int PREFERRED_HOTBAR_SLOT = 8;

    private final JavaPlugin plugin;
    private final PluginConfig pluginConfig;
    private final PortalTrackingService portalTrackingService;
    private final NamespacedKey trackerKey;
    private BukkitTask trackingTask;

    public CompassTracker(JavaPlugin plugin, PluginConfig pluginConfig, PortalTrackingService portalTrackingService) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.portalTrackingService = portalTrackingService;
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

    public void resetTrackingState() {
        portalTrackingService.reset();
    }

    public void giveHunterCompass(Player hunter) {
        ensureHunterCompass(hunter);
    }

    public void reconcileHunterCompass(Player hunter) {
        ensureHunterCompass(hunter);
    }

    public void removeHunterCompasses(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isHunterCompass(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        if (isHunterCompass(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    public void removeHunterCompasses(Collection<ItemStack> itemStacks) {
        itemStacks.removeIf(this::isHunterCompass);
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
        for (Player hunter : huntersSupplier.get()) {
            if (!hunter.isOnline()) {
                continue;
            }

            ensureHunterCompass(hunter);
            TrackingSnapshot snapshot = resolveSnapshot(hunter, runner);
            applyTrackingSnapshot(hunter, snapshot);
        }
    }

    private TrackingSnapshot resolveSnapshot(Player hunter, Player runner) {
        if (runner == null || !runner.isOnline()) {
            return new TrackingSnapshot(
                TrackingMode.NO_TARGET,
                hunter.getLocation(),
                Component.text("No players to track", NamedTextColor.GRAY)
            );
        }

        if (hunter.getWorld().equals(runner.getWorld())) {
            return new TrackingSnapshot(
                TrackingMode.DIRECT,
                runner.getLocation(),
                Component.text("Tracking runner", NamedTextColor.GREEN)
            );
        }

        Location recentPortal = portalTrackingService
            .findRecentLocation(hunter.getWorld(), pluginConfig.getTrackingPortalMemorySeconds() * 1000L)
            .orElse(null);

        if (recentPortal != null) {
            return new TrackingSnapshot(
                TrackingMode.LAST_PORTAL,
                recentPortal,
                Component.text("Tracking last portal", NamedTextColor.YELLOW)
            );
        }

        return new TrackingSnapshot(
            TrackingMode.OTHER_DIMENSION,
            hunter.getLocation(),
            Component.text("Runner is in another dimension", NamedTextColor.GRAY)
        );
    }

    private void applyTrackingSnapshot(Player hunter, TrackingSnapshot snapshot) {
        ItemStack compass = getTrackedCompass(hunter);
        if (compass == null) {
            return;
        }

        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        meta.displayName(Component.text("Runner Tracker", NamedTextColor.GOLD));
        meta.lore(buildLore(snapshot.mode()));
        meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, "huntcore-tracker");
        meta.setLodestone(resolveLodestoneTarget(hunter, snapshot));
        meta.setLodestoneTracked(false);
        compass.setItemMeta(meta);

        hunter.sendActionBar(snapshot.statusText());
    }

    private List<Component> buildLore(TrackingMode trackingMode) {
        return switch (trackingMode) {
            case DIRECT -> List.of(Component.text("Direct runner tracking is active.", NamedTextColor.GRAY));
            case LAST_PORTAL -> List.of(Component.text("Tracking the runner's last portal.", NamedTextColor.GRAY));
            case OTHER_DIMENSION -> List.of(Component.text("Runner is in another dimension.", NamedTextColor.GRAY));
            case NO_TARGET -> List.of(Component.text("No active runner to track.", NamedTextColor.GRAY));
        };
    }

    private Location resolveLodestoneTarget(Player hunter, TrackingSnapshot snapshot) {
        Location target = snapshot.targetLocation();
        if (target != null && sameWorld(target.getWorld(), hunter.getWorld())) {
            return target;
        }

        return hunter.getLocation();
    }

    private boolean sameWorld(World first, World second) {
        return first != null && second != null && first.getUID().equals(second.getUID());
    }

    private ItemStack getTrackedCompass(Player hunter) {
        PlayerInventory inventory = hunter.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack itemStack = inventory.getItem(slot);
            if (isHunterCompass(itemStack)) {
                return itemStack;
            }
        }

        if (isHunterCompass(inventory.getItemInOffHand())) {
            return inventory.getItemInOffHand();
        }

        return null;
    }

    private void ensureHunterCompass(Player hunter) {
        PlayerInventory inventory = hunter.getInventory();
        List<Integer> compassSlots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            if (isHunterCompass(inventory.getItem(slot))) {
                compassSlots.add(slot);
            }
        }

        if (isHunterCompass(inventory.getItemInOffHand())) {
            if (!compassSlots.isEmpty()) {
                inventory.setItemInOffHand(null);
            } else {
                ItemStack offhandCompass = inventory.getItemInOffHand();
                inventory.setItemInOffHand(null);
                placeCompassInInventory(hunter, offhandCompass);
                return;
            }
        }

        if (compassSlots.isEmpty()) {
            placeCompassInInventory(hunter, createHunterCompass());
            return;
        }

        for (int index = 1; index < compassSlots.size(); index++) {
            inventory.setItem(compassSlots.get(index), null);
        }
    }

    private void placeCompassInInventory(Player hunter, ItemStack compass) {
        PlayerInventory inventory = hunter.getInventory();
        if (isEmpty(inventory.getItem(PREFERRED_HOTBAR_SLOT))) {
            inventory.setItem(PREFERRED_HOTBAR_SLOT, compass);
            return;
        }

        int emptySlot = findFirstEmptyMainSlot(inventory);
        if (emptySlot >= 0) {
            inventory.setItem(emptySlot, compass);
            return;
        }

        if (isEmpty(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(compass);
            return;
        }

        hunter.sendActionBar(Component.text("Free one slot to restore your tracker", NamedTextColor.YELLOW));
    }

    private int findFirstEmptyMainSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) {
            if (isEmpty(inventory.getItem(slot))) {
                return slot;
            }
        }

        return -1;
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private ItemStack createHunterCompass() {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta itemMeta = (CompassMeta) compass.getItemMeta();
        itemMeta.displayName(Component.text("Runner Tracker", NamedTextColor.GOLD));
        itemMeta.lore(List.of(Component.text("Tracks the runner across dimensions.", NamedTextColor.GRAY)));
        itemMeta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, "huntcore-tracker");
        itemMeta.setLodestoneTracked(false);
        compass.setItemMeta(itemMeta);
        return compass;
    }
}
