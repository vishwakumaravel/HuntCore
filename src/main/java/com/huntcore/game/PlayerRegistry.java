package com.huntcore.game;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class PlayerRegistry {

    private final Map<UUID, PlayerSelection> playerSelections = new HashMap<>();

    public void registerPlayer(Player player) {
        playerSelections.putIfAbsent(player.getUniqueId(), new PlayerSelection());
    }

    public void removePlayer(UUID playerId) {
        playerSelections.remove(playerId);
    }

    public PlayerRole getRole(UUID playerId) {
        return getSelection(playerId).role;
    }

    public void setRole(UUID playerId, PlayerRole role) {
        PlayerSelection selection = getSelection(playerId);
        selection.role = role;
        selection.ready = false;
    }

    public boolean isReady(UUID playerId) {
        return getSelection(playerId).ready;
    }

    public void setReady(UUID playerId, boolean ready) {
        getSelection(playerId).ready = ready;
    }

    public void resetAllReady() {
        for (PlayerSelection selection : playerSelections.values()) {
            selection.ready = false;
        }
    }

    public List<Player> getOnlinePlayersWithRole(Collection<? extends Player> onlinePlayers, PlayerRole role) {
        return onlinePlayers.stream()
            .filter(player -> getRole(player.getUniqueId()) == role)
            .map(player -> (Player) player)
            .toList();
    }

    public boolean areAllQueuedPlayersReadyAndAssigned(Collection<? extends Player> onlinePlayers) {
        boolean hasQueuedPlayers = false;

        for (Player player : onlinePlayers) {
            PlayerRole role = getRole(player.getUniqueId());
            if (role != PlayerRole.RUNNER && role != PlayerRole.HUNTER) {
                continue;
            }

            hasQueuedPlayers = true;
            if (!isReady(player.getUniqueId())) {
                return false;
            }
        }

        return hasQueuedPlayers;
    }

    public int countOnlinePlayersWithAnyRole(Collection<? extends Player> onlinePlayers, PlayerRole... roles) {
        int count = 0;
        for (Player player : onlinePlayers) {
            PlayerRole playerRole = getRole(player.getUniqueId());
            for (PlayerRole role : roles) {
                if (playerRole == role) {
                    count++;
                    break;
                }
            }
        }

        return count;
    }

    private PlayerSelection getSelection(UUID playerId) {
        return playerSelections.computeIfAbsent(playerId, ignored -> new PlayerSelection());
    }

    private static final class PlayerSelection {
        private PlayerRole role = PlayerRole.NONE;
        private boolean ready;
    }
}
