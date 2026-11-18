package io.github.aubergineux.riposte.storage;

import io.github.aubergineux.riposte.profile.KitStats;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerSettings;
import io.github.aubergineux.riposte.profile.Rating;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** All SQL lives here. Methods ending in Sync block; everything else is async. */
public final class DataStore {

    public record EloChange(int before, int after, boolean won, String opponent, long time) {
    }

    public record LeaderboardEntry(UUID uuid, String name, int elo, int wins, int losses) {
    }

    public record Season(int id, String name, long startedAt) {
    }

    private final Database db;
    private final String players;
    private final String kitStats;
    private final String ratings;
    private final String history;
    private final String layouts;
    private final String seasons;

    public DataStore(Database db) {
        this.db = db;
        this.players = db.table("players");
        this.kitStats = db.table("kit_stats");
        this.ratings = db.table("ratings");
        this.history = db.table("elo_history");
        this.layouts = db.table("kit_layouts");
        this.seasons = db.table("seasons");
    }

    public Database database() {
        return db;
    }

    public void createSchemaSync() throws SQLException {
        String id = db.dialect().autoIncrementKey();
        db.sync(c -> {
            try (Statement st = c.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + players + " ("
                        + "uuid VARCHAR(36) NOT NULL PRIMARY KEY, "
                        + "name VARCHAR(16) NOT NULL, "
                        + "first_join BIGINT NOT NULL, "
                        + "last_join BIGINT NOT NULL, "
                        + "settings INT NOT NULL DEFAULT 7, "
                        + "time_of_day VARCHAR(16) NOT NULL DEFAULT 'DAY')");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + kitStats + " ("
                        + "uuid VARCHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL, "
                        + "wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, "
                        + "kills INT NOT NULL DEFAULT 0, deaths INT NOT NULL DEFAULT 0, "
                        + "streak INT NOT NULL DEFAULT 0, best_streak INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, kit))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + ratings + " ("
                        + "uuid VARCHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL, season INT NOT NULL, "
                        + "elo INT NOT NULL, peak INT NOT NULL, "
                        + "wins INT NOT NULL DEFAULT 0, losses INT NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, kit, season))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + history + " ("
                        + "id " + id + ", "
                        + "uuid VARCHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL, season INT NOT NULL, "
                        + "elo_before INT NOT NULL, elo_after INT NOT NULL, "
                        + "opponent VARCHAR(16) NOT NULL, won BOOLEAN NOT NULL, created_at BIGINT NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + layouts + " ("
                        + "uuid VARCHAR(36) NOT NULL, kit VARCHAR(32) NOT NULL, layout VARCHAR(255) NOT NULL, "
                        + "PRIMARY KEY (uuid, kit))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS " + seasons + " ("
                        + "id INT NOT NULL PRIMARY KEY, name VARCHAR(64) NOT NULL, "
                        + "started_at BIGINT NOT NULL, ended_at BIGINT)");
                indexIfMissing(st, "idx_" + ratings + "_board", ratings, "kit, season, elo");
                indexIfMissing(st, "idx_" + history + "_player", history, "uuid, kit, season, id");
                indexIfMissing(st, "idx_" + players + "_name", players, "name");
            }
            return null;
        });
    }

    private void indexIfMissing(Statement st, String name, String table, String columns) throws SQLException {
        if (db.dialect() == Database.Dialect.SQLITE) {
            st.executeUpdate("CREATE INDEX IF NOT EXISTS " + name + " ON " + table + " (" + columns + ")");
            return;
        }
        try {
            st.executeUpdate("CREATE INDEX " + name + " ON " + table + " (" + columns + ")");
        } catch (SQLException e) {
            // MySQL has no IF NOT EXISTS for indexes; error 1061 means it already exists
            if (e.getErrorCode() != 1061) {
                throw e;
            }
        }
    }

    // ---------------------------------------------------------------- seasons

    public Season currentSeasonSync() throws SQLException {
        return db.sync(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, name, started_at FROM " + seasons + " WHERE ended_at IS NULL ORDER BY id DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Season(rs.getInt(1), rs.getString(2), rs.getLong(3));
                }
            }
            Season first = new Season(1, "Season 1", System.currentTimeMillis());
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + seasons + " (id, name, started_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, first.id());
                ps.setString(2, first.name());
                ps.setLong(3, first.startedAt());
                ps.executeUpdate();
            }
            return first;
        });
    }

    /** Closes the current season and opens a new one. Ratings carry over compressed toward the start value. */
    public CompletableFuture<Season> startSeason(Season current, String name, double carryFactor, int startingElo) {
        return db.async(c -> {
            long now = System.currentTimeMillis();
            Season next = new Season(current.id() + 1, name, now);
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + seasons + " SET ended_at = ? WHERE id = ?")) {
                    ps.setLong(1, now);
                    ps.setInt(2, current.id());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + seasons + " (id, name, started_at) VALUES (?, ?, ?)")) {
                    ps.setInt(1, next.id());
                    ps.setString(2, name);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
                if (carryFactor > 0) {
                    String carried = "CAST(? + (elo - ?) * ? AS INT)";
                    if (db.dialect() == Database.Dialect.MYSQL) {
                        carried = "CAST(? + (elo - ?) * ? AS SIGNED)";
                    }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + ratings
                            + " (uuid, kit, season, elo, peak, wins, losses) SELECT uuid, kit, ?, " + carried + ", " + carried
                            + ", 0, 0 FROM " + ratings + " WHERE season = ?")) {
                        ps.setInt(1, next.id());
                        for (int i = 0; i < 2; i++) {
                            ps.setInt(2 + i * 3, startingElo);
                            ps.setInt(3 + i * 3, startingElo);
                            ps.setDouble(4 + i * 3, carryFactor);
                        }
                        ps.setInt(8, current.id());
                        ps.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            return next;
        });
    }

    // ---------------------------------------------------------------- profiles

    public PlayerProfile loadProfileSync(UUID uuid, String name, int season) throws SQLException {
        PlayerProfile profile = new PlayerProfile(uuid, name);
        String id = uuid.toString();
        db.sync(c -> {
            long now = System.currentTimeMillis();
            boolean exists = false;
            try (PreparedStatement ps = c.prepareStatement("SELECT settings, time_of_day FROM " + players + " WHERE uuid = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        exists = true;
                        profile.settings().unpack(rs.getInt(1));
                        profile.settings().time = PlayerSettings.TimeOfDay.parse(rs.getString(2));
                    }
                }
            }
            if (exists) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE " + players + " SET name = ?, last_join = ? WHERE uuid = ?")) {
                    ps.setString(1, name);
                    ps.setLong(2, now);
                    ps.setString(3, id);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + players + " (uuid, name, first_join, last_join, settings, time_of_day) VALUES (?, ?, ?, ?, ?, ?)")) {
                    ps.setString(1, id);
                    ps.setString(2, name);
                    ps.setLong(3, now);
                    ps.setLong(4, now);
                    ps.setInt(5, profile.settings().pack());
                    ps.setString(6, profile.settings().time.name());
                    ps.executeUpdate();
                }
            }
            readStats(c, id, profile);
            readRatings(c, id, season, profile);
            try (PreparedStatement ps = c.prepareStatement("SELECT kit, layout FROM " + layouts + " WHERE uuid = ?")) {
                ps.setString(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int[] layout = parseLayout(rs.getString(2));
                        if (layout != null) {
                            profile.layout(rs.getString(1), layout);
                        }
                    }
                }
            }
            return null;
        });
        return profile;
    }

    /** Read-only lookup of an offline player for /stats. Returns null if they never joined. */
    public CompletableFuture<PlayerProfile> loadOffline(String name, int season) {
        return db.async(c -> {
            PlayerProfile profile = null;
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid, name FROM " + players + " WHERE LOWER(name) = LOWER(?) ORDER BY last_join DESC LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        profile = new PlayerProfile(UUID.fromString(rs.getString(1)), rs.getString(2));
                    }
                }
            }
            if (profile != null) {
                readStats(c, profile.uuid().toString(), profile);
                readRatings(c, profile.uuid().toString(), season, profile);
            }
            return profile;
        });
    }

    private void readStats(Connection c, String id, PlayerProfile profile) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT kit, wins, losses, kills, deaths, streak, best_streak FROM " + kitStats + " WHERE uuid = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    KitStats s = profile.stats(rs.getString(1));
                    s.wins = rs.getInt(2);
                    s.losses = rs.getInt(3);
                    s.kills = rs.getInt(4);
                    s.deaths = rs.getInt(5);
                    s.streak = rs.getInt(6);
                    s.bestStreak = rs.getInt(7);
                }
            }
        }
    }

    private void readRatings(Connection c, String id, int season, PlayerProfile profile) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT kit, elo, peak, wins, losses FROM " + ratings + " WHERE uuid = ? AND season = ?")) {
            ps.setString(1, id);
            ps.setInt(2, season);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Rating r = profile.rating(rs.getString(1), rs.getInt(2));
                    r.elo = rs.getInt(2);
                    r.peak = rs.getInt(3);
                    r.wins = rs.getInt(4);
                    r.losses = rs.getInt(5);
                }
            }
        }
    }

    public CompletableFuture<Void> saveSettings(UUID uuid, PlayerSettings settings) {
        int flags = settings.pack();
        String time = settings.time.name();
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE " + players + " SET settings = ?, time_of_day = ? WHERE uuid = ?")) {
                ps.setInt(1, flags);
                ps.setString(2, time);
                ps.setString(3, uuid.toString());
                return ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> saveStats(UUID uuid, String kit, KitStats stats) {
        int[] values = {stats.wins, stats.losses, stats.kills, stats.deaths, stats.streak, stats.bestStreak};
        String sql = db.dialect().upsert(kitStats, new String[]{"uuid", "kit", "wins", "losses", "kills", "deaths", "streak", "best_streak"}, new String[]{"uuid", "kit"});
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                for (int i = 0; i < values.length; i++) {
                    ps.setInt(3 + i, values[i]);
                }
                return ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> saveRating(UUID uuid, String kit, int season, Rating rating, int eloBefore, String opponent, boolean won) {
        int[] values = {rating.elo, rating.peak, rating.wins, rating.losses};
        String sql = db.dialect().upsert(ratings, new String[]{"uuid", "kit", "season", "elo", "peak", "wins", "losses"}, new String[]{"uuid", "kit", "season"});
        long now = System.currentTimeMillis();
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                ps.setInt(3, season);
                for (int i = 0; i < values.length; i++) {
                    ps.setInt(4 + i, values[i]);
                }
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + history + " (uuid, kit, season, elo_before, elo_after, opponent, won, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                ps.setInt(3, season);
                ps.setInt(4, eloBefore);
                ps.setInt(5, values[0]);
                ps.setString(6, opponent);
                ps.setBoolean(7, won);
                ps.setLong(8, now);
                return ps.executeUpdate();
            }
        });
    }

    public CompletableFuture<Void> saveLayout(UUID uuid, String kit, int[] layout) {
        if (layout == null) {
            return db.run(c -> {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM " + layouts + " WHERE uuid = ? AND kit = ?")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, kit);
                    return ps.executeUpdate();
                }
            });
        }
        String sql = db.dialect().upsert(layouts, new String[]{"uuid", "kit", "layout"}, new String[]{"uuid", "kit"});
        String encoded = encodeLayout(layout);
        return db.run(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                ps.setString(3, encoded);
                return ps.executeUpdate();
            }
        });
    }

    /** Oldest first, so the result can be drawn left to right. */
    public CompletableFuture<List<EloChange>> eloHistory(UUID uuid, String kit, int season, int limit) {
        return db.async(c -> {
            List<EloChange> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT elo_before, elo_after, won, opponent, created_at FROM " + history
                    + " WHERE uuid = ? AND kit = ? AND season = ? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, kit);
                ps.setInt(3, season);
                ps.setInt(4, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.addFirst(new EloChange(rs.getInt(1), rs.getInt(2), rs.getBoolean(3), rs.getString(4), rs.getLong(5)));
                    }
                }
            }
            return out;
        });
    }

    public CompletableFuture<List<LeaderboardEntry>> leaderboard(String kit, int season, int limit) {
        return db.async(c -> {
            List<LeaderboardEntry> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT r.uuid, p.name, r.elo, r.wins, r.losses FROM " + ratings + " r JOIN " + players
                    + " p ON p.uuid = r.uuid WHERE r.kit = ? AND r.season = ? AND (r.wins + r.losses) > 0 ORDER BY r.elo DESC, r.wins DESC LIMIT ?")) {
                ps.setString(1, kit);
                ps.setInt(2, season);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)));
                    }
                }
            }
            return out;
        });
    }

    /**
     * Global rating: the average over every ranked kit, where kits a player never played count as the starting ELO.
     * This stops a single lucky kit from topping the global board.
     */
    public CompletableFuture<List<LeaderboardEntry>> globalLeaderboard(int season, int rankedKits, int startingElo, int limit) {
        return db.async(c -> {
            List<LeaderboardEntry> out = new ArrayList<>();
            String sql = "SELECT r.uuid, p.name, (SUM(r.elo) + (? - COUNT(*)) * ?) / ? AS global_elo, SUM(r.wins), SUM(r.losses) FROM " + ratings + " r JOIN "
                    + players + " p ON p.uuid = r.uuid WHERE r.season = ? AND (r.wins + r.losses) > 0 GROUP BY r.uuid, p.name ORDER BY global_elo DESC LIMIT ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int kits = Math.max(1, rankedKits);
                ps.setInt(1, kits);
                ps.setInt(2, startingElo);
                ps.setInt(3, kits);
                ps.setInt(4, season);
                ps.setInt(5, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), (int) Math.round(rs.getDouble(3)), rs.getInt(4), rs.getInt(5)));
                    }
                }
            }
            return out;
        });
    }

    /** 1-based position of a rating on its kit leaderboard. */
    public CompletableFuture<Integer> position(String kit, int season, int elo) {
        return db.async(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + ratings + " WHERE kit = ? AND season = ? AND elo > ? AND (wins + losses) > 0")) {
                ps.setString(1, kit);
                ps.setInt(2, season);
                ps.setInt(3, elo);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) + 1 : 1;
                }
            }
        });
    }

    public static String encodeLayout(int[] layout) {
        StringBuilder sb = new StringBuilder(layout.length * 3);
        for (int i = 0; i < layout.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(layout[i]);
        }
        return sb.toString();
    }

    public static int[] parseLayout(String s) {
        try {
            String[] parts = s.split(",");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i]);
            }
            return out;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
