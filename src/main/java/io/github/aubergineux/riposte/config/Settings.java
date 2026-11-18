package io.github.aubergineux.riposte.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed view over config.yml. Re-created on every reload so values are always consistent. */
public final class Settings {

    // storage
    public final String storageType;
    public final String sqliteFile;
    public final String mysqlHost;
    public final int mysqlPort;
    public final String mysqlDatabase;
    public final String mysqlUser;
    public final String mysqlPassword;
    public final int mysqlPoolSize;
    public final String mysqlParams;
    public final String tablePrefix;

    // match
    public final int countdownSeconds;
    public final int endDelaySeconds;
    public final int maxMatchMinutes;
    public final int rematchSeconds;
    public final int voidDepth;
    public final int snapshotMinutes;
    public final boolean comboActionBar;
    public final int comboActionBarMin;

    // queue
    public final int rankedRangeStart;
    public final int rankedRangeGrowth;
    public final int rankedRangeIntervalSeconds;
    public final int rankedRangeMax;
    public final int rankedRequiredUnrankedWins;
    public final int maxMatchStartsPerTick;

    // ranked
    public final int startingElo;
    public final int kFactor;
    public final int provisionalKFactor;
    public final int provisionalGames;
    public final int eloFloor;
    public final double softResetFactor;
    public final int leaderboardRefreshSeconds;
    public final int leaderboardSize;

    // duels
    public final int duelExpireSeconds;
    public final int duelCooldownSeconds;

    // party
    public final int partyMaxSize;
    public final int partyInviteSeconds;

    // events
    public final int eventJoinSeconds;
    public final int eventMinPlayers;
    public final int eventMaxPlayers;

    // arena
    public final String arenaWorld;
    public final String templateWorld;
    public final int arenaSpacing;
    public final int pasteBlocksPerTick;
    public final int resetBlocksPerTick;
    public final boolean keepArenasLoaded;
    public final int arenaMillisPerTick;

    // lobby
    public final boolean lobbyOnJoin;
    public final boolean lobbyVoidTeleport;
    public final boolean lobbyHidePlayers;
    public final boolean lobbyFlight;

    // scoreboard
    public final int scoreboardInterval;

    public Settings(FileConfiguration c) {
        storageType = c.getString("storage.type", "sqlite").toLowerCase(java.util.Locale.ROOT);
        sqliteFile = c.getString("storage.sqlite.file", "riposte.db");
        mysqlHost = c.getString("storage.mysql.host", "localhost");
        mysqlPort = c.getInt("storage.mysql.port", 3306);
        mysqlDatabase = c.getString("storage.mysql.database", "riposte");
        mysqlUser = c.getString("storage.mysql.username", "root");
        mysqlPassword = c.getString("storage.mysql.password", "");
        mysqlPoolSize = c.getInt("storage.mysql.pool-size", 6);
        mysqlParams = c.getString("storage.mysql.parameters", "useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        tablePrefix = c.getString("storage.table-prefix", "riposte_");

        countdownSeconds = c.getInt("match.countdown-seconds", 5);
        endDelaySeconds = c.getInt("match.end-delay-seconds", 4);
        maxMatchMinutes = c.getInt("match.max-duration-minutes", 15);
        rematchSeconds = c.getInt("match.rematch-seconds", 30);
        voidDepth = c.getInt("match.void-depth", 6);
        snapshotMinutes = c.getInt("match.inventory-preview-minutes", 10);
        comboActionBar = c.getBoolean("match.combo-action-bar.enabled", true);
        comboActionBarMin = c.getInt("match.combo-action-bar.minimum", 3);

        rankedRangeStart = c.getInt("queue.ranked-range.start", 50);
        rankedRangeGrowth = c.getInt("queue.ranked-range.growth", 25);
        rankedRangeIntervalSeconds = Math.max(1, c.getInt("queue.ranked-range.interval-seconds", 5));
        rankedRangeMax = c.getInt("queue.ranked-range.max", 500);
        rankedRequiredUnrankedWins = c.getInt("queue.ranked-required-unranked-wins", 0);
        maxMatchStartsPerTick = Math.max(1, c.getInt("queue.max-match-starts-per-tick", 4));

        startingElo = c.getInt("ranked.starting-elo", 1000);
        kFactor = c.getInt("ranked.k-factor", 32);
        provisionalKFactor = c.getInt("ranked.provisional-k-factor", 48);
        provisionalGames = c.getInt("ranked.provisional-games", 10);
        eloFloor = c.getInt("ranked.elo-floor", 100);
        softResetFactor = c.getDouble("ranked.season-soft-reset-factor", 0.5);
        leaderboardRefreshSeconds = Math.max(10, c.getInt("ranked.leaderboard-refresh-seconds", 60));
        leaderboardSize = Math.max(1, Math.min(45, c.getInt("ranked.leaderboard-size", 10)));

        duelExpireSeconds = c.getInt("duels.request-expire-seconds", 30);
        duelCooldownSeconds = c.getInt("duels.request-cooldown-seconds", 3);

        partyMaxSize = c.getInt("party.max-size", 8);
        partyInviteSeconds = c.getInt("party.invite-expire-seconds", 60);

        eventJoinSeconds = c.getInt("events.join-seconds", 60);
        eventMinPlayers = Math.max(2, c.getInt("events.min-players", 4));
        eventMaxPlayers = c.getInt("events.max-players", 64);

        arenaWorld = c.getString("arenas.world", "riposte_arenas");
        templateWorld = c.getString("arenas.template-world", "riposte_templates");
        arenaSpacing = Math.max(64, c.getInt("arenas.copy-spacing", 512));
        pasteBlocksPerTick = Math.max(1000, c.getInt("arenas.paste-blocks-per-tick", 15000));
        resetBlocksPerTick = Math.max(500, c.getInt("arenas.reset-blocks-per-tick", 20000));
        keepArenasLoaded = c.getBoolean("arenas.keep-loaded", true);
        arenaMillisPerTick = Math.max(1, c.getInt("arenas.max-millis-per-tick", 10));

        lobbyOnJoin = c.getBoolean("lobby.teleport-on-join", true);
        lobbyVoidTeleport = c.getBoolean("lobby.void-teleport", true);
        lobbyHidePlayers = c.getBoolean("lobby.hide-players", false);
        lobbyFlight = c.getBoolean("lobby.allow-flight", false);

        scoreboardInterval = Math.max(2, c.getInt("scoreboard.update-ticks", 10));
    }

    public static ConfigurationSection section(FileConfiguration c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        return s == null ? c.createSection(path) : s;
    }
}
