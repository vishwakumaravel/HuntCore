package com.huntcore.game;

import com.huntcore.HuntCorePlugin;
import com.huntcore.config.PluginConfig;
import com.huntcore.tracking.CompassTracker;
import com.huntcore.world.MatchSpawnService;
import com.huntcore.world.MatchWorldSet;
import com.huntcore.world.MatchWorldService;
import com.huntcore.world.StructureHint;
import com.huntcore.world.StructureHintService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager {

    private static final NamespacedKey KILL_DRAGON_ADVANCEMENT = NamespacedKey.minecraft("end/kill_dragon");

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;
    private final PlayerRegistry playerRegistry;
    private final LobbyService lobbyService;
    private final MatchSpawnService matchSpawnService;
    private final MatchWorldService matchWorldService;
    private final StructureHintService structureHintService;
    private final MatchCountdown matchCountdown;
    private final CompassTracker compassTracker;

    private GameState gameState = GameState.LOBBY;
    private MatchContext currentMatch;
    private BukkitTask headStartTask;
    private BukkitTask endTask;
    private BukkitTask runnerDisconnectTask;
    private BukkitTask huntersDisconnectTask;

    public GameManager(
        HuntCorePlugin plugin,
        PluginConfig pluginConfig,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        MatchSpawnService matchSpawnService,
        MatchWorldService matchWorldService,
        StructureHintService structureHintService,
        MatchCountdown matchCountdown,
        CompassTracker compassTracker
    ) {
        this.plugin = plugin;
        this.pluginConfig = pluginConfig;
        this.playerRegistry = playerRegistry;
        this.lobbyService = lobbyService;
        this.matchSpawnService = matchSpawnService;
        this.matchWorldService = matchWorldService;
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
        return gameState == GameState.HEAD_START && currentMatch != null && currentMatch.isHunter(player.getUniqueId());
    }

    public boolean isActiveRunner(UUID playerId) {
        return currentMatch != null && currentMatch.isRunner(playerId) && isLiveMatchState();
    }

    public boolean isActiveHunter(UUID playerId) {
        return currentMatch != null && currentMatch.isHunter(playerId) && isLiveMatchState();
    }

    public boolean isSpectator(UUID playerId) {
        return playerRegistry.getRole(playerId) == PlayerRole.SPECTATOR;
    }

    public boolean shouldPreventHunger(UUID playerId) {
        if (currentMatch == null) {
            return true;
        }

        return !isActiveRunner(playerId) && !isActiveHunter(playerId);
    }

    public boolean shouldHuntersKeepInventory() {
        return pluginConfig.shouldHuntersKeepInventory();
    }

    public boolean canUseRunnerRole(Player player) {
        List<Player> onlineRunners = playerRegistry.getOnlinePlayersWithRole(Bukkit.getOnlinePlayers(), PlayerRole.RUNNER);
        return onlineRunners.isEmpty()
            || (onlineRunners.size() == 1 && onlineRunners.get(0).getUniqueId().equals(player.getUniqueId()));
    }

    public boolean isRoleSelectionLocked(Player player) {
        return currentMatch != null && currentMatch.involves(player.getUniqueId()) && (isLiveMatchState() || gameState == GameState.ENDING);
    }

    public String getLobbyStatusSummary() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int runnerCount = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.RUNNER).size();
        int hunterCount = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.HUNTER).size();
        int spectatorCount = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.SPECTATOR).size();
        int queuedCount = playerRegistry.countOnlinePlayersWithAnyRole(onlinePlayers, PlayerRole.RUNNER, PlayerRole.HUNTER);
        int readyCount = 0;

        for (Player player : onlinePlayers) {
            PlayerRole role = playerRegistry.getRole(player.getUniqueId());
            if ((role == PlayerRole.RUNNER || role == PlayerRole.HUNTER) && playerRegistry.isReady(player.getUniqueId())) {
                readyCount++;
            }
        }

        String summary = "Queued: " + runnerCount + " runner, " + hunterCount + " hunters, " + readyCount + "/" + queuedCount + " ready";
        if (spectatorCount > 0) {
            summary += ", " + spectatorCount + " spectating";
        }
        return summary + ".";
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

    public void handleHunterDeath(Player hunter) {
        if (!isActiveHunter(hunter.getUniqueId())) {
            return;
        }

        hunter.sendMessage("[HuntCore] You can respawn and keep hunting.");
    }

    public void handleRunnerDragonKill(Player runner) {
        if (!isActiveRunner(runner.getUniqueId())) {
            return;
        }

        endMatch(MatchWinner.RUNNER, runner.getName() + " killed the Ender Dragon.");
    }

    public Location getPortalDestination(Player player, PlayerTeleportEvent.TeleportCause cause, Location fromLocation) {
        World fromWorld = fromLocation.getWorld();
        if (currentMatch == null || !currentMatch.involves(player.getUniqueId()) || !isLiveMatchState()) {
            return null;
        }

        if (fromWorld == null || !currentMatch.getMatchWorldSet().containsWorld(fromWorld.getName())) {
            return null;
        }

        Location destination = switch (cause) {
            case NETHER_PORTAL -> getNetherPortalDestination(fromLocation);
            case END_PORTAL -> getEndPortalDestination(fromLocation);
            default -> null;
        };

        rememberParticipantLocation(player, destination);
        return destination;
    }

    public void handlePlayerQuit(Player player) {
        if ((!isLiveMatchState() && gameState != GameState.ENDING) || currentMatch == null) {
            return;
        }

        if (!currentMatch.involves(player.getUniqueId())) {
            return;
        }

        rememberParticipantLocation(player, player.getLocation());

        if (currentMatch.isRunner(player.getUniqueId()) && gameState != GameState.ENDING) {
            startRunnerDisconnectTimer(player.getName());
            return;
        }

        if (currentMatch.isHunter(player.getUniqueId()) && gameState != GameState.ENDING && !hasAnyActiveHunters(player.getUniqueId())) {
            startHuntersDisconnectTimer();
        }
    }

    public boolean shouldKeepPlayerRegisteredOnQuit(UUID playerId) {
        return currentMatch != null && currentMatch.involves(playerId) && isLiveMatchState();
    }

    public boolean handlePlayerJoin(Player player) {
        if (currentMatch == null || !currentMatch.involves(player.getUniqueId()) || !isLiveMatchState()) {
            return false;
        }

        if (currentMatch.isRunner(player.getUniqueId())) {
            cancelTask(runnerDisconnectTask);
            runnerDisconnectTask = null;
            prepareReturningRunner(player, resolveRunnerReturnLocation());
            player.sendMessage("[HuntCore] You rejoined the match.");
            return true;
        }

        if (currentMatch.isHunter(player.getUniqueId())) {
            cancelTask(huntersDisconnectTask);
            huntersDisconnectTask = null;
            prepareReturningHunter(player, resolveHunterReturnLocation(player.getUniqueId()));
            player.sendMessage("[HuntCore] You rejoined the hunt.");
            return true;
        }

        return false;
    }

    public boolean toggleSpectatorMode(Player player) {
        UUID playerId = player.getUniqueId();
        if (isRoleSelectionLocked(player)) {
            player.sendMessage("[HuntCore] Active players cannot switch to spectator mode until the round ends.");
            return true;
        }

        playerRegistry.registerPlayer(player);
        if (isSpectator(playerId)) {
            playerRegistry.setRole(playerId, PlayerRole.NONE);
            lobbyService.sendToLobby(player, true);
            player.sendMessage("[HuntCore] Spectator mode disabled. Choose /runner or /hunter for the next round.");
            player.sendMessage("[HuntCore] " + getLobbyStatusSummary());
            handleLobbyStateChange();
            return true;
        }

        playerRegistry.setRole(playerId, PlayerRole.SPECTATOR);
        if (isLiveMatchState()) {
            sendPlayerToSpectatorView(player, true);
        } else {
            lobbyService.sendToLobby(player, true);
            player.sendMessage("[HuntCore] You will spectate and will not count toward ready checks.");
        }

        player.sendMessage("[HuntCore] " + getLobbyStatusSummary());
        handleLobbyStateChange();
        return true;
    }

    public Location getRespawnLocation(Player player) {
        if (currentMatch == null) {
            return null;
        }

        UUID playerId = player.getUniqueId();
        if (currentMatch.isHunter(playerId) && isLiveMatchState()) {
            Location hunterRespawn = spreadHunterSpawn(currentMatch.getMatchSpawn(), currentMatch.getHunterSpawnIndex(playerId));
            currentMatch.rememberParticipantLocation(playerId, hunterRespawn);
            return hunterRespawn;
        }

        if (currentMatch.involves(playerId) || gameState == GameState.ENDING) {
            return lobbyService.getLobbySpawn(player);
        }

        return null;
    }

    public void handlePostRespawn(Player player) {
        if (currentMatch == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        if (currentMatch.isHunter(playerId) && isLiveMatchState()) {
            prepareHunterForRespawn(player);
            return;
        }

        if (currentMatch.involves(playerId) || gameState == GameState.ENDING) {
            lobbyService.prepareForLobby(player, true);
        }
    }

    public void shutdown() {
        matchCountdown.cancel();
        cancelTask(headStartTask);
        cancelTask(endTask);
        cancelTask(runnerDisconnectTask);
        cancelTask(huntersDisconnectTask);
        compassTracker.stop();
        compassTracker.resetTrackingState();
        cleanupActiveMatchWorlds();
    }

    private void startMatchCountdown() {
        gameState = GameState.COUNTDOWN;
        Bukkit.broadcastMessage("[HuntCore] All players are ready. Match starts in " + pluginConfig.getMatchStartSeconds() + " seconds.");

        matchCountdown.start(
            pluginConfig.getMatchStartSeconds(),
            seconds -> {
                sendQueuedActionBar("Match starts in " + seconds + "s");
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
        List<Player> spectators = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.SPECTATOR);

        if (runners.size() != 1 || hunters.isEmpty()) {
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because the teams changed.");
            return;
        }

        Player runner = runners.get(0);
        MatchWorldSet matchWorldSet = matchWorldService.createFreshWorldSet();
        World matchWorld = matchWorldService.getOverworld(matchWorldSet);

        Location matchSpawn = matchSpawnService.findSafeSpawn(matchWorld).orElse(null);
        if (matchSpawn == null) {
            matchWorldService.cleanup(matchWorldSet);
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because no safe spawn was found.");
            return;
        }

        // TODO HuntCore v2: support multiple runners and shared hunter target rules.
        StructureHint structureHint = structureHintService.findNearestHint(matchSpawn).orElse(null);
        compassTracker.resetTrackingState();
        currentMatch = new MatchContext(
            runner.getUniqueId(),
            hunters.stream().map(Player::getUniqueId).toList(),
            matchSpawn,
            structureHint,
            matchWorldSet
        );

        preparePlayerForMatch(runner);
        runner.teleport(matchSpawn);
        currentMatch.rememberParticipantLocation(runner.getUniqueId(), matchSpawn);

        for (int index = 0; index < hunters.size(); index++) {
            Player hunter = hunters.get(index);
            Location hunterSpawn = spreadHunterSpawn(matchSpawn, index);
            preparePlayerForMatch(hunter);
            hunter.teleport(hunterSpawn);
            currentMatch.rememberParticipantLocation(hunter.getUniqueId(), hunterSpawn);
        }

        resetDragonAdvancement(runner);
        for (Player hunter : hunters) {
            resetDragonAdvancement(hunter);
        }

        gameState = GameState.HEAD_START;
        for (Player spectator : spectators) {
            sendPlayerToSpectatorView(spectator, false);
        }

        if (structureHint != null) {
            runner.getInventory().addItem(structureHintService.createHintBook(structureHint));
            runner.sendMessage("[HuntCore] A scout note has been added to your inventory.");
        } else {
            runner.sendMessage("[HuntCore] No nearby structure hint was found for this round.");
        }

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
        sendHeadStartActionBar(headStartSeconds);

        final int[] remaining = {headStartSeconds};
        headStartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            remaining[0]--;

            if (remaining[0] <= 0) {
                cancelTask(headStartTask);
                headStartTask = null;
                releaseHunters(runner);
                return;
            }

            sendHeadStartActionBar(remaining[0]);
            if (remaining[0] <= 5) {
                Bukkit.broadcastMessage("[HuntCore] Hunters release in " + remaining[0] + "...");
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
            hunter.sendActionBar(Component.text("Track the runner.", NamedTextColor.GOLD));
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

        MatchContext finishedMatch = currentMatch;
        gameState = GameState.ENDING;
        matchCountdown.cancel();
        cancelTask(headStartTask);
        headStartTask = null;
        cancelTask(runnerDisconnectTask);
        runnerDisconnectTask = null;
        cancelTask(huntersDisconnectTask);
        huntersDisconnectTask = null;
        compassTracker.stop();

        String winnerText = winner == MatchWinner.RUNNER ? "Runner" : "Hunters";
        String durationText = formatDuration(System.currentTimeMillis() - finishedMatch.getStartedAtMillis());
        Bukkit.broadcastMessage("[HuntCore] " + winnerText + " win! " + reason + " Duration: " + durationText + ".");
        showMatchSummary(winnerText, reason, durationText);

        // TODO HuntCore v2: expand the end summary into a reusable match recap object when stats are added.
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
        gameState = GameState.LOBBY;
        compassTracker.resetTrackingState();
        cleanupActiveMatchWorlds();
        Bukkit.broadcastMessage("[HuntCore] Returned to the lobby. Choose /runner, /hunter, or /spectate.");
    }

    private String getStartBlocker() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int queuedPlayers = playerRegistry.countOnlinePlayersWithAnyRole(onlinePlayers, PlayerRole.RUNNER, PlayerRole.HUNTER);
        if (queuedPlayers < 2) {
            return "Need at least 1 runner and 1 hunter queued.";
        }

        List<Player> runners = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.RUNNER);
        List<Player> hunters = playerRegistry.getOnlinePlayersWithRole(onlinePlayers, PlayerRole.HUNTER);

        if (runners.isEmpty()) {
            return "Need at least 1 runner.";
        }

        if (runners.size() > 1) {
            return "v2 currently supports exactly 1 runner.";
        }

        if (hunters.isEmpty()) {
            return "Need at least 1 hunter.";
        }

        if (!playerRegistry.areAllQueuedPlayersReadyAndAssigned(onlinePlayers)) {
            return "All runners and hunters must choose a role and use /ready.";
        }

        return null;
    }

    private boolean hasAnyActiveHunters(UUID excludedHunterId) {
        if (currentMatch == null) {
            return false;
        }

        for (UUID hunterId : currentMatch.getHunterIds()) {
            if (hunterId.equals(excludedHunterId)) {
                continue;
            }

            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline()) {
                return true;
            }
        }

        return false;
    }

    private void startRunnerDisconnectTimer(String runnerName) {
        cancelTask(runnerDisconnectTask);
        int graceSeconds = pluginConfig.getDisconnectGraceSeconds();
        Bukkit.broadcastMessage(
            "[HuntCore] " + runnerName + " disconnected. Hunters win in " + graceSeconds + " seconds if the runner does not return."
        );

        runnerDisconnectTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runnerDisconnectTask = null;
            if (currentMatch == null || gameState == GameState.ENDING) {
                return;
            }

            Player runner = Bukkit.getPlayer(currentMatch.getRunnerId());
            if (runner == null || !runner.isOnline()) {
                endMatch(MatchWinner.HUNTERS, "The runner did not return after disconnecting.");
            }
        }, graceSeconds * 20L);
    }

    private void startHuntersDisconnectTimer() {
        cancelTask(huntersDisconnectTask);
        int graceSeconds = pluginConfig.getDisconnectGraceSeconds();
        Bukkit.broadcastMessage(
            "[HuntCore] All hunters disconnected. Runner wins in " + graceSeconds + " seconds if no hunter returns."
        );

        huntersDisconnectTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            huntersDisconnectTask = null;
            if (currentMatch == null || gameState == GameState.ENDING) {
                return;
            }

            if (!hasAnyActiveHunters(null)) {
                endMatch(MatchWinner.RUNNER, "No hunters returned after disconnecting.");
            }
        }, graceSeconds * 20L);
    }

    private List<Player> getActiveOnlineHunters() {
        if (currentMatch == null) {
            return List.of();
        }

        List<Player> hunters = new ArrayList<>();
        for (UUID hunterId : currentMatch.getHunterIds()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter != null && hunter.isOnline()) {
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

    private void prepareHunterForRespawn(Player player) {
        player.setGameMode(GameMode.SURVIVAL);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }

        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setAllowFlight(false);
        player.setFlying(false);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        compassTracker.giveHunterCompass(player);
        if (gameState == GameState.HEAD_START) {
            player.sendMessage("[HuntCore] You respawned early. Wait for the hunter release.");
            return;
        }

        player.sendMessage("[HuntCore] Back in the hunt.");
    }

    private void prepareReturningRunner(Player player, Location targetLocation) {
        player.setGameMode(GameMode.SURVIVAL);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(maxHealth.getValue());
        }

        player.setAllowFlight(false);
        player.setFlying(false);
        if (targetLocation != null) {
            player.teleport(targetLocation);
            rememberParticipantLocation(player, targetLocation);
        }
    }

    private void prepareReturningHunter(Player player, Location targetLocation) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        compassTracker.giveHunterCompass(player);
        if (targetLocation != null) {
            player.teleport(targetLocation);
            rememberParticipantLocation(player, targetLocation);
        }

        if (gameState == GameState.HEAD_START) {
            player.sendMessage("[HuntCore] Hunters are still frozen for the head start.");
        }
    }

    private Location getNetherPortalDestination(Location fromLocation) {
        if (currentMatch == null) {
            return null;
        }

        World fromWorld = fromLocation.getWorld();
        if (fromWorld == null) {
            return null;
        }

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            World nether = matchWorldService.getNether(currentMatch.getMatchWorldSet());
            return resolveScaledPortalDestination(nether, fromLocation.getX() / 8.0, fromLocation.getZ() / 8.0);
        }

        if (fromWorld.getEnvironment() == World.Environment.NETHER) {
            World overworld = matchWorldService.getOverworld(currentMatch.getMatchWorldSet());
            return resolveScaledPortalDestination(overworld, fromLocation.getX() * 8.0, fromLocation.getZ() * 8.0);
        }

        return null;
    }

    private Location getEndPortalDestination(Location fromLocation) {
        if (currentMatch == null) {
            return null;
        }

        World fromWorld = fromLocation.getWorld();
        if (fromWorld == null) {
            return null;
        }

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            World end = matchWorldService.getEnd(currentMatch.getMatchWorldSet());
            return end == null ? null : end.getSpawnLocation();
        }

        if (fromWorld.getEnvironment() == World.Environment.THE_END) {
            World overworld = matchWorldService.getOverworld(currentMatch.getMatchWorldSet());
            return overworld == null ? null : overworld.getSpawnLocation();
        }

        return null;
    }

    private void resetDragonAdvancement(Player player) {
        Advancement dragonAdvancement = Bukkit.getAdvancement(KILL_DRAGON_ADVANCEMENT);
        if (dragonAdvancement == null) {
            return;
        }

        AdvancementProgress progress = player.getAdvancementProgress(dragonAdvancement);
        for (String criterion : List.copyOf(progress.getAwardedCriteria())) {
            progress.revokeCriteria(criterion);
        }
    }

    private Location resolveRunnerReturnLocation() {
        if (currentMatch == null) {
            return null;
        }

        Location lastKnown = currentMatch.getLastKnownLocation(currentMatch.getRunnerId());
        if (isValidMatchLocation(lastKnown)) {
            return lastKnown;
        }

        return currentMatch.getMatchSpawn();
    }

    private Location resolveHunterReturnLocation(UUID hunterId) {
        if (currentMatch == null) {
            return null;
        }

        Location lastKnown = currentMatch.getLastKnownLocation(hunterId);
        if (isValidMatchLocation(lastKnown)) {
            return lastKnown;
        }

        return spreadHunterSpawn(currentMatch.getMatchSpawn(), currentMatch.getHunterSpawnIndex(hunterId));
    }

    private boolean isValidMatchLocation(Location location) {
        return location != null
            && location.getWorld() != null
            && currentMatch != null
            && currentMatch.getMatchWorldSet().containsWorld(location.getWorld().getName());
    }

    private void rememberParticipantLocation(Player player, Location location) {
        if (currentMatch == null || player == null || location == null || location.getWorld() == null) {
            return;
        }

        if (!currentMatch.involves(player.getUniqueId())) {
            return;
        }

        if (!currentMatch.getMatchWorldSet().containsWorld(location.getWorld().getName())) {
            return;
        }

        currentMatch.rememberParticipantLocation(player.getUniqueId(), location);
    }

    private Location resolveScaledPortalDestination(World targetWorld, double targetX, double targetZ) {
        if (targetWorld == null) {
            return null;
        }

        return matchSpawnService.findSafeSpawnNear(targetWorld, targetX, targetZ, 8).orElse(targetWorld.getSpawnLocation());
    }

    private void cleanupActiveMatchWorlds() {
        if (currentMatch == null) {
            return;
        }

        MatchWorldSet worldSet = currentMatch.getMatchWorldSet();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (worldSet.containsWorld(onlinePlayer.getWorld().getName())) {
                lobbyService.sendToLobby(onlinePlayer, true);
            }
        }

        matchWorldService.cleanup(worldSet);
        currentMatch = null;
    }

    private void sendPlayerToSpectatorView(Player player, boolean notify) {
        if (currentMatch == null) {
            lobbyService.sendToLobby(player, true);
            return;
        }

        // TODO HuntCore v2: allow configurable spectator anchors and camera rules.
        player.getInventory().clear();
        player.setGameMode(GameMode.SPECTATOR);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setAllowFlight(true);
        player.setFlying(true);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        Player runner = Bukkit.getPlayer(currentMatch.getRunnerId());
        Location target = runner != null && runner.isOnline() ? runner.getLocation() : currentMatch.getMatchSpawn();
        player.teleport(target);

        if (notify) {
            player.sendMessage("[HuntCore] You are now spectating. Use /spectate again to leave spectator mode.");
        }
    }

    private void sendQueuedActionBar(String message) {
        Component component = Component.text(message, NamedTextColor.YELLOW);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerRole role = playerRegistry.getRole(player.getUniqueId());
            if (role == PlayerRole.RUNNER || role == PlayerRole.HUNTER) {
                player.sendActionBar(component);
            }
        }
    }

    private void sendHeadStartActionBar(int seconds) {
        if (currentMatch == null) {
            return;
        }

        Player runner = Bukkit.getPlayer(currentMatch.getRunnerId());
        if (runner != null && runner.isOnline()) {
            runner.sendActionBar(Component.text("Head start: " + seconds + "s", NamedTextColor.GREEN));
        }

        Component hunterMessage = Component.text("Release in " + seconds + "s", NamedTextColor.RED);
        for (Player hunter : getActiveOnlineHunters()) {
            hunter.sendActionBar(hunterMessage);
        }
    }

    private void showMatchSummary(String winnerText, String reason, String durationText) {
        String titleText = winnerText.equals("Runner") ? "Runner Wins" : "Hunters Win";
        Title title = Title.title(
            Component.text(titleText, NamedTextColor.GOLD),
            Component.text(reason + " | " + durationText, NamedTextColor.WHITE),
            Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(700))
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.sendMessage("[HuntCore] Winner: " + winnerText);
            player.sendMessage("[HuntCore] Reason: " + reason);
            player.sendMessage("[HuntCore] Duration: " + durationText);
        }
    }

    private String formatDuration(long durationMillis) {
        Duration duration = Duration.ofMillis(Math.max(durationMillis, 0L));
        long totalSeconds = duration.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
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
