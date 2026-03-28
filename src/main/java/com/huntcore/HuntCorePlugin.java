package com.huntcore;

import com.huntcore.command.HunterKeepInventoryCommand;
import com.huntcore.command.InstallLobbyMapCommand;
import com.huntcore.command.ReadyCommand;
import com.huntcore.command.RoleCommand;
import com.huntcore.command.SetLobbyCommand;
import com.huntcore.command.SpectateCommand;
import com.huntcore.config.PluginConfig;
import com.huntcore.game.GameManager;
import com.huntcore.game.LobbyService;
import com.huntcore.game.MatchCountdown;
import com.huntcore.game.PlayerRegistry;
import com.huntcore.game.PlayerRole;
import com.huntcore.listener.HunterCompassListener;
import com.huntcore.listener.MatchEventListener;
import com.huntcore.listener.MatchPortalListener;
import com.huntcore.listener.PlayerConnectionListener;
import com.huntcore.tracking.CompassTracker;
import com.huntcore.tracking.PortalTrackingService;
import com.huntcore.world.LobbyMapInstaller;
import com.huntcore.world.MatchSpawnService;
import com.huntcore.world.MatchWorldService;
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
        this.gameManager = new GameManager(
            this,
            pluginConfig,
            playerRegistry,
            lobbyService,
            matchSpawnService,
            matchWorldService,
            structureHintService,
            matchCountdown,
            compassTracker
        );

        registerCommands();
        registerListeners();

        // TODO HuntCore: introduce a reusable minigame registry when more game modes are added.
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerRegistry.registerPlayer(player);
            Bukkit.getScheduler().runTask(this, () -> lobbyService.sendToLobby(player, true));
        }
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.shutdown();
        }
    }

    private void registerCommands() {
        registerCommand("runner", new RoleCommand(playerRegistry, gameManager, PlayerRole.RUNNER));
        registerCommand("hunter", new RoleCommand(playerRegistry, gameManager, PlayerRole.HUNTER));
        registerCommand("spectate", new SpectateCommand(gameManager));
        registerCommand("ready", new ReadyCommand(playerRegistry, gameManager, true));
        registerCommand("unready", new ReadyCommand(playerRegistry, gameManager, false));
        registerCommand("hunterkeepinventory", new HunterKeepInventoryCommand(pluginConfig));
        registerCommand("setlobby", new SetLobbyCommand(pluginConfig));
        registerCommand("installlobbymap", new InstallLobbyMapCommand(lobbyMapInstaller));
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(
            new PlayerConnectionListener(this, playerRegistry, lobbyService, gameManager),
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
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            throw new IllegalStateException("Command " + name + " is missing from plugin.yml");
        }

        command.setExecutor(executor);
    }
}
