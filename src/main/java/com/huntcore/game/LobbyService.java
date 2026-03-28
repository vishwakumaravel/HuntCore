package com.huntcore.game;

import com.huntcore.config.PluginConfig;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

public final class LobbyService {

    private final PluginConfig pluginConfig;

    public LobbyService(PluginConfig pluginConfig) {
        this.pluginConfig = pluginConfig;
    }

    public void sendToLobby(Player player, boolean clearInventory) {
        prepareForLobby(player, clearInventory);
        player.teleport(pluginConfig.getLobbySpawn(player.getServer()));
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
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setExp(0.0f);
        player.setLevel(0);
        player.setAllowFlight(false);
        player.setFlying(false);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    public Location getLobbySpawn(Player player) {
        return pluginConfig.getLobbySpawn(player.getServer());
    }
}
