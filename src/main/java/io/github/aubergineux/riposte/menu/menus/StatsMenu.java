package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.profile.KitStats;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.Rating;
import io.github.aubergineux.riposte.ranked.Division;
import io.github.aubergineux.riposte.storage.DataStore;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** /stats: totals plus per-kit stats, rating, division and recent ELO history as a sparkline. */
public final class StatsMenu extends Menu {

    private final RipostePlugin plugin;
    private final PlayerProfile target;
    private final Map<String, List<DataStore.EloChange>> history;
    private final List<Kit> kits;

    private StatsMenu(RipostePlugin plugin, Player viewer, PlayerProfile target, Map<String, List<DataStore.EloChange>> history, List<Kit> kits) {
        super(viewer, rowsFor(kits.size()) + 1, plugin.menus().title("stats", Text.p("player", target.name())));
        this.plugin = plugin;
        this.target = target;
        this.history = history;
        this.kits = kits;
    }

    /** Loads the player (online or not) and their ELO history off the main thread, then opens the menu. */
    public static void open(RipostePlugin plugin, Player viewer, String name) {
        Player online = Bukkit.getPlayerExact(name);
        PlayerProfile loaded = online == null ? null : plugin.profiles().get(online);
        int season = plugin.ranked().season().id();
        CompletableFuture<PlayerProfile> future = loaded != null ? CompletableFuture.completedFuture(loaded)
                : plugin.dataStore().loadOffline(name, season);
        future.thenCompose(profile -> {
            if (profile == null) {
                return CompletableFuture.completedFuture(null);
            }
            Map<String, List<DataStore.EloChange>> history = new HashMap<>();
            List<CompletableFuture<?>> all = new ArrayList<>();
            for (Kit kit : plugin.kits().ranked()) {
                all.add(plugin.dataStore().eloHistory(profile.uuid(), kit.id(), season, 15)
                        .thenAccept(list -> {
                            synchronized (history) {
                                history.put(kit.id(), list);
                            }
                        }));
            }
            return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]))
                    .thenApply(v -> Map.entry(profile, history));
        }).whenComplete((result, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (error != null || result == null) {
                plugin.messages().send(viewer, "stats.not-found", Text.p("player", name));
                return;
            }
            new StatsMenu(plugin, viewer, result.getKey(), result.getValue(), plugin.kits().enabled()).open();
        }));
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        int wins = 0;
        int losses = 0;
        int kills = 0;
        int deaths = 0;
        int bestStreak = 0;
        for (KitStats s : target.allStats().values()) {
            wins += s.wins;
            losses += s.losses;
            kills += s.kills;
            deaths += s.deaths;
            bestStreak = Math.max(bestStreak, s.bestStreak);
        }
        int global = plugin.ranked().globalElo(target);
        Division globalDivision = plugin.ranked().division(global);
        set(4, Items.of(Material.PLAYER_HEAD).skull(Bukkit.getOfflinePlayer(target.uuid()))
                .name(m.messages().formatItem(m.string("stats.overview.name", "<player>"), Text.p("player", target.name())))
                .lore(m.lore(m.lines("stats.overview.lore"), Text.p("wins", wins), Text.p("losses", losses),
                        Text.p("winrate", Text.percent(wins, wins + losses)), Text.p("kills", kills), Text.p("deaths", deaths),
                        Text.p("kdr", Text.ratio(kills, deaths)), Text.p("best_streak", bestStreak), Text.p("elo", global),
                        Text.mm("division", globalDivision.formatted()), Text.p("season", plugin.ranked().season().name())))
                .hideAll().build());

        int[] slots = innerSlots(size() / 9);
        for (int i = 0; i < kits.size() && i < slots.length; i++) {
            Kit kit = kits.get(i);
            KitStats s = target.allStats().getOrDefault(kit.id(), new KitStats());
            Rating r = target.allRatings().getOrDefault(kit.id(), new Rating(plugin.settings().startingElo));
            Division division = plugin.ranked().division(r.elo);
            List<DataStore.EloChange> changes = history.getOrDefault(kit.id(), List.of());
            List<Integer> points = new ArrayList<>();
            if (!changes.isEmpty()) {
                points.add(changes.getFirst().before());
            }
            for (DataStore.EloChange c : changes) {
                points.add(c.after());
            }
            List<Component> lore = new ArrayList<>(m.lore(m.lines("stats.kit.lore"), Text.mm("kit", kit.displayName()),
                    Text.p("wins", s.wins), Text.p("losses", s.losses), Text.p("winrate", Text.percent(s.wins, s.games())),
                    Text.p("kills", s.kills), Text.p("deaths", s.deaths), Text.p("kdr", Text.ratio(s.kills, s.deaths)),
                    Text.p("streak", s.streak), Text.p("best_streak", s.bestStreak), Text.p("elo", r.elo), Text.p("peak", r.peak),
                    Text.p("ranked_wins", r.wins), Text.p("ranked_losses", r.losses), Text.mm("division", division.formatted())));
            if (kit.rules().ranked) {
                if (points.size() > 1) {
                    lore.addAll(m.lore(m.lines("stats.kit.history"), Text.p("sparkline", Text.sparkline(points)),
                            Text.p("games", changes.size())));
                    int shown = 0;
                    for (int c = changes.size() - 1; c >= 0 && shown < 5; c--, shown++) {
                        DataStore.EloChange change = changes.get(c);
                        int delta = change.after() - change.before();
                        lore.add(m.messages().formatItem(m.string(delta >= 0 ? "stats.kit.change-win" : "stats.kit.change-loss", ""),
                                Text.p("delta", (delta >= 0 ? "+" : "") + delta), Text.p("opponent", change.opponent()),
                                Text.p("elo", change.after())));
                    }
                } else {
                    lore.addAll(m.lore(m.lines("stats.kit.no-history")));
                }
            }
            set(slots[i], Items.of(kit.icon()).name(m.messages().formatItem(m.string("stats.kit.name", "<kit>"),
                    Text.mm("kit", kit.displayName()))).lore(lore).hideAll().build());
        }
        border(m.border());
    }
}
