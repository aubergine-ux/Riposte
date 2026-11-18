package io.github.aubergineux.riposte.ranked;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.MatchTeam;
import io.github.aubergineux.riposte.match.Participant;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.Rating;
import io.github.aubergineux.riposte.storage.DataStore;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** ELO updates, divisions, seasons and cached leaderboards. */
public final class RankedManager {

    public static final String GLOBAL = "global";

    private final RipostePlugin plugin;
    private final Divisions divisions;
    private final Map<String, List<DataStore.LeaderboardEntry>> boards = new ConcurrentHashMap<>();
    private DataStore.Season season;
    private BukkitTask refreshTask;

    public RankedManager(RipostePlugin plugin) {
        this.plugin = plugin;
        this.divisions = new Divisions(plugin);
    }

    public void load() throws SQLException {
        divisions.reload();
        season = plugin.dataStore().currentSeasonSync();
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long period = plugin.settings().leaderboardRefreshSeconds * 20L;
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboards, 40L, period);
    }

    public void reloadDivisions() {
        divisions.reload();
    }

    public DataStore.Season season() {
        return season;
    }

    public Divisions divisions() {
        return divisions;
    }

    public Division division(int elo) {
        return divisions.of(elo);
    }

    public EloCalculator calculator() {
        var s = plugin.settings();
        return new EloCalculator(s.kFactor, s.provisionalKFactor, s.provisionalGames, s.eloFloor);
    }

    /** Average rating across all ranked kits, counting unplayed kits as the starting rating. */
    public int globalElo(PlayerProfile profile) {
        List<Kit> kits = plugin.kits().ranked();
        if (kits.isEmpty()) {
            return plugin.settings().startingElo;
        }
        long total = 0;
        for (Kit kit : kits) {
            Rating rating = profile.allRatings().get(kit.id());
            total += rating == null || rating.games() == 0 ? plugin.settings().startingElo : rating.elo;
        }
        return (int) Math.round((double) total / kits.size());
    }

    public void applyResult(Match match, MatchTeam winnerTeam) {
        Participant winner = winnerTeam.leader();
        Participant loser = match.opponentOf(winner.uuid());
        if (loser == null) {
            return;
        }
        PlayerProfile wp = plugin.profiles().get(winner.uuid());
        PlayerProfile lp = plugin.profiles().get(loser.uuid());
        if (wp == null || lp == null) {
            return;
        }
        String kit = match.kit().id();
        int start = plugin.settings().startingElo;
        Rating wr = wp.rating(kit, start);
        Rating lr = lp.rating(kit, start);
        EloCalculator.Outcome outcome = calculator().calculate(wr.elo, wr.games(), lr.elo, lr.games());
        winner.eloBefore = wr.elo;
        loser.eloBefore = lr.elo;
        Division wBefore = division(wr.elo);
        Division lBefore = division(lr.elo);
        wr.elo = outcome.winnerNew();
        wr.peak = Math.max(wr.peak, wr.elo);
        wr.wins++;
        lr.elo = outcome.loserNew();
        lr.losses++;
        winner.eloAfter = wr.elo;
        loser.eloAfter = lr.elo;
        plugin.dataStore().saveRating(winner.uuid(), kit, season.id(), wr, winner.eloBefore, loser.name(), true);
        plugin.dataStore().saveRating(loser.uuid(), kit, season.id(), lr, loser.eloBefore, winner.name(), false);
        notify(winner.player(), match, outcome.winnerDelta(), wr.elo, wBefore);
        notify(loser.player(), match, outcome.loserDelta(), lr.elo, lBefore);
    }

    private void notify(Player player, Match match, int delta, int elo, Division before) {
        if (player == null) {
            return;
        }
        Division after = division(elo);
        plugin.messages().send(player, delta >= 0 ? "ranked.elo-gain" : "ranked.elo-loss", Text.p("delta", Math.abs(delta)),
                Text.p("elo", elo), Text.mm("kit", match.kit().displayName()), Text.mm("division", after.formatted()));
        if (after.minElo() > before.minElo()) {
            plugin.messages().send(player, "ranked.promoted", Text.mm("division", after.formatted()));
            plugin.messages().title(player, "titles.promoted", Text.mm("division", after.formatted()));
        } else if (after.minElo() < before.minElo()) {
            plugin.messages().send(player, "ranked.demoted", Text.mm("division", after.formatted()));
        }
    }

    // ------------------------------------------------------------------ leaderboards

    public List<DataStore.LeaderboardEntry> board(String key) {
        return boards.getOrDefault(key, List.of());
    }

    public void refreshLeaderboards() {
        int size = plugin.settings().leaderboardSize;
        List<CompletableFuture<?>> futures = new ArrayList<>();
        int seasonId = season.id();
        for (Kit kit : plugin.kits().ranked()) {
            futures.add(plugin.dataStore().leaderboard(kit.id(), seasonId, size).thenAccept(list -> boards.put(kit.id(), list)));
        }
        futures.add(plugin.dataStore().globalLeaderboard(seasonId, plugin.kits().ranked().size(), plugin.settings().startingElo, size)
                .thenAccept(list -> boards.put(GLOBAL, list)));
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> plugin.holograms().update()));
    }

    // ------------------------------------------------------------------ seasons

    public void startSeason(String name, boolean soft, Runnable done) {
        double keep = soft ? plugin.settings().softResetFactor : 0;
        plugin.dataStore().startSeason(season, name, keep, plugin.settings().startingElo).thenAccept(next ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    season = next;
                    int start = plugin.settings().startingElo;
                    for (PlayerProfile profile : plugin.profiles().all()) {
                        Map<String, Rating> ratings = profile.allRatings();
                        List<Map.Entry<String, Rating>> old = new ArrayList<>(ratings.entrySet());
                        ratings.clear();
                        if (keep > 0) {
                            for (Map.Entry<String, Rating> e : old) {
                                int carried = EloCalculator.softReset(e.getValue().elo, start, keep);
                                ratings.put(e.getKey(), new Rating(carried));
                            }
                        }
                    }
                    boards.clear();
                    refreshLeaderboards();
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        plugin.messages().send(player, "ranked.season-started", Text.p("season", next.name()));
                    }
                    done.run();
                }));
    }
}
