package com.huntcore.pvp;

import com.huntcore.config.PluginConfig;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import com.huntcore.game.PlayerVitals;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import com.huntcore.game.TeleportSafetyService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.potion.PotionEffect;

public final class PvpArenaManager {

    private final PluginConfig pluginConfig;
    private final PlayerRegistry playerRegistry;
    private final LobbyService lobbyService;
    private final GameManager gameManager;
    private final TeleportSafetyService teleportSafetyService;
    private final Map<UUID, PvpSession> sessions = new HashMap<>();

    public PvpArenaManager(
        PluginConfig pluginConfig,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        GameManager gameManager,
        TeleportSafetyService teleportSafetyService
    ) {
        this.pluginConfig = pluginConfig;
        this.playerRegistry = playerRegistry;
        this.lobbyService = lobbyService;
        this.gameManager = gameManager;
        this.teleportSafetyService = teleportSafetyService;
    }

    public boolean isPvpParticipant(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean enterPvpArena(Player player) {
        if (isPvpParticipant(player.getUniqueId())) {
            player.sendMessage("[HuntCore] You are already in the PvP arena. Use /pvpleave to leave.");
            return true;
        }

        if (gameManager.isRoleSelectionLocked(player)) {
            player.sendMessage("[HuntCore] Active runners and hunters cannot leave the match for /pvp.");
            return true;
        }

        Location pvpSpawn = getPvpSpawnOrNull(player);
        if (pvpSpawn == null) {
            player.sendMessage("[HuntCore] PvP arena is not ready. Use /installpvpmap or /setpvpspawn first.");
            return true;
        }

        playerRegistry.registerPlayer(player);
        PlayerRole previousRole = playerRegistry.getRole(player.getUniqueId());
        sessions.put(player.getUniqueId(), PvpSession.capture(player, previousRole));
        playerRegistry.setRole(player.getUniqueId(), PlayerRole.NONE);
        playerRegistry.setReady(player.getUniqueId(), false);
        gameManager.handleLobbyStateChange();

        prepareForPvp(player);
        teleportSafetyService.teleport(player, pvpSpawn, false, false);
        player.sendMessage("[HuntCore] Entered the PvP arena. Use /pvpleave to return.");
        return true;
    }

    public boolean leavePvpArena(Player player) {
        return leavePvpArena(player, false);
    }

    public boolean leavePvpArena(Player player, boolean sendToLobby) {
        PvpSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            player.sendMessage("[HuntCore] You are not in the PvP arena.");
            return true;
        }

        restoreRole(player, session.previousRole());
        restorePlayerState(player, session, sendToLobby ? lobbyService.getLobbySpawn(player) : resolveReturnLocation(player, session));
        player.sendMessage(sendToLobby
            ? "[HuntCore] PvP session closed. Returned to the lobby."
            : "[HuntCore] Left the PvP arena.");
        return true;
    }

    public boolean handlePlayerJoin(Player player) {
        PvpSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return false;
        }

        Location pvpSpawn = getPvpSpawnOrNull(player);
        if (pvpSpawn == null) {
            sessions.remove(player.getUniqueId());
            restoreRole(player, session.previousRole());
            restorePlayerState(player, session, lobbyService.getLobbySpawn(player));
            player.sendMessage("[HuntCore] PvP arena was unavailable, so your saved state was restored in the lobby.");
            return true;
        }

        prepareForPvp(player);
        teleportSafetyService.teleport(player, pvpSpawn, false, false);
        player.sendMessage("[HuntCore] You rejoined the PvP arena.");
        return true;
    }

    public boolean shouldKeepPlayerRegisteredOnQuit(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public Location getRespawnLocation(Player player) {
        if (!isPvpParticipant(player.getUniqueId())) {
            return null;
        }

        Location pvpSpawn = getPvpSpawnOrNull(player);
        return pvpSpawn != null ? pvpSpawn : lobbyService.getLobbySpawn(player);
    }

    public void handlePostRespawn(Player player) {
        PvpSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (getPvpSpawnOrNull(player) == null) {
            sessions.remove(player.getUniqueId());
            restoreRole(player, session.previousRole());
            restorePlayerState(player, session, lobbyService.getLobbySpawn(player));
            player.sendMessage("[HuntCore] PvP arena was unavailable, so your saved state was restored in the lobby.");
            return;
        }

        prepareForPvp(player);
        teleportSafetyService.stabilize(player, false, false);
    }

    public void shutdown() {
        sessions.clear();
    }

    private void restoreRole(Player player, PlayerRole role) {
        playerRegistry.setRole(player.getUniqueId(), role);
        playerRegistry.setReady(player.getUniqueId(), false);
        gameManager.handleLobbyStateChange();
    }

    private Location resolveReturnLocation(Player player, PvpSession session) {
        Location returnLocation = session.returnLocation();
        if (returnLocation != null && returnLocation.getWorld() != null && player.getServer().getWorld(returnLocation.getWorld().getName()) != null) {
            return returnLocation;
        }

        return lobbyService.getLobbySpawn(player);
    }

    private void restorePlayerState(Player player, PvpSession session, Location targetLocation) {
        clearPotionEffects(player);
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setStorageContents(cloneItems(session.storageContents()));
        inventory.setArmorContents(cloneItems(session.armorContents()));
        inventory.setItemInOffHand(session.offHand() == null ? null : session.offHand().clone());
        inventory.setHeldItemSlot(Math.max(0, Math.min(8, session.heldItemSlot())));

        player.setGameMode(session.gameMode());
        player.setAllowFlight(session.allowFlight());
        player.setFlying(session.allowFlight() && session.flying());
        player.setInvulnerable(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(Math.min(session.health(), maxHealth.getValue()));
        }

        player.setFoodLevel(session.foodLevel());
        player.setSaturation(session.saturation());
        player.setLevel(session.level());
        player.setExp(session.expProgress());
        teleportSafetyService.teleport(player, targetLocation, session.allowFlight(), session.flying());
    }

    private void prepareForPvp(Player player) {
        clearPotionEffects(player);
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }

        PlayerVitals.applySurvivalMatchNutrition(player);
        equipKit(player.getInventory());
    }

    private void equipKit(PlayerInventory inventory) {
        inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        inventory.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
        inventory.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
        inventory.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inventory.setItem(1, new ItemStack(Material.DIAMOND_AXE));
        inventory.setItem(3, new ItemStack(Material.COOKED_BEEF, 6));

        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        CrossbowMeta crossbowMeta = (CrossbowMeta) crossbow.getItemMeta();
        crossbow.setItemMeta(crossbowMeta);
        inventory.setItem(2, crossbow);
        inventory.setItem(8, new ItemStack(Material.ARROW, 8));
        inventory.setItemInOffHand(new ItemStack(Material.SHIELD));
        inventory.setHeldItemSlot(0);
    }

    private void clearPotionEffects(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private Location getPvpSpawnOrNull(Player player) {
        try {
            return pluginConfig.getPvpSpawn(player.getServer());
        } catch (IllegalStateException exception) {
            return null;
        }
    }

    private ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] cloned = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            cloned[index] = items[index] == null ? null : items[index].clone();
        }
        return cloned;
    }
}
