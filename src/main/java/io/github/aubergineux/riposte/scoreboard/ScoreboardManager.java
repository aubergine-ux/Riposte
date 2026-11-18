package io.github.aubergineux.riposte.scoreboard;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.event.EventManager;
import io.github.aubergineux.riposte.kit.KitRules;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.MatchTeam;
import io.github.aubergineux.riposte.match.Participant;
import io.github.aubergineux.riposte.party.Party;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.queue.QueueManager;
import io.github.aubergineux.riposte.ranked.Division;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Builds sidebar lines from scoreboard.yml for each player's current situation. */
public final class ScoreboardManager implements Listener {

    private final RipostePlugin plugin;
    private final YamlFile file;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private YamlConfiguration yaml;
    private BukkitTask task;

    public ScoreboardManager(RipostePlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "scoreboard.yml", true);
    }

    public void reload() {
        yaml = file.load();
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 10L, plugin.settings().scoreboardInterval);
    }

    public void disable() {
        if (task != null) {
            task.cancel();
        }
        for (UUID uuid : sidebars.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        }
        sidebars.clear();
    }

    private boolean enabled() {
        return yaml.getBoolean("enabled", true);
    }

    public void refresh(Player player) {
        if (enabled()) {
            update(player);
        }
    }

    public void refreshAll(Collection<Player> players) {
        for (Player player : players) {
            refresh(player);
        }
    }

    private void updateAll() {
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    private void update(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        Sidebar sidebar = sidebars.get(player.getUniqueId());
        if (sidebar == null || player.getScoreboard() != sidebar.board()) {
            sidebar = new Sidebar(player);
            sidebars.put(player.getUniqueId(), sidebar);
        }
        sidebar.visible(profile.settings().scoreboard);

        Match match = plugin.matches().matchOf(player);
        if (match == null && profile.state() == PlayerState.SPECTATING) {
            match = plugin.spectators().watching(player);
        }
        decorate(sidebar, match, player);
        if (!profile.settings().scoreboard) {
            return;
        }
        String section = section(profile, match, player);
        List<TagResolver> resolvers = new ArrayList<>();
        common(resolvers, player, profile);
        if (match != null) {
            matchPlaceholders(resolvers, match, player);
        }
        if (profile.state() == PlayerState.QUEUE) {
            queuePlaceholders(resolvers, player);
        }
        if (profile.state() == PlayerState.EVENT || plugin.events().current() != null) {
            eventPlaceholders(resolvers);
        }
        Party party = plugin.parties().partyOf(player);
        if (party != null) {
            resolvers.add(Text.p("party_leader", party.leaderName()));
            resolvers.add(Text.p("party_size", party.size()));
            resolvers.add(Text.p("party_max", plugin.settings().partyMaxSize));
        }
        TagResolver all = TagResolver.resolver(resolvers);
        sidebar.title(plugin.messages().format(yaml.getString("title", "<b>RIPOSTE"), all));
        List<Component> lines = new ArrayList<>();
        for (String line : yaml.getStringList("boards." + section)) {
            lines.add(plugin.messages().format(line, all));
        }
        sidebar.lines(lines);
    }

    private String section(PlayerProfile profile, Match match, Player player) {
        if (match != null) {
            if (profile.state() == PlayerState.SPECTATING) {
                return match.isDuel() ? "spectating" : "spectating-team";
            }
            if (match.isDuel()) {
                String special = "match-" + match.kit().rules().winCondition.name().toLowerCase(Locale.ROOT);
                return yaml.contains("boards." + special) ? special : "match";
            }
            if (match.kit().rules().winCondition == KitRules.WinCondition.BRIDGE && yaml.contains("boards.match-bridge")) {
                return "match-bridge";
            }
            return match.teams().size() > 2 ? "match-ffa" : "match-team";
        }
        return switch (profile.state()) {
            case QUEUE -> "queue";
            case EVENT -> "event";
            default -> plugin.parties().partyOf(player) != null ? "party" : "lobby";
        };
    }

    private void common(List<TagResolver> r, Player player, PlayerProfile profile) {
        r.add(Text.p("online", Bukkit.getOnlinePlayers().size()));
        r.add(Text.p("in_queue", plugin.queues().totalQueued()));
        r.add(Text.p("in_fight", plugin.matches().playersInMatches()));
        r.add(Text.p("player", player.getName()));
        r.add(Text.p("ping", player.getPing()));
        int global = plugin.ranked().globalElo(profile);
        Division division = plugin.ranked().division(global);
        r.add(Text.p("global_elo", global));
        r.add(Text.mm("division", division.formatted()));
        r.add(Text.p("season", plugin.ranked().season().name()));
        int wins = 0;
        int losses = 0;
        for (var stats : profile.allStats().values()) {
            wins += stats.wins;
            losses += stats.losses;
        }
        r.add(Text.p("wins", wins));
        r.add(Text.p("losses", losses));
        r.add(Text.mm("server", yaml.getString("server-name", "<gray>play.example.net")));
    }

    private void queuePlaceholders(List<TagResolver> r, Player player) {
        QueueManager.Ticket ticket = plugin.queues().ticket(player);
        if (ticket == null) {
            return;
        }
        r.add(Text.mm("kit", ticket.kit().displayName()));
        r.add(Text.p("queue_type", plugin.messages().raw(ticket.ranked() ? "scoreboard.ranked" : "scoreboard.unranked")));
        r.add(Text.p("queue_time", Text.formatDuration(System.currentTimeMillis() - ticket.joinedAt())));
        r.add(Text.p("elo", ticket.elo()));
    }

    private void eventPlaceholders(List<TagResolver> r) {
        EventManager.Event event = plugin.events().current();
        if (event == null) {
            r.add(Text.p("event_players", 0));
            return;
        }
        r.add(Text.mm("event", event.displayName()));
        r.add(Text.mm("event_kit", event.kit().displayName()));
        r.add(Text.p("event_players", event.players().size()));
        r.add(Text.p("event_starts", event.state() == EventManager.State.WAITING ? event.countdown() + "s" : "-"));
        r.add(Text.p("event_round", event.bracket() == null ? "-" : event.bracket().round() + "/" + event.bracket().totalRounds()));
    }

    private void matchPlaceholders(List<TagResolver> r, Match match, Player player) {
        r.add(Text.mm("kit", match.kit().displayName()));
        r.add(Text.mm("arena", match.arena().arena().displayName()));
        r.add(Text.p("duration", Text.formatDuration(match.duration())));
        r.add(Text.p("round", match.round()));
        r.add(Text.p("spectators", match.spectators().size()));
        r.add(Text.p("target", match.kit().rules().winCondition == KitRules.WinCondition.BOXING ? match.kit().rules().boxingHits : match.kit().rules().roundsToWin));
        Participant self = match.participant(player.getUniqueId());
        if (match.isDuel()) {
            Participant first = self != null ? self : match.teams().get(0).leader();
            Participant second = match.opponentOf(first.uuid());
            fighter(r, "", first);
            fighter(r, "opponent_", second);
            // spectator boards name the two fighters explicitly
            fighter(r, "p1_", match.teams().get(0).leader());
            fighter(r, "p2_", match.teams().get(1).leader());
            int diff = first.stats().hits - (second == null ? 0 : second.stats().hits);
            r.add(Text.mm("hit_diff", diff > 0 ? "<green>+" + diff : diff < 0 ? "<red>" + diff : "<gray>0"));
        } else if (self != null) {
            r.add(Text.p("hits", self.stats().hits));
            r.add(Text.p("combo", self.stats().combo));
            r.add(Text.p("kills", self.stats().kills));
        }
        int alive = 0;
        for (Participant p : match.participants()) {
            if (p.alive()) {
                alive++;
            }
        }
        r.add(Text.p("alive", alive));
        r.add(Text.p("total", match.participants().size()));
        if (match.teams().size() >= 2) {
            MatchTeam own = self != null ? self.team() : match.teams().get(0);
            MatchTeam other = match.teams().get(own.index() == 0 ? 1 : 0);
            r.add(Text.p("team_alive", own.aliveCount()));
            r.add(Text.p("team_size", own.members().size()));
            r.add(Text.p("enemy_alive", other.aliveCount()));
            r.add(Text.p("enemy_size", other.members().size()));
            r.add(Text.p("team_score", own.score()));
            r.add(Text.p("enemy_score", other.score()));
            r.add(Text.p("red_score", match.teams().get(0).score()));
            r.add(Text.p("blue_score", match.teams().get(1).score()));
        }
    }

    private void fighter(List<TagResolver> r, String prefix, Participant p) {
        Player player = p == null ? null : p.player();
        r.add(Text.p(prefix + "name", p == null ? "-" : p.name()));
        r.add(Text.p(prefix + "health", player == null || !p.alive() ? "0" : Text.hearts(player.getHealth())));
        r.add(Text.p(prefix + "ping", player == null ? 0 : player.getPing()));
        r.add(Text.p(prefix + "hits", p == null ? 0 : p.stats().hits));
        r.add(Text.p(prefix + "combo", p == null ? 0 : p.stats().combo));
        r.add(Text.p(prefix + "pots", p == null ? 0 : potions(player)));
        r.add(Text.p(prefix + "goals", p == null ? 0 : p.team().score()));
    }

    private static int potions(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (var item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.SPLASH_POTION) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /** Health under names and red/blue nametags while a match is on. */
    private void decorate(Sidebar sidebar, Match match, Player viewer) {
        if (match == null) {
            sidebar.health(null, List.of());
            sidebar.teams(List.of(), List.of());
            return;
        }
        if (match.kit().rules().showHealth && !match.kit().rules().damageDisabled()) {
            sidebar.health(plugin.messages().get("scoreboard.health-label"), match.onlinePlayers());
        } else {
            sidebar.health(null, List.of());
        }
        if (match.teams().size() == 2) {
            List<List<String>> teams = new ArrayList<>();
            for (MatchTeam team : match.teams()) {
                List<String> names = new ArrayList<>();
                for (Participant p : team.members()) {
                    names.add(p.name());
                }
                teams.add(names);
            }
            sidebar.teams(teams, List.of(NamedTextColor.RED, NamedTextColor.BLUE));
        } else {
            sidebar.teams(List.of(), List.of());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebars.remove(event.getPlayer().getUniqueId());
    }
}
