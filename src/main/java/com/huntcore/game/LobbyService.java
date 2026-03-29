package com.huntcore.game;

import com.huntcore.config.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class LobbyService {

    private final PluginConfig pluginConfig;
    private final TeleportSafetyService teleportSafetyService;

    public LobbyService(PluginConfig pluginConfig, TeleportSafetyService teleportSafetyService) {
        this.pluginConfig = pluginConfig;
        this.teleportSafetyService = teleportSafetyService;
    }

    public void sendToLobby(Player player, boolean clearInventory) {
        prepareForLobby(player, clearInventory);
        clearDragonBossBars(player);
        teleportSafetyService.teleport(player, pluginConfig.getLobbySpawn(player.getServer()), false, false);
    }

    public void prepareForLobby(Player player, boolean clearInventory) {
        if (clearInventory) {
            player.getInventory().clear();
        }

        player.setGameMode(GameMode.ADVENTURE);
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        PlayerVitals.applySurvivalMatchNutrition(player);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public Location getLobbySpawn(Player player) {
        return pluginConfig.getLobbySpawn(player.getServer());
    }

    public void clearDragonBossBars(Player player) {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.THE_END) {
                continue;
            }

            DragonBattle dragonBattle = world.getEnderDragonBattle();
            if (dragonBattle != null) {
                dragonBattle.getBossBar().removePlayer(player);
            }
        }
    }
}
