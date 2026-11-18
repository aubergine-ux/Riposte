package io.github.aubergineux.riposte;

import io.github.aubergineux.riposte.arena.ArenaListener;
import io.github.aubergineux.riposte.arena.ArenaManager;
import io.github.aubergineux.riposte.command.CommandRegistrar;
import io.github.aubergineux.riposte.config.Messages;
import io.github.aubergineux.riposte.config.Settings;
import io.github.aubergineux.riposte.duel.DuelManager;
import io.github.aubergineux.riposte.event.EventManager;
import io.github.aubergineux.riposte.kit.KitEditor;
import io.github.aubergineux.riposte.kit.KitManager;
import io.github.aubergineux.riposte.lobby.LobbyListener;
import io.github.aubergineux.riposte.lobby.LobbyManager;
import io.github.aubergineux.riposte.match.MatchListener;
import io.github.aubergineux.riposte.match.MatchManager;
import io.github.aubergineux.riposte.menu.MenuConfig;
import io.github.aubergineux.riposte.menu.MenuListener;
import io.github.aubergineux.riposte.party.PartyManager;
import io.github.aubergineux.riposte.profile.ProfileManager;
import io.github.aubergineux.riposte.queue.QueueManager;
import io.github.aubergineux.riposte.ranked.HologramManager;
import io.github.aubergineux.riposte.ranked.RankedManager;
import io.github.aubergineux.riposte.scoreboard.ScoreboardManager;
import io.github.aubergineux.riposte.spectate.SpectatorManager;
import io.github.aubergineux.riposte.storage.DataStore;
import io.github.aubergineux.riposte.storage.Database;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class RipostePlugin extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private MenuConfig menus;
    private Database database;
    private DataStore dataStore;
    private KitManager kits;
    private KitEditor kitEditor;
    private ArenaManager arenas;
    private ArenaListener arenaListener;
    private ProfileManager profiles;
    private LobbyManager lobby;
    private LobbyListener lobbyListener;
    private MatchManager matches;
    private QueueManager queues;
    private DuelManager duels;
    private PartyManager parties;
    private EventManager events;
    private SpectatorManager spectators;
    private RankedManager ranked;
    private HologramManager holograms;
    private ScoreboardManager scoreboards;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        saveDefaultConfig();
        settings = new Settings(getConfig());
        messages = new Messages(this);
        messages.reload();
        menus = new MenuConfig(this, messages);
        menus.reload();

        try {
            database = new Database(settings, getDataFolder(), getLogger());
            dataStore = new DataStore(database);
            dataStore.createSchemaSync();
            ranked = new RankedManager(this);
            ranked.load();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Could not connect to the database. Check the storage section of config.yml.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        kits = new KitManager(this);
        kits.load();
        kitEditor = new KitEditor(this);
        profiles = new ProfileManager(this);
        lobby = new LobbyManager(this);
        lobby.load();
        arenas = new ArenaManager(this);
        arenas.enable();
        matches = new MatchManager(this);
        queues = new QueueManager(this);
        duels = new DuelManager(this);
        parties = new PartyManager(this);
        events = new EventManager(this);
        spectators = new SpectatorManager(this);
        holograms = new HologramManager(this);
        scoreboards = new ScoreboardManager(this);
        scoreboards.reload();

        PluginManager pm = getServer().getPluginManager();
        arenaListener = new ArenaListener(this);
        lobbyListener = new LobbyListener(this);
        pm.registerEvents(profiles, this);
        pm.registerEvents(new MenuListener(this), this);
        pm.registerEvents(new MatchListener(this), this);
        pm.registerEvents(arenaListener, this);
        pm.registerEvents(lobbyListener, this);
        pm.registerEvents(spectators, this);
        pm.registerEvents(duels, this);
        pm.registerEvents(parties, this);
        pm.registerEvents(events, this);
        pm.registerEvents(holograms, this);
        pm.registerEvents(scoreboards, this);
        pm.registerEvents(new Listener() {
            @EventHandler
            public void onQuit(PlayerQuitEvent event) {
                queues.removeOffline(event.getPlayer().getUniqueId());
            }
        }, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> new CommandRegistrar(this).register(event.registrar()));

        holograms.load();
        profiles.loadOnline();
        getLogger().info("Riposte enabled in " + (System.currentTimeMillis() - start) + " ms ("
                + kits.all().size() + " kits, " + arenas.all().size() + " arenas, " + database.dialect().name().toLowerCase() + " storage)");
    }

    @Override
    public void onDisable() {
        if (matches != null) {
            matches.endAll();
        }
        if (holograms != null) {
            holograms.removeAll();
        }
        if (arenas != null) {
            arenas.disable();
        }
        if (scoreboards != null) {
            scoreboards.disable();
        }
        if (database != null) {
            database.close();
        }
    }

    /** Reloads configuration, messages, menus, kits, divisions and holograms. Arenas are edited live with /arena. */
    public void reload() {
        reloadConfig();
        settings = new Settings(getConfig());
        messages.reload();
        menus.reload();
        scoreboards.reload();
        kits.load();
        ranked.reloadDivisions();
        lobby.load();
        holograms.load();
    }

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public MenuConfig menus() {
        return menus;
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public KitManager kits() {
        return kits;
    }

    public KitEditor kitEditor() {
        return kitEditor;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public ArenaListener arenaListener() {
        return arenaListener;
    }

    public ProfileManager profiles() {
        return profiles;
    }

    public LobbyManager lobby() {
        return lobby;
    }

    public LobbyListener lobbyListener() {
        return lobbyListener;
    }

    public MatchManager matches() {
        return matches;
    }

    public QueueManager queues() {
        return queues;
    }

    public DuelManager duels() {
        return duels;
    }

    public PartyManager parties() {
        return parties;
    }

    public EventManager events() {
        return events;
    }

    public SpectatorManager spectators() {
        return spectators;
    }

    public RankedManager ranked() {
        return ranked;
    }

    public HologramManager holograms() {
        return holograms;
    }

    public ScoreboardManager scoreboards() {
        return scoreboards;
    }
}
