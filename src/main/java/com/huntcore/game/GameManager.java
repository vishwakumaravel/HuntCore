package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import com.huntcore.tracking.CompassTracker;
import com.huntcore.world.MatchSpawnService;
import com.huntcore.world.StructureHint;
import com.huntcore.world.StructureHintService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager {

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;
    private final PlayerRegistry playerRegistry;
    private final LobbyService lobbyService;
    private final MatchSpawnService matchSpawnService;
    private final StructureHintService structureHintService;
    private final MatchCountdown matchCountdown;
    private final CompassTracker compassTracker;

    private GameState gameState = GameState.LOBBY;
    private MatchContext currentMatch;
    private BukkitTask headStartTask;
    private BukkitTask endTask;

    public GameManager(
        HuntCorePlugin plugin,
        PluginConfig pluginConfig,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        MatchSpawnService matchSpawnService,
        StructureHintService structureHintService,
        MatchCountdown matchCountdown,
        CompassTracker compassTracker
    ) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.playerRegistry = playerRegistry;
        this.lobbyService = lobbyService;
        this.matchSpawnService = matchSpawnService;
        this.structureHintService = structureHintService;
        this.matchCountdown = matchCountdown;
        this.compassTracker = compassTracker;
    }

    public GameState getGameState() {
        return gameState;
    }

    public boolean isLobbyEditable() {
        return gameState == GameState.LOBBY || gameState == GameState.COUNTDOWN;
    }

    public boolean isWaitingState() {
        return gameState == GameState.LOBBY || gameState == GameState.COUNTDOWN;
    }

    public boolean isLiveMatchState() {
        return gameState == GameState.HEAD_START || gameState == GameState.IN_GAME;
    }

    public boolean isHunterFrozen(Player player) {
        return gameState == GameState.HEAD_START && currentMatch != null && isActiveHunter(player.getUniqueId());
    }

    public boolean isActiveRunner(UUID playerId) {
        return currentMatch != null && currentMatch.isRunner(playerId) && isLiveMatchState();
    }

    public boolean isActiveHunter(UUID playerId) {
        return currentMatch != null
            && currentMatch.isHunter(playerId)
            && !currentMatch.isHunterEliminated(playerId)
            && isLiveMatchState();
    }

    public boolean shouldRespawnInLobby(UUID playerId) {
        return currentMatch != null && currentMatch.involves(playerId);
    }

    public boolean shouldApplyLobbyProtections(UUID playerId) {
        if (isWaitingState()) {
            return true;
        }

        if (currentMatch == null) {
            return false;
        }

        return !isActiveRunner(playerId) && !isActiveHunter(playerId);
    }

    public boolean canUseRunnerRole(Player player) {
        List<Player> onlineRunners = playerRegistry.getOnlinePlayersWithRole(Bukkit.getOnlinePlayers(), PlayerRole.RUNNER);
        return onlineRunners.isEmpty()
            || (onlineRunners.size() == 1 && onlineRunners.get(0).getUniqueId().equals(player.getUniqueId()));
    }

    public void handleLobbyStateChange() {
        if (!isLobbyEditable()) {
            return;
        }

        String blocker = getStartBlocker();
        if (blocker == null) {
            if (gameState == GameState.LOBBY) {
                startMatchCountdown();
            }
            return;
        }

        if (gameState == GameState.COUNTDOWN) {
            cancelMatchCountdown(blocker);
        }
    }

    public void handleRunnerDeath(Player runner) {
        if (!isActiveRunner(runner.getUniqueId())) {
            return;
        }

        endMatch(MatchWinner.HUNTERS, runner.getName() + " died.");
    }

    public void handleHunterElimination(Player hunter) {
        if (!isActiveHunter(hunter.getUniqueId()) || currentMatch == null) {
            return;
        }

        currentMatch.eliminateHunter(hunter.getUniqueId());
        Bukkit.broadcastMessage("[HuntCore] Hunter eliminated: " + hunter.getName());

        if (!hasAnyActiveHunters()) {
            endMatch(MatchWinner.RUNNER, "All hunters were eliminated or left.");
        }
    }

    public void handlePlayerQuit(Player player) {
        if ((!isLiveMatchState() && gameState != GameState.ENDING) || currentMatch == null) {
            return;
        }

        if (!currentMatch.involves(player.getUniqueId())) {
            return;
        }

        if (currentMatch.isRunner(player.getUniqueId()) && gameState != GameState.ENDING) {
            endMatch(MatchWinner.HUNTERS, "The runner left the match.");
            return;
        }

        if (currentMatch.isHunter(player.getUniqueId()) && !currentMatch.isHunterEliminated(player.getUniqueId())) {
            currentMatch.eliminateHunter(player.getUniqueId());
            if (gameState != GameState.ENDING && !hasAnyActiveHunters()) {
                endMatch(MatchWinner.RUNNER, "All hunters were eliminated or left.");
            }
        }
    }

    public void shutdown() {
        matchCountdown.cancel();
        cancelTask(headStartTask);
        cancelTask(endTask);
        compassTracker.stop();
    }

    private void startMatchCountdown() {
        gameState = GameState.COUNTDOWN;
        Bukkit.broadcastMessage("[HuntCore] All players are ready. Match starts in " + pluginConfig.getMatchStartSeconds() + " seconds.");

        matchCountdown.start(
            pluginConfig.getMatchStartSeconds(),
            seconds -> {
                if (seconds <= 5 || seconds % 5 == 0) {
                    Bukkit.broadcastMessage("[HuntCore] Match starts in " + seconds + "...");
                }
            },
            () -> {
                String blocker = getStartBlocker();
                if (blocker != null) {
                    cancelMatchCountdown(blocker);
                    return;
                }
                beginMatch();
            }
        );
    }

    private void cancelMatchCountdown(String reason) {
        matchCountdown.cancel();
        gameState = GameState.LOBBY;
        Bukkit.broadcastMessage("[HuntCore] Match countdown cancelled: " + reason);
    }

    private void beginMatch() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        List<Player> runners = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.RUNNER);
        List<Player> hunters = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.HUNTER);

        if (runners.size() != 1 || hunters.isEmpty()) {
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because the teams changed.");
            return;
        }

        Player runner = runners.get(0);
        World matchWorld = pluginConfig.getMatchWorld(Bukkit.getServer());
        if (matchWorld == null) {
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because no overworld was available.");
            return;
        }

        Location matchSpawn = matchSpawnService.findSafeSpawn(matchWorld).orElse(null);
        if (matchSpawn == null) {
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because no safe spawn was found.");
            return;
        }

        // TODO HuntCore v2: support multiple runners and shared hunter target rules.
        StructureHint structureHint = structureHintService.findNearestHint(matchSpawn).orElse(null);
        currentMatch = new MatchContext(
            runner.getUniqueId(),
            new HashSet<>(hunters.stream().map(Player::getUniqueId).toList()),
            matchSpawn,
            structureHint
        );

        for (Player player : onlinePlayers) {
            preparePlayerForMatch(player);
            if (player.getUniqueId().equals(runner.getUniqueId())) {
                player.teleport(matchSpawn);
            } else {
                player.teleport(spreadHunterSpawn(matchSpawn, hunters.indexOf(player)));
            }
        }

        if (structureHint != null) {
            runner.getInventory().addItem(structureHintService.createHintBook(structureHint));
            runner.sendMessage("[HuntCore] A scout note has been added to your inventory.");
        } else {
            runner.sendMessage("[HuntCore] No nearby structure hint was found for this round.");
        }

        gameState = GameState.HEAD_START;
        Bukkit.broadcastMessage("[HuntCore] Match started. " + runner.getName() + " is the runner.");
        startHeadStart(runner);
    }

    private void startHeadStart(Player runner) {
        cancelTask(headStartTask);

        int headStartSeconds = pluginConfig.getHunterHeadStartSeconds();
        if (headStartSeconds <= 0) {
            releaseHunters(runner);
            return;
        }

        runner.sendMessage("[HuntCore] Run. Hunters are released in " + headStartSeconds + " seconds.");
        for (Player hunter : getActiveOnlineHunters()) {
            hunter.sendMessage("[HuntCore] Wait for the head start. Release in " + headStartSeconds + " seconds.");
        }

        final int[] remaining = {headStartSeconds};
        headStartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;

            if (remaining[0] <= 0) {
                cancelTask(headStartTask);
                headStartTask = null;
                releaseHunters(runner);
                return;
            }

            if (remaining[0] <= 5) {
                runner.sendMessage("[HuntCore] Hunters release in " + remaining[0] + "...");
                for (Player hunter : getActiveOnlineHunters()) {
                    hunter.sendMessage("[HuntCore] Release in " + remaining[0] + "...");
                }
            }
        }, 20L, 20L);
    }

    private void releaseHunters(Player runner) {
        if (currentMatch == null) {
            return;
        }

        gameState = GameState.IN_GAME;
        for (Player hunter : getActiveOnlineHunters()) {
            compassTracker.giveHunterCompass(hunter);
        }

        compassTracker.start(
            () -> Bukkit.getPlayer(runner.getUniqueId()),
            this::getActiveOnlineHunters
        );

        Bukkit.broadcastMessage("[HuntCore] Hunters released. Good luck.");
    }

    private void endMatch(MatchWinner winner, String reason) {
        if (gameState == GameState.ENDING || currentMatch == null) {
            return;
        }

        gameState = GameState.ENDING;
        matchCountdown.cancel();
        cancelTask(headStartTask);
        headStartTask = null;
        compassTracker.stop();

        String winnerText = winner == MatchWinner.RUNNER ? "Runner" : "Hunters";
        Bukkit.broadcastMessage("[HuntCore] " + winnerText + " win! " + reason);

        // TODO HuntCore v2: add scoreboard and richer post-match summaries.
        cancelTask(endTask);
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::returnEveryoneToLobby, pluginConfig.getReturnToLobbySeconds() * 20L);
    }

    private void returnEveryoneToLobby() {
        cancelTask(endTask);
        endTask = null;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            lobbyService.sendToLobby(onlinePlayer, true);
            compassTracker.removeHunterCompasses(onlinePlayer);
        }

        playerRegistry.resetAllReady();
        currentMatch = null;
        gameState = GameState.LOBBY;
        Bukkit.broadcastMessage("[HuntCore] Returned to the lobby.");
    }

    private String getStartBlocker() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.size() < 2) {
            return "Need at least 2 players online.";
        }

        List<Player> runners = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.RUNNER);
        List<Player> hunters = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.HUNTER);

        if (runners.isEmpty()) {
            return "Need at least 1 runner.";
        }

        if (runners.size() > 1) {
            return "v1 currently supports exactly 1 runner.";
        }

        if (hunters.isEmpty()) {
            return "Need at least 1 hunter.";
        }

        if (!playerRegistry.areAllOnlinePlayersReadyAndAssigned(onlinePlayers)) {
            return "All online players must choose a role and use /ready.";
        }

        return null;
    }

    private boolean hasAnyActiveHunters() {
        if (currentMatch == null) {
            return false;
        }

        for (UUID hunterId : currentMatch.getHunterIds()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline() && !currentMatch.isHunterEliminated(hunterId)) {
                return true;
            }
        }

        return false;
    }

    private List<Player> getActiveOnlineHunters() {
        if (currentMatch == null) {
            return List.of();
        }

        List<Player> hunters = new ArrayList<>();
        for (UUID hunterId : currentMatch.getHunterIds()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline() && !currentMatch.isHunterEliminated(hunterId)) {
                hunters.add(hunter);
            }
        }

        return hunters;
    }

    private void preparePlayerForMatch(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);

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

    private Location spreadHunterSpawn(Location baseSpawn, int hunterIndex) {
        if (hunterIndex < 0) {
            return baseSpawn.clone();
        }

        double angle = Math.toRadians(hunterIndex * 45.0);
        double radius = 2.0;
        return baseSpawn.clone().add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }
}
