package com.huntcore.pvp;

import com.huntcore.game.PlayerRole;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public record PvpSession(
    PlayerRole previousRole,
    Location returnLocation,
    ItemStack[] storageContents,
    ItemStack[] armorContents,
    ItemStack offHand,
    int heldItemSlot,
    float expProgress,
    int level,
    double health,
    int foodLevel,
    float saturation,
    GameMode gameMode,
    boolean allowFlight,
    boolean flying
) {

    public static PvpSession capture(Player player, PlayerRole previousRole) {
        PlayerInventory inventory = player.getInventory();
        return new PvpSession(
            previousRole,
            player.getLocation().clone(),
            cloneItems(inventory.getStorageContents()),
            cloneItems(inventory.getArmorContents()),
            cloneItem(inventory.getItemInOffHand()),
            inventory.getHeldItemSlot(),
            player.getExp(),
            player.getLevel(),
            player.getHealth(),
            player.getFoodLevel(),
            player.getSaturation(),
            player.getGameMode(),
            player.getAllowFlight(),
            player.isFlying()
        );
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] cloned = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            cloned[index] = cloneItem(items[index]);
        }
        return cloned;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }
}
