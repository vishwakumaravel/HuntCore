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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class GameManager {

    private static final int PREPARED_MATCH_CACHE_SIZE = 2;
    private static final int CACHE_MAINTENANCE_INTERVAL_TICKS = 20 * 20;
    private static final int RECENT_POI_HISTORY_LIMIT = 4;
    private static final int MATCH_SPAWN_CANDIDATE_LIMIT = 8;
    private static final int[] PRECOMPUTE_CHUNK_RADII = {8, 14, 25};
    private static final int IDEAL_POI_DISTANCE_BLOCKS = 220;
    private static final int GOOD_POI_DISTANCE_BLOCKS = 300;
    private static final int ACCEPTABLE_POI_DISTANCE_BLOCKS = 360;
    private static final int MAX_POI_DISTANCE_BLOCKS = 400;
    private static final int END_PLATFORM_CENTER_X = 100;
    private static final int END_PLATFORM_CENTER_Z = 0;
    private static final int END_PLATFORM_Y = 49;

    private final HuntCorePlugin plugin;
    private final PluginConfig pluginConfig;
    private final PlayerRegistry playerRegistry;
    private final LobbyService lobbyService;
    private final MatchSpawnService matchSpawnService;
    private final MatchWorldService matchWorldService;
    private final StructureHintService structureHintService;
    private final MatchCountdown matchCountdown;
    private final CompassTracker compassTracker;
    private final PausedMatchStore pausedMatchStore;
    private final PreparedMatchStore preparedMatchStore;
    private final MatchStatsStore matchStatsStore;
    private final TeleportSafetyService teleportSafetyService;

    private GameState gameState = GameState.LOBBY;
    private MatchContext currentMatch;
    private final List<MatchPreparation> preparedMatches = new ArrayList<>();
    private final Deque<String> recentPoiHistory = new ArrayDeque<>();
    private UUID activePreparationId;
    private int preparationAttempts;
    private BukkitTask cacheMaintenanceTask;
    private BukkitTask headStartTask;
    private BukkitTask endTask;
    private BukkitTask runnerDisconnectTask;
    private BukkitTask huntersDisconnectTask;
    private GameState pausedResumeState = GameState.IN_GAME;
    private int headStartSecondsRemaining;

    public GameManager(
        HuntCorePlugin plugin,
        PluginConfig pluginConfig,
        PlayerRegistry playerRegistry,
        LobbyService lobbyService,
        MatchSpawnService matchSpawnService,
        MatchWorldService matchWorldService,
        StructureHintService structureHintService,
        MatchCountdown matchCountdown,
        CompassTracker compassTracker,
        PausedMatchStore pausedMatchStore,
        PreparedMatchStore preparedMatchStore,
        MatchStatsStore matchStatsStore,
        TeleportSafetyService teleportSafetyService
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
        this.pausedMatchStore = pausedMatchStore;
        this.preparedMatchStore = preparedMatchStore;
        this.matchStatsStore = matchStatsStore;
        this.teleportSafetyService = teleportSafetyService;
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

    public boolean isPausedMatchState() {
        return gameState == GameState.PAUSED;
    }

    public void handleParticipantKill(Player killer, Player victim) {
        if (currentMatch == null || killer == null || victim == null) {
            return;
        }

        if (!isLiveMatchState()) {
            return;
        }

        if (killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        if (currentMatch.involves(killer.getUniqueId()) && currentMatch.involves(victim.getUniqueId())) {
            currentMatch.recordKill(killer.getUniqueId());
        }
    }

    public List<String> getStatusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("[HuntCore] State: " + formatState(gameState));
        lines.add(
            "[HuntCore] Prescouted worlds: "
                + preparedMatches.size()
                + "/"
                + PREPARED_MATCH_CACHE_SIZE
                + (activePreparationId == null ? " (idle)." : " (scouting).")
        );
        lines.add("[HuntCore] Background scouting: " + describeBackgroundScoutingStatus() + ".");

        if (currentMatch != null) {
            String runnerName = resolvePlayerName(currentMatch.getRunnerId());
            String poiText = currentMatch.getStructureHint() == null
                ? "none"
                : currentMatch.getStructureHint().displayName() + " (" + currentMatch.getStructureHint().approximateDistanceBlocks() + " blocks)";
            lines.add("[HuntCore] Active runner: " + runnerName + ".");
            lines.add("[HuntCore] Active hunters: " + currentMatch.getHunterIds().size() + ".");
            lines.add("[HuntCore] Match POI: " + poiText + ".");
        } else {
            lines.add("[HuntCore] Lobby summary: " + getLobbyStatusSummary());
        }

        if (!preparedMatches.isEmpty()) {
            MatchPreparation bestPrepared = selectBestPreparedMatch();
            if (bestPrepared != null && bestPrepared.structureHint() != null) {
                lines.add(
                    "[HuntCore] Best cached start: "
                        + bestPrepared.structureHint().displayName()
                        + " about "
                        + bestPrepared.structureHint().approximateDistanceBlocks()
                        + " blocks away."
                );
            }
        }

        MatchStatsStore.MatchStatSnapshot latestMatch = matchStatsStore.loadLatest();
        if (latestMatch != null) {
            lines.add(
                "[HuntCore] Last match: "
                    + latestMatch.winner()
                    + " won in "
                    + formatDuration(latestMatch.durationMillis())
                    + " | runner "
                    + latestMatch.runnerName()
                    + " | POI "
                    + latestMatch.poiName()
                    + "."
            );
        }

        return lines;
    }

    public List<String> getMatchHistoryLines(int limit) {
        List<MatchStatsStore.MatchStatSnapshot> history = matchStatsStore.load();
        if (history.isEmpty()) {
            return List.of("[HuntCore] No match history has been recorded yet.");
        }

        int clampedLimit = Math.max(1, Math.min(limit, Math.min(10, history.size())));
        List<String> lines = new ArrayList<>();
        lines.add("[HuntCore] Recent match stats:");
        for (int index = 0; index < clampedLimit; index++) {
            MatchStatsStore.MatchStatSnapshot snapshot = history.get(index);
            lines.add(
                "[HuntCore] #"
                    + (index + 1)
                    + " "
                    + snapshot.winner()
                    + " won in "
                    + formatDuration(snapshot.durationMillis())
                    + " | runner "
                    + snapshot.runnerName()
                    + " | hunters "
                    + snapshot.hunterCount()
                    + " | POI "
                    + snapshot.poiName()
                    + " ("
                    + snapshot.poiDistanceBlocks()
                    + " blocks)"
            );
            lines.add("[HuntCore] Reason: " + snapshot.reason());
            lines.add("[HuntCore] Kills: " + formatRecordedKills(snapshot.playerKills()));
        }

        return lines;
    }

    public boolean isHunterFrozen(Player player) {
        return gameState == GameState.HEAD_START && currentMatch != null && currentMatch.isHunter(player.getUniqueId());
    }

    public boolean isMovementLocked(Player player) {
        if (currentMatch == null || !currentMatch.involves(player.getUniqueId())) {
            return false;
        }

        return gameState == GameState.PAUSED || isHunterFrozen(player);
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

    public boolean hasPausedMatch() {
        return currentMatch != null && gameState == GameState.PAUSED;
    }

    public boolean shouldPreventHunger(UUID playerId) {
        if (isSpectator(playerId)) {
            return true;
        }

        if (currentMatch == null) {
            return false;
        }

        if (gameState == GameState.PAUSED) {
            return currentMatch.involves(playerId);
        }

        if (!currentMatch.involves(playerId)) {
            return false;
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
        return currentMatch != null
            && currentMatch.involves(player.getUniqueId())
            && (isLiveMatchState() || gameState == GameState.PAUSED || gameState == GameState.ENDING);
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
        if (gameState == GameState.PAUSED || gameState == GameState.ENDING) {
            return;
        }

        if (!hasOnlinePlayers()) {
            cancelMatchPreparation();

            if (gameState == GameState.COUNTDOWN) {
                cancelMatchCountdown("No players are online to keep the lobby active.", true);
            }
            return;
        }

        String startBlocker = getStartBlocker();
        if (hasPreparedMatches()) {
            if (startBlocker == null) {
                if (gameState == GameState.LOBBY) {
                    startMatchCountdown();
                }
            } else if (gameState == GameState.COUNTDOWN) {
                cancelMatchCountdown(startBlocker, true);
            }

            if (activePreparationId == null && shouldStartPreparationNow()) {
                startMatchPreparation();
            }
            return;
        }

        if (gameState == GameState.COUNTDOWN && startBlocker != null) {
            cancelMatchCountdown(startBlocker, true);
        }

        if (activePreparationId == null && shouldStartPreparationNow()) {
            startMatchPreparation();
        }
    }

    private void startMatchPreparation() {
        if (!shouldScoutPreparedMatch() || activePreparationId != null || !hasPreparedMatchCapacity()) {
            return;
        }
        preparationAttempts++;

        MatchWorldSet matchWorldSet = matchWorldService.createFreshWorldSet();
        World matchWorld = matchWorldService.getOverworld(matchWorldSet);
        List<Location> spawnCandidates = matchSpawnService.findSafeSpawnCandidates(matchWorld, MATCH_SPAWN_CANDIDATE_LIMIT);
        SpawnSelection selectedSpawn = selectMatchSpawn(spawnCandidates);
        if (selectedSpawn.spawn() == null) {
            matchWorldService.cleanup(matchWorldSet);
            Bukkit.broadcastMessage("[HuntCore] Match preparation failed because no safe spawn was found.");
            return;
        }

        MatchPreparation preparation = new MatchPreparation(
            matchWorldSet,
            selectedSpawn.spawn(),
            selectedSpawn.structureHint()
        );
        UUID preparationId = UUID.randomUUID();
        activePreparationId = preparationId;
        if (preparationAttempts == 1) {
            Bukkit.broadcastMessage("[HuntCore] Scouting the next match world...");
        } else if (preparationAttempts <= 3 || preparationAttempts % 3 == 0) {
            Bukkit.broadcastMessage("[HuntCore] Still scouting for a strong nearby POI...");
        }

        scoutPreparationChunks(preparationId, preparation, 0);
    }

    private void scoutPreparationChunks(UUID preparationId, MatchPreparation preparation, int radiusIndex) {
        if (activePreparationId == null || !activePreparationId.equals(preparationId)) {
            return;
        }

        World matchWorld = matchWorldService.getOverworld(preparation.matchWorldSet());
        if (matchWorld == null) {
            activePreparationId = null;
            matchWorldService.cleanup(preparation.matchWorldSet());
            return;
        }

        int chunkRadius = PRECOMPUTE_CHUNK_RADII[Math.min(radiusIndex, PRECOMPUTE_CHUNK_RADII.length - 1)];
        List<ChunkCoordinate> chunkWindow = buildPrecomputeChunkWindow(preparation.matchSpawn(), chunkRadius);
        preloadPreparedChunks(matchWorld, chunkWindow).whenComplete((ignored, throwable) ->
            Bukkit.getScheduler().runTask(plugin, () ->
                finalizeMatchPreparation(preparationId, preparation, radiusIndex, chunkWindow, throwable)
            )
        );
    }

    private void finalizeMatchPreparation(
        UUID preparationId,
        MatchPreparation preparation,
        int radiusIndex,
        List<ChunkCoordinate> chunkWindow,
        Throwable throwable
    ) {
        if (activePreparationId == null || !activePreparationId.equals(preparationId)) {
            matchWorldService.cleanup(preparation.matchWorldSet());
            return;
        }

        if (throwable != null) {
            activePreparationId = null;
            matchWorldService.cleanup(preparation.matchWorldSet());
            Bukkit.broadcastMessage("[HuntCore] Match preparation failed. Try /ready again.");
            return;
        }

        if (!shouldScoutPreparedMatch()) {
            activePreparationId = null;
            matchWorldService.cleanup(preparation.matchWorldSet());
            return;
        }

        StructureHint preparedHint = structureHintService.findNearestStructureHint(
            preparation.matchSpawn(),
            collectLoadedChunks(preparation.matchWorldSet(), chunkWindow),
            getDiscouragedPoiTypes()
        ).orElse(null);

        if (preparedHint != null && shouldAcceptPreparedHint(preparedHint)) {
            activePreparationId = null;
            preparedMatches.add(new MatchPreparation(
                preparation.matchWorldSet(),
                preparation.matchSpawn(),
                preparedHint
            ));
            preparationAttempts = 0;
            persistPreparedMatches();
            announcePreparedMatchStatus();

            if (gameState == GameState.LOBBY && getStartBlocker() == null) {
                startMatchCountdown();
            } else if (shouldStartPreparationNow()) {
                Bukkit.getScheduler().runTask(plugin, this::startMatchPreparation);
            }
            return;
        }

        if (radiusIndex + 1 < PRECOMPUTE_CHUNK_RADII.length) {
            scoutPreparationChunks(preparationId, preparation, radiusIndex + 1);
            return;
        }

        activePreparationId = null;
        matchWorldService.cleanup(preparation.matchWorldSet());
        if (shouldScoutPreparedMatch() && gameState == GameState.LOBBY) {
            Bukkit.getScheduler().runTask(plugin, this::startMatchPreparation);
        }
    }

    private CompletableFuture<Void> preloadPreparedChunks(World world, List<ChunkCoordinate> chunkWindow) {
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        for (ChunkCoordinate chunkCoordinate : chunkWindow) {
            futures.add(world.getChunkAtAsync(chunkCoordinate.x(), chunkCoordinate.z(), true));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private List<ChunkCoordinate> buildPrecomputeChunkWindow(Location center, int chunkRadius) {
        int centerChunkX = center.getBlockX() >> 4;
        int centerChunkZ = center.getBlockZ() >> 4;
        List<ChunkCoordinate> chunkWindow = new ArrayList<>();
        for (int offsetX = -chunkRadius; offsetX <= chunkRadius; offsetX++) {
            for (int offsetZ = -chunkRadius; offsetZ <= chunkRadius; offsetZ++) {
                if ((offsetX * offsetX) + (offsetZ * offsetZ) > chunkRadius * chunkRadius) {
                    continue;
                }

                chunkWindow.add(new ChunkCoordinate(centerChunkX + offsetX, centerChunkZ + offsetZ));
            }
        }

        return chunkWindow;
    }

    private List<Chunk> collectLoadedChunks(MatchWorldSet worldSet, List<ChunkCoordinate> chunkWindow) {
        World overworld = matchWorldService.getOverworld(worldSet);
        List<Chunk> chunks = new ArrayList<>();
        if (overworld == null) {
            return chunks;
        }

        for (ChunkCoordinate chunkCoordinate : chunkWindow) {
            if (overworld.isChunkLoaded(chunkCoordinate.x(), chunkCoordinate.z())) {
                chunks.add(overworld.getChunkAt(chunkCoordinate.x(), chunkCoordinate.z(), false));
            }
        }

        return chunks;
    }

    private void cancelMatchPreparation() {
        activePreparationId = null;
        preparationAttempts = 0;
    }

    private void discardPreparedMatches() {
        if (preparedMatches.isEmpty()) {
            preparedMatchStore.delete();
            return;
        }

        for (MatchPreparation preparedMatch : List.copyOf(preparedMatches)) {
            matchWorldService.cleanup(preparedMatch.matchWorldSet());
        }
        preparedMatches.clear();
        preparedMatchStore.delete();
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
            case END_PORTAL -> getEndPortalDestination(player, fromLocation);
            default -> null;
        };

        rememberParticipantLocation(player, destination);
        return destination;
    }

    public void handlePlayerQuit(Player player) {
        if ((!isLiveMatchState() && gameState != GameState.PAUSED && gameState != GameState.ENDING) || currentMatch == null) {
            return;
        }

        if (!currentMatch.involves(player.getUniqueId())) {
            return;
        }

        rememberParticipantLocation(player, player.getLocation());

        if (gameState == GameState.PAUSED) {
            persistPausedMatch();
            return;
        }

        if (currentMatch.isRunner(player.getUniqueId()) && gameState != GameState.ENDING) {
            startRunnerDisconnectTimer(player.getName());
            return;
        }

        if (currentMatch.isHunter(player.getUniqueId()) && gameState != GameState.ENDING && !hasAnyActiveHunters(player.getUniqueId())) {
            startHuntersDisconnectTimer();
        }
    }

    public boolean shouldKeepPlayerRegisteredOnQuit(UUID playerId) {
        if (currentMatch == null) {
            return false;
        }

        if (currentMatch.involves(playerId) && (isLiveMatchState() || gameState == GameState.PAUSED)) {
            return true;
        }

        return isSpectator(playerId) && (isLiveMatchState() || gameState == GameState.PAUSED);
    }

    public boolean handlePlayerJoin(Player player) {
        if (currentMatch == null || (!isLiveMatchState() && gameState != GameState.PAUSED)) {
            return false;
        }

        if (isSpectator(player.getUniqueId())) {
            sendPlayerToSpectatorView(player, false);
            player.sendMessage(gameState == GameState.PAUSED
                ? "[HuntCore] You rejoined as a spectator while the match is paused."
                : "[HuntCore] You rejoined as a spectator.");
            return true;
        }

        if (!currentMatch.involves(player.getUniqueId())) {
            return false;
        }

        if (currentMatch.isRunner(player.getUniqueId())) {
            playerRegistry.setRole(player.getUniqueId(), PlayerRole.RUNNER);
            cancelTask(runnerDisconnectTask);
            runnerDisconnectTask = null;
            prepareReturningRunner(player, resolveRunnerReturnLocation());
            if (gameState == GameState.PAUSED) {
                applyPausedParticipantState(player);
                player.sendMessage("[HuntCore] You rejoined the paused match.");
            } else {
                player.sendMessage("[HuntCore] You rejoined the match.");
            }
            return true;
        }

        if (currentMatch.isHunter(player.getUniqueId())) {
            playerRegistry.setRole(player.getUniqueId(), PlayerRole.HUNTER);
            cancelTask(huntersDisconnectTask);
            huntersDisconnectTask = null;
            prepareReturningHunter(player, resolveHunterReturnLocation(player.getUniqueId()));
            if (gameState == GameState.PAUSED) {
                applyPausedParticipantState(player);
                player.sendMessage("[HuntCore] You rejoined the paused match.");
            } else {
                player.sendMessage("[HuntCore] You rejoined the hunt.");
            }
            return true;
        }

        return false;
    }

    public boolean resetPlayerToLobby(Player player) {
        if (isRoleSelectionLocked(player)) {
            player.sendMessage("[HuntCore] Active runners and hunters cannot use /reset during a live round.");
            return true;
        }

        lobbyService.sendToLobby(player, true);
        player.sendMessage("[HuntCore] Returned to the lobby spawn.");
        return true;
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

    public Location getRespawnLocation(Player player, Location vanillaRespawnLocation) {
        if (currentMatch == null) {
            return null;
        }

        UUID playerId = player.getUniqueId();
        if (currentMatch.isHunter(playerId) && (isLiveMatchState() || gameState == GameState.PAUSED)) {
            Location hunterRespawn = resolveHunterRespawnLocation(playerId, vanillaRespawnLocation);
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
        if (currentMatch.isHunter(playerId) && (isLiveMatchState() || gameState == GameState.PAUSED)) {
            prepareHunterForRespawn(player);
            if (gameState == GameState.PAUSED) {
                applyPausedParticipantState(player);
            }
            return;
        }

        if (currentMatch.involves(playerId) || gameState == GameState.ENDING) {
            lobbyService.prepareForLobby(player, true);
        }
    }

    public void shutdown() {
        matchCountdown.cancel();
        cancelMatchPreparation();
        cancelTask(headStartTask);
        cancelTask(endTask);
        cancelTask(runnerDisconnectTask);
        cancelTask(huntersDisconnectTask);
        cancelTask(cacheMaintenanceTask);
        headStartTask = null;
        endTask = null;
        runnerDisconnectTask = null;
        huntersDisconnectTask = null;
        cacheMaintenanceTask = null;
        compassTracker.stop();
        persistPreparedMatches();
        if (hasPausedMatch()) {
            persistPausedMatch();
            matchWorldService.saveWorldSet(currentMatch.getMatchWorldSet());
            return;
        }

        clearPausedMatchPersistence();
        compassTracker.resetTrackingState();
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;
        cleanupActiveMatchWorlds();
    }

    public void restorePausedMatchIfPresent() {
        if (!pausedMatchStore.exists()) {
            return;
        }

        PausedMatchStore.PausedMatchSnapshot snapshot = pausedMatchStore.load();
        if (snapshot == null) {
            plugin.getLogger().warning("Paused match snapshot could not be read. Leaving it untouched for manual recovery.");
            return;
        }

        try {
            matchWorldService.loadExistingWorldSet(snapshot.matchWorldSet());
            currentMatch = new MatchContext(
                snapshot.runnerId(),
                snapshot.hunterIds(),
                toLocation(snapshot.matchSpawn()),
                null,
                snapshot.matchWorldSet(),
                snapshot.startedAtMillis()
            );
            Location runnerLastKnownLocation = toLocation(snapshot.runnerLastKnownLocation());
            if (runnerLastKnownLocation != null) {
                currentMatch.rememberParticipantLocation(snapshot.runnerId(), runnerLastKnownLocation);
            }
            for (var entry : snapshot.hunterLastKnownLocations().entrySet()) {
                Location hunterLocation = toLocation(entry.getValue());
                if (hunterLocation != null) {
                    currentMatch.rememberParticipantLocation(entry.getKey(), hunterLocation);
                }
            }

            pausedResumeState = snapshot.resumeState();
            headStartSecondsRemaining = snapshot.headStartSecondsRemaining();
            gameState = GameState.PAUSED;
            compassTracker.resetTrackingState();
            plugin.getLogger().info("Restored a paused HuntCore match. Use /unpause once the runner and at least one hunter are online.");
        } catch (RuntimeException exception) {
            plugin.getLogger().warning(
                "Could not restore paused match worlds. The paused snapshot was left in place for manual recovery: "
                    + exception.getMessage()
            );
            currentMatch = null;
            gameState = GameState.LOBBY;
            pausedResumeState = GameState.IN_GAME;
            headStartSecondsRemaining = 0;
        }
    }

    public void restorePreparedMatchesIfPresent() {
        List<PreparedMatchStore.PreparedMatchSnapshot> snapshots = preparedMatchStore.load();
        if (snapshots.isEmpty()) {
            return;
        }

        boolean restoredAny = false;
        for (PreparedMatchStore.PreparedMatchSnapshot snapshot : snapshots) {
            if (!hasPreparedMatchCapacity()) {
                matchWorldService.cleanup(snapshot.matchWorldSet());
                continue;
            }

            try {
                matchWorldService.loadExistingWorldSet(snapshot.matchWorldSet());
                Location matchSpawn = toLocation(snapshot.matchSpawn());
                Location hintLocation = toLocation(snapshot.structureHint().location());
                if (matchSpawn == null || hintLocation == null) {
                    throw new IllegalStateException("Prepared match metadata had an invalid location.");
                }

                preparedMatches.add(
                    new MatchPreparation(
                        snapshot.matchWorldSet(),
                        matchSpawn,
                        new StructureHint(
                            snapshot.structureHint().landmarkName(),
                            hintLocation,
                            snapshot.structureHint().roughDirection(),
                            snapshot.structureHint().approximateDistanceBlocks(),
                            snapshot.structureHint().targetYawDegrees()
                        )
                    )
                );
                restoredAny = true;
                if (!hasPreparedMatchCapacity()) {
                    break;
                }
            } catch (RuntimeException exception) {
                matchWorldService.cleanup(snapshot.matchWorldSet());
                plugin.getLogger().warning(
                    "Discarded a cached prepared match that could not be restored: " + exception.getMessage()
                );
            }
        }

        if (restoredAny) {
            persistPreparedMatches();
            plugin.getLogger().info("Restored " + preparedMatches.size() + " cached HuntCore match world(s).");
        } else {
            preparedMatchStore.delete();
        }
    }

    public boolean pauseMatch(CommandSender sender) {
        if (!isLiveMatchState() || currentMatch == null) {
            sender.sendMessage("[HuntCore] There is no active match to pause.");
            return true;
        }

        pausedResumeState = gameState;
        cancelTask(headStartTask);
        headStartTask = null;
        cancelTask(runnerDisconnectTask);
        runnerDisconnectTask = null;
        cancelTask(huntersDisconnectTask);
        huntersDisconnectTask = null;
        compassTracker.stop();
        gameState = GameState.PAUSED;

        for (Player player : getActiveOnlineParticipants()) {
            applyPausedParticipantState(player);
        }

        persistPausedMatch();
        Bukkit.broadcastMessage("[HuntCore] Match paused. Players may disconnect until /unpause.");
        return true;
    }

    public boolean unpauseMatch(CommandSender sender) {
        if (gameState != GameState.PAUSED || currentMatch == null) {
            sender.sendMessage("[HuntCore] There is no paused match to resume.");
            return true;
        }

        Player runner = Bukkit.getPlayer(currentMatch.getRunnerId());
        if (runner == null || !runner.isOnline()) {
            sender.sendMessage("[HuntCore] The runner must be online before the match can resume.");
            return true;
        }

        List<Player> hunters = getActiveOnlineHunters();
        if (hunters.isEmpty()) {
            sender.sendMessage("[HuntCore] At least one hunter must be online before the match can resume.");
            return true;
        }

        for (Player player : getActiveOnlineParticipants()) {
            clearPausedParticipantState(player);
        }

        clearPausedMatchPersistence();

        if (pausedResumeState == GameState.HEAD_START && headStartSecondsRemaining > 0) {
            gameState = GameState.HEAD_START;
            Bukkit.broadcastMessage("[HuntCore] Match resumed. Hunters release in " + headStartSecondsRemaining + " seconds.");
            resumeHeadStart(runner);
            return true;
        }

        gameState = GameState.IN_GAME;
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;
        for (Player hunter : hunters) {
            compassTracker.giveHunterCompass(hunter);
            hunter.sendActionBar(Component.text("Tracking resumed.", NamedTextColor.GOLD));
        }

        compassTracker.start(
            () -> Bukkit.getPlayer(currentMatch.getRunnerId()),
            this::getActiveOnlineHunters
        );

        Bukkit.broadcastMessage("[HuntCore] Match resumed.");
        return true;
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
        cancelMatchCountdown(reason, false);
    }

    private void cancelMatchCountdown(String reason, boolean preservePreparedMatch) {
        matchCountdown.cancel();
        gameState = GameState.LOBBY;
        if (!preservePreparedMatch) {
            discardPreparedMatches();
        }
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
            handleLobbyStateChange();
            return;
        }

        Player runner = runners.get(0);
        MatchPreparation preparation = consumePreparedMatch();
        if (preparation == null) {
            gameState = GameState.LOBBY;
            Bukkit.broadcastMessage("[HuntCore] Match start failed because the prepared world was no longer valid.");
            handleLobbyStateChange();
            return;
        }
        announcePreparedMatchStatus();

        // TODO HuntCore v2: support multiple runners and shared hunter target rules.
        clearPausedMatchPersistence();
        compassTracker.resetTrackingState();
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;
        currentMatch = new MatchContext(
            runner.getUniqueId(),
            hunters.stream().map(Player::getUniqueId).toList(),
            preparation.matchSpawn(),
            preparation.structureHint(),
            preparation.matchWorldSet()
        );

        preparePlayerForMatch(runner);
        teleportSafetyService.teleport(runner, preparation.matchSpawn(), false, false);
        currentMatch.rememberParticipantLocation(runner.getUniqueId(), preparation.matchSpawn());

        for (int index = 0; index < hunters.size(); index++) {
            Player hunter = hunters.get(index);
            Location hunterSpawn = spreadHunterSpawn(preparation.matchSpawn(), index);
            preparePlayerForMatch(hunter);
            teleportSafetyService.teleport(hunter, hunterSpawn, false, false);
            currentMatch.rememberParticipantLocation(hunter.getUniqueId(), hunterSpawn);
        }

        resetAllAdvancements(runner);
        for (Player hunter : hunters) {
            resetAllAdvancements(hunter);
        }

        gameState = GameState.HEAD_START;
        for (Player spectator : spectators) {
            sendPlayerToSpectatorView(spectator, false);
        }

        if (preparation.structureHint() != null) {
            runner.getInventory().addItem(structureHintService.createHintBook(preparation.structureHint()));
            runner.sendMessage("[HuntCore] A scout note has been added to your inventory.");
        } else {
            runner.sendMessage("[HuntCore] No nearby structure hint was found for this round.");
        }

        Bukkit.broadcastMessage("[HuntCore] Match started. " + runner.getName() + " is the runner.");
        rememberRecentPoiType(preparation.structureHint().displayName());
        handleLobbyStateChange();
        startHeadStart(runner);
    }

    private void startHeadStart(Player runner) {
        cancelTask(headStartTask);

        int headStartSeconds = pluginConfig.getHunterHeadStartSeconds();
        if (headStartSeconds <= 0) {
            releaseHunters(runner);
            return;
        }

        startHeadStartCountdown(runner, headStartSeconds);
    }

    private void startHeadStartCountdown(Player runner, int headStartSeconds) {
        headStartSecondsRemaining = headStartSeconds;
        runner.sendMessage("[HuntCore] Run. Hunters are released in " + headStartSeconds + " seconds.");
        for (Player hunter : getActiveOnlineHunters()) {
            hunter.sendMessage("[HuntCore] Wait for the head start. Release in " + headStartSeconds + " seconds.");
        }
        sendHeadStartActionBar(headStartSeconds);

        headStartTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            headStartSecondsRemaining--;

            if (headStartSecondsRemaining <= 0) {
                cancelTask(headStartTask);
                headStartTask = null;
                releaseHunters(runner);
                return;
            }

            sendHeadStartActionBar(headStartSecondsRemaining);
            if (headStartSecondsRemaining <= 5) {
                Bukkit.broadcastMessage("[HuntCore] Hunters release in " + headStartSecondsRemaining + "...");
            }
        }, 20L, 20L);
    }

    private void resumeHeadStart(Player runner) {
        startHeadStartCountdown(runner, headStartSecondsRemaining);
    }

    private void releaseHunters(Player runner) {
        if (currentMatch == null) {
            return;
        }

        gameState = GameState.IN_GAME;
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;
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
        clearPausedMatchPersistence();
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;

        String winnerText = winner == MatchWinner.RUNNER ? "Runner" : "Hunters";
        long durationMillis = System.currentTimeMillis() - finishedMatch.getStartedAtMillis();
        String durationText = formatDuration(durationMillis);
        Bukkit.broadcastMessage("[HuntCore] " + winnerText + " win! " + reason + " Duration: " + durationText + ".");
        showMatchSummary(winnerText, reason, durationText);
        recordMatchStats(finishedMatch, winnerText, reason, durationMillis);

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
        clearPausedMatchPersistence();
        pausedResumeState = GameState.IN_GAME;
        headStartSecondsRemaining = 0;
        cleanupActiveMatchWorlds();
        Bukkit.broadcastMessage("[HuntCore] Returned to the lobby. Choose /runner, /hunter, or /spectate.");
        handleLobbyStateChange();
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

    private boolean shouldAcceptPreparedHint(StructureHint structureHint) {
        if (structureHint == null) {
            return false;
        }

        int distance = structureHint.approximateDistanceBlocks();
        if (distance <= IDEAL_POI_DISTANCE_BLOCKS) {
            return true;
        }

        if (preparationAttempts >= 2 && distance <= GOOD_POI_DISTANCE_BLOCKS) {
            return true;
        }

        if (preparationAttempts >= 3 && distance <= ACCEPTABLE_POI_DISTANCE_BLOCKS) {
            return true;
        }

        return preparationAttempts >= 4 && distance <= MAX_POI_DISTANCE_BLOCKS;
    }

    private boolean shouldScoutPreparedMatch() {
        return hasOnlinePlayers() && gameState == GameState.LOBBY;
    }

    private boolean hasOnlinePlayers() {
        return !Bukkit.getOnlinePlayers().isEmpty();
    }

    public void startCacheMaintenance() {
        cancelTask(cacheMaintenanceTask);
        cacheMaintenanceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::handleLobbyStateChange, 100L, CACHE_MAINTENANCE_INTERVAL_TICKS);
    }

    private boolean shouldStartPreparationNow() {
        return activePreparationId == null && hasPreparedMatchCapacity() && gameState == GameState.LOBBY;
    }

    private String describeBackgroundScoutingStatus() {
        if (activePreparationId != null) {
            return "active";
        }

        if (!shouldScoutPreparedMatch()) {
            return isLiveMatchState()
                ? "disabled during live match"
                : gameState == GameState.PAUSED || gameState == GameState.ENDING
                ? "paused by match state"
                : "idle";
        }

        if (!hasPreparedMatchCapacity()) {
            return "cache full";
        }

        if (gameState == GameState.LOBBY) {
            return "ready to scout";
        }

        return "idle";
    }

    private boolean hasPreparedMatches() {
        return !preparedMatches.isEmpty();
    }

    private boolean hasPreparedMatchCapacity() {
        return preparedMatches.size() < PREPARED_MATCH_CACHE_SIZE;
    }

    private MatchPreparation consumePreparedMatch() {
        if (preparedMatches.isEmpty()) {
            return null;
        }

        MatchPreparation preparation = selectBestPreparedMatch();
        preparedMatches.remove(preparation);
        persistPreparedMatches();
        return preparation;
    }

    private void announcePreparedMatchStatus() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        Bukkit.broadcastMessage(
            "[HuntCore] Prescouted worlds ready: " + preparedMatches.size() + "/" + PREPARED_MATCH_CACHE_SIZE + "."
        );
    }

    private List<String> getDiscouragedPoiTypes() {
        List<String> discouragedTypes = new ArrayList<>();
        for (MatchPreparation preparedMatch : preparedMatches) {
            discouragedTypes.add(preparedMatch.structureHint().displayName());
        }
        discouragedTypes.addAll(recentPoiHistory);
        return discouragedTypes;
    }

    private void rememberRecentPoiType(String poiType) {
        if (poiType == null || poiType.isBlank()) {
            return;
        }

        recentPoiHistory.addFirst(poiType);
        while (recentPoiHistory.size() > RECENT_POI_HISTORY_LIMIT) {
            recentPoiHistory.removeLast();
        }
    }

    private MatchPreparation selectBestPreparedMatch() {
        if (preparedMatches.isEmpty()) {
            return null;
        }

        List<String> discouragedPoiTypes = new ArrayList<>(recentPoiHistory);
        MatchPreparation bestPreparation = null;
        double bestScore = Double.MAX_VALUE;
        for (MatchPreparation preparedMatch : preparedMatches) {
            double score = structureHintService.scorePreparedStructureHint(preparedMatch.structureHint(), discouragedPoiTypes);
            if (score < bestScore) {
                bestScore = score;
                bestPreparation = preparedMatch;
            }
        }

        return bestPreparation == null ? preparedMatches.get(0) : bestPreparation;
    }

    private void recordMatchStats(MatchContext finishedMatch, String winnerText, String reason, long durationMillis) {
        try {
            StructureHint structureHint = finishedMatch.getStructureHint();
            matchStatsStore.append(
                new MatchStatsStore.MatchStatSnapshot(
                    System.currentTimeMillis(),
                    durationMillis,
                    winnerText,
                    reason,
                    resolvePlayerName(finishedMatch.getRunnerId()),
                    finishedMatch.getHunterIds().size(),
                    structureHint == null ? "none" : structureHint.displayName(),
                    structureHint == null ? 0 : structureHint.approximateDistanceBlocks(),
                    finishedMatch.getMatchWorldSet().getBaseName(),
                    buildRecordedKillMap(finishedMatch)
                )
            );
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Failed to record match stats: " + exception.getMessage());
        }
    }

    private String resolvePlayerName(UUID playerId) {
        Player onlinePlayer = Bukkit.getPlayer(playerId);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
        return offlinePlayer.getName() == null ? playerId.toString() : offlinePlayer.getName();
    }

    private String formatState(GameState state) {
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private java.util.Map<String, Integer> buildRecordedKillMap(MatchContext finishedMatch) {
        java.util.Map<String, Integer> kills = new java.util.LinkedHashMap<>();

        String runnerName = resolvePlayerName(finishedMatch.getRunnerId());
        kills.put(runnerName, finishedMatch.getKillCount(finishedMatch.getRunnerId()));

        for (UUID hunterId : finishedMatch.getHunterIds()) {
            kills.put(resolvePlayerName(hunterId), finishedMatch.getKillCount(hunterId));
        }

        return kills;
    }

    private String formatRecordedKills(java.util.Map<String, Integer> playerKills) {
        if (playerKills == null || playerKills.isEmpty()) {
            return "none";
        }

        List<String> parts = new ArrayList<>();
        for (var entry : playerKills.entrySet()) {
            parts.add(entry.getKey() + " " + entry.getValue());
        }
        return String.join(", ", parts);
    }

    private void persistPreparedMatches() {
        if (preparedMatches.isEmpty()) {
            preparedMatchStore.delete();
            return;
        }

        try {
            for (MatchPreparation preparedMatch : preparedMatches) {
                matchWorldService.saveWorldSet(preparedMatch.matchWorldSet());
            }

            List<PreparedMatchStore.PreparedMatchSnapshot> snapshots = new ArrayList<>();
            for (MatchPreparation preparedMatch : preparedMatches) {
                snapshots.add(
                    new PreparedMatchStore.PreparedMatchSnapshot(
                        preparedMatch.matchWorldSet(),
                        toStoredLocation(preparedMatch.matchSpawn()),
                        PreparedMatchStore.StructureHintSnapshot.fromStructureHint(
                            preparedMatch.structureHint(),
                            toStoredLocation(preparedMatch.structureHint().location())
                        )
                    )
                );
            }

            preparedMatchStore.save(snapshots);
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Failed to save cached prepared matches: " + exception.getMessage());
        }
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

    private List<Player> getActiveOnlineParticipants() {
        if (currentMatch == null) {
            return List.of();
        }

        List<Player> participants = new ArrayList<>();
        Player runner = Bukkit.getPlayer(currentMatch.getRunnerId());
        if (runner != null && runner.isOnline()) {
            participants.add(runner);
        }
        participants.addAll(getActiveOnlineHunters());
        return participants;
    }

    private void preparePlayerForMatch(Player player) {
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);

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

    private void prepareHunterForRespawn(Player player) {
        player.setGameMode(GameMode.SURVIVAL);

        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }

        PlayerVitals.applySurvivalMatchNutrition(player);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        compassTracker.giveHunterCompass(player);
        teleportSafetyService.stabilize(player, false, false);
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

        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        if (targetLocation != null) {
            teleportSafetyService.teleport(player, targetLocation, false, false);
            rememberParticipantLocation(player, targetLocation);
        }
    }

    private void prepareReturningHunter(Player player, Location targetLocation) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        compassTracker.giveHunterCompass(player);
        if (targetLocation != null) {
            teleportSafetyService.teleport(player, targetLocation, false, false);
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

    private Location getEndPortalDestination(Player player, Location fromLocation) {
        if (currentMatch == null) {
            return null;
        }

        World fromWorld = fromLocation.getWorld();
        if (fromWorld == null) {
            return null;
        }

        if (fromWorld.getEnvironment() == World.Environment.NORMAL) {
            World end = matchWorldService.getEnd(currentMatch.getMatchWorldSet());
            return getEndPlatformLocation(end, fromLocation);
        }

        if (fromWorld.getEnvironment() == World.Environment.THE_END) {
            lobbyService.clearDragonBossBars(player);
            World overworld = matchWorldService.getOverworld(currentMatch.getMatchWorldSet());
            return overworld == null ? null : overworld.getSpawnLocation();
        }

        return null;
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

    private Location resolveHunterRespawnLocation(UUID hunterId, Location vanillaRespawnLocation) {
        if (isValidMatchLocation(vanillaRespawnLocation)) {
            return vanillaRespawnLocation;
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

    private void resetAllAdvancements(Player player) {
        java.util.Iterator<Advancement> iterator = Bukkit.advancementIterator();
        while (iterator.hasNext()) {
            AdvancementProgress progress = player.getAdvancementProgress(iterator.next());
            for (String criterion : List.copyOf(progress.getAwardedCriteria())) {
                progress.revokeCriteria(criterion);
            }
        }
    }

    private Location resolveScaledPortalDestination(World targetWorld, double targetX, double targetZ) {
        if (targetWorld == null) {
            return null;
        }

        return matchSpawnService.findSafeSpawnNear(targetWorld, targetX, targetZ, 8).orElse(targetWorld.getSpawnLocation());
    }

    private Location getEndPlatformLocation(World endWorld, Location fromLocation) {
        if (endWorld == null) {
            return null;
        }

        ensureEndPlatform(endWorld);
        float yaw = fromLocation == null ? 0.0f : fromLocation.getYaw();
        float pitch = fromLocation == null ? 0.0f : fromLocation.getPitch();
        return new Location(endWorld, END_PLATFORM_CENTER_X + 0.5, END_PLATFORM_Y + 1.0, END_PLATFORM_CENTER_Z + 0.5, yaw, pitch);
    }

    private void ensureEndPlatform(World endWorld) {
        for (int x = END_PLATFORM_CENTER_X - 2; x <= END_PLATFORM_CENTER_X + 2; x++) {
            for (int z = END_PLATFORM_CENTER_Z - 2; z <= END_PLATFORM_CENTER_Z + 2; z++) {
                endWorld.getBlockAt(x, END_PLATFORM_Y, z).setType(Material.OBSIDIAN, false);
                for (int y = END_PLATFORM_Y + 1; y <= END_PLATFORM_Y + 4; y++) {
                    endWorld.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private SpawnSelection selectMatchSpawn(List<Location> spawnCandidates) {
        if (spawnCandidates.isEmpty()) {
            return new SpawnSelection(null, null);
        }

        Location selectedSpawn = spawnCandidates.get(0);
        return new SpawnSelection(selectedSpawn, null);
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
        teleportSafetyService.teleport(player, target, true, true);

        if (notify) {
            player.sendMessage("[HuntCore] You are now spectating. Use /spectate again to leave spectator mode.");
        }
    }

    private void applyPausedParticipantState(Player player) {
        player.setInvulnerable(true);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
        player.sendActionBar(Component.text("Match paused", NamedTextColor.YELLOW));
    }

    private void clearPausedParticipantState(Player player) {
        player.setInvulnerable(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0f);
    }

    private void persistPausedMatch() {
        if (!hasPausedMatch()) {
            return;
        }

        try {
            pausedMatchStore.save(
                new PausedMatchStore.PausedMatchSnapshot(
                    currentMatch.getRunnerId(),
                    currentMatch.getHunterIds(),
                    currentMatch.getMatchWorldSet(),
                    toStoredLocation(currentMatch.getMatchSpawn()),
                    toStoredLocation(currentMatch.getLastKnownLocation(currentMatch.getRunnerId())),
                    buildHunterLocationSnapshot(),
                    currentMatch.getStartedAtMillis(),
                    pausedResumeState,
                    headStartSecondsRemaining
                )
            );
        } catch (java.io.IOException exception) {
            plugin.getLogger().warning("Failed to save paused match snapshot: " + exception.getMessage());
        }
    }

    private void clearPausedMatchPersistence() {
        pausedMatchStore.delete();
    }

    private java.util.Map<UUID, PausedMatchStore.StoredLocation> buildHunterLocationSnapshot() {
        java.util.Map<UUID, PausedMatchStore.StoredLocation> locations = new java.util.HashMap<>();
        if (currentMatch == null) {
            return locations;
        }

        for (UUID hunterId : currentMatch.getHunterIds()) {
            PausedMatchStore.StoredLocation location = toStoredLocation(currentMatch.getLastKnownLocation(hunterId));
            if (location != null) {
                locations.put(hunterId, location);
            }
        }

        return locations;
    }

    private PausedMatchStore.StoredLocation toStoredLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return new PausedMatchStore.StoredLocation(
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    private Location toLocation(PausedMatchStore.StoredLocation location) {
        if (location == null || location.worldName().isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(location.worldName());
        if (world == null) {
            return null;
        }

        return new Location(world, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
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
        double radius = 4.0;
        double targetX = baseSpawn.getX() + Math.cos(angle) * radius;
        double targetZ = baseSpawn.getZ() + Math.sin(angle) * radius;
        World world = baseSpawn.getWorld();
        if (world == null) {
            return baseSpawn.clone();
        }

        return matchSpawnService.findSafeSpawnNear(world, targetX, targetZ, 6).orElse(baseSpawn.clone());
    }

    private void cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
    }

    private record SpawnSelection(Location spawn, StructureHint structureHint) {
    }

    private record MatchPreparation(
        MatchWorldSet matchWorldSet,
        Location matchSpawn,
        StructureHint structureHint
    ) {
    }

    private record ChunkCoordinate(int x, int z) {
    }
}
