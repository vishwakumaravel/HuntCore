package com.huntcore;

import com.huntcore.command.HunterKeepInventoryCommand;
import com.huntcore.command.InstallLobbyMapCommand;
import com.huntcore.command.InstallPvpMapCommand;
import com.huntcore.command.PauseCommand;
import com.huntcore.command.PvpCommand;
import com.huntcore.command.PvpLeaveCommand;
import com.huntcore.command.ReadyCommand;
import com.huntcore.command.ResetCommand;
import com.huntcore.command.RoleCommand;
import com.huntcore.command.SetLobbyCommand;
import com.huntcore.command.SetPvpSpawnCommand;
import com.huntcore.command.SpectateCommand;
import com.huntcore.command.UnpauseCommand;
import com.huntcore.config.PluginConfig;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import com.huntcore.game.MatchCountdown;
import com.huntcore.game.PausedMatchStore;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import com.huntcore.listener.HunterCompassListener;
import com.huntcore.listener.MatchEventListener;
import com.huntcore.listener.MatchPortalListener;
import com.huntcore.listener.PlayerConnectionListener;
import com.huntcore.listener.PvpEventListener;
import com.huntcore.pvp.PvpArenaManager;
import com.huntcore.tracking.CompassTracker;
import com.huntcore.tracking.PortalTrackingService;
import com.huntcore.world.LobbyMapInstaller;
import com.huntcore.world.MatchSpawnService;
import com.huntcore.world.MatchWorldService;
import com.huntcore.world.PvpMapInstaller;
import com.huntcore.world.StructureHintService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class HuntCorePlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private PlayerRegistry playerRegistry;
    private LobbyService lobbyService;
    private MatchSpawnService matchSpawnService;
    private MatchWorldService matchWorldService;
    private StructureHintService structureHintService;
    private MatchCountdown matchCountdown;
    private PortalTrackingService portalTrackingService;
    private CompassTracker compassTracker;
    private GameManager gameManager;
    private LobbyMapInstaller lobbyMapInstaller;
    private PvpMapInstaller pvpMapInstaller;
    private PvpArenaManager pvpArenaManager;
    private PausedMatchStore pausedMatchStore;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.pluginConfig = new PluginConfig(this);
        this.playerRegistry = new PlayerRegistry();
        this.lobbyService = new LobbyService(pluginConfig);
        this.matchSpawnService = new MatchSpawnService(pluginConfig);
        this.matchWorldService = new MatchWorldService(this, pluginConfig);
        this.structureHintService = new StructureHintService(pluginConfig);
        this.matchCountdown = new MatchCountdown(this);
        this.portalTrackingService = new PortalTrackingService();
        this.compassTracker = new CompassTracker(this, pluginConfig, portalTrackingService);
        this.lobbyMapInstaller = new LobbyMapInstaller(this, pluginConfig);
        this.pvpMapInstaller = new PvpMapInstaller(this, pluginConfig);
        this.pausedMatchStore = new PausedMatchStore(this);
        this.gameManager = new GameManager(
            this,
            pluginConfig,
            playerRegistry,
            lobbyService,
            matchSpawnService,
            matchWorldService,
            structureHintService,
            matchCountdown,
            compassTracker,
            pausedMatchStore
        );
        this.pvpArenaManager = new PvpArenaManager(pluginConfig, playerRegistry, lobbyService, gameManager);

        lobbyMapInstaller.ensureConfiguredLobbyWorldLoaded();
        pvpMapInstaller.ensureConfiguredPvpWorldLoaded();
        gameManager.restorePausedMatchIfPresent();

        registerCommands();
        registerListeners();

        // TODO HuntCore: introduce a reusable minigame registry when more game modes are added.
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerRegistry.registerPlayer(player);
            Bukkit.getScheduler().runTask(this, () -> {
                if (pvpArenaManager.handlePlayerJoin(player)) {
                    return;
                }

                if (gameManager.handlePlayerJoin(player)) {
                    return;
                }

                lobbyService.sendToLobby(player, true);
            });
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
        if (pvpArenaManager != null) {
            pvpArenaManager.shutdown();
        }
    }

    private void registerCommands() {
        registerCommand("runner", new RoleCommand(playerRegistry, gameManager, pvpArenaManager, PlayerRole.RUNNER));
        registerCommand("hunter", new RoleCommand(playerRegistry, gameManager, pvpArenaManager, PlayerRole.HUNTER));
        registerCommand("spectate", new SpectateCommand(gameManager, pvpArenaManager));
        registerCommand("ready", new ReadyCommand(playerRegistry, gameManager, pvpArenaManager, true));
        registerCommand("unready", new ReadyCommand(playerRegistry, gameManager, pvpArenaManager, false));
        registerCommand("hunterkeepinventory", new HunterKeepInventoryCommand(pluginConfig));
        registerCommand("setlobby", new SetLobbyCommand(pluginConfig));
        registerCommand("setpvpspawn", new SetPvpSpawnCommand(pluginConfig));
        registerCommand("installlobbymap", new InstallLobbyMapCommand(lobbyMapInstaller));
        registerCommand("installpvpmap", new InstallPvpMapCommand(pvpMapInstaller));
        registerCommand("reset", new ResetCommand(gameManager, pvpArenaManager));
        registerCommand("pvp", new PvpCommand(pvpArenaManager));
        registerCommand("pvpleave", new PvpLeaveCommand(pvpArenaManager));
        registerCommand("pause", new PauseCommand(gameManager));
        registerCommand("unpause", new UnpauseCommand(gameManager));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(
            new PlayerConnectionListener(this, playerRegistry, lobbyService, gameManager, pvpArenaManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new MatchEventListener(this, gameManager),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new MatchPortalListener(gameManager, portalTrackingService),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new HunterCompassListener(gameManager, compassTracker),
            this
        );
        Bukkit.getPluginManager().registerEvents(
            new PvpEventListener(this, pvpArenaManager),
            this
        );
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command " + name + " is missing from plugin.yml");
        }

        command.setExecutor(executor);
    }
}
