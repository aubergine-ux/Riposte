package io.github.aubergineux.riposte.event;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.MatchTeam;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Hosted events. Tournaments run every pairing of a round at once in separate arenas; sumo events
 * run one fight at a time in a single arena while everyone else watches. Brackets advance automatically.
 */
public final class EventManager implements Listener {

    public enum Type {
        TOURNAMENT, SUMO;

        public static Type parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    public enum State {
        WAITING, RUNNING
    }

    public final class Event {
        private final Type type;
        private final Kit kit;
        private final String host;
        private final Set<UUID> players = new LinkedHashSet<>();
        private State state = State.WAITING;
        private int countdown;
        private Bracket<UUID> bracket;
        private final Set<UUID> fighting = new LinkedHashSet<>();
        private boolean betweenRounds;

        Event(Type type, Kit kit, String host, int countdown) {
            this.type = type;
            this.kit = kit;
            this.host = host;
            this.countdown = countdown;
        }

        public Type type() {
            return type;
        }

        public Kit kit() {
            return kit;
        }

        public String host() {
            return host;
        }

        public State state() {
            return state;
        }

        public int countdown() {
            return countdown;
        }

        public Set<UUID> players() {
            return players;
        }

        public Bracket<UUID> bracket() {
            return bracket;
        }

        public String displayName() {
            return plugin.messages().raw("events.names." + type.name().toLowerCase(Locale.ROOT));
        }
    }

    private final RipostePlugin plugin;
    private Event event;

    public EventManager(RipostePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public Event current() {
        return event;
    }

    public boolean isWaiting(Player player) {
        return event != null && event.players.contains(player.getUniqueId());
    }

    public void host(Player host, Type type, Kit kit) {
        if (event != null) {
            plugin.messages().send(host, "events.already-running");
            return;
        }
        if (type == Type.SUMO) {
            kit = plugin.kits().get("sumo") != null ? plugin.kits().get("sumo") : kit;
        }
        if (kit == null || plugin.arenas().arenasFor(kit).isEmpty()) {
            plugin.messages().send(host, "queue.no-arenas", Text.mm("kit", kit == null ? "?" : kit.displayName()));
            return;
        }
        event = new Event(type, kit, host.getName(), plugin.settings().eventJoinSeconds);
        announce();
        join(host);
    }

    private void announce() {
        Component join = plugin.messages().get("events.join-button")
                .clickEvent(ClickEvent.runCommand("/event join"))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("events.join-hover")));
        List<Component> lines = plugin.messages().getList("events.announce", Text.p("host", event.host),
                Text.mm("event", event.displayName()), Text.mm("kit", event.kit.displayName()),
                Text.p("seconds", event.countdown), Text.p("players", event.players.size()), Text.c("join", join));
        for (Player player : Bukkit.getOnlinePlayers()) {
            lines.forEach(player::sendMessage);
        }
    }

    public void join(Player player) {
        if (event == null) {
            plugin.messages().send(player, "events.none");
            return;
        }
        if (event.state != State.WAITING) {
            plugin.messages().send(player, "events.started");
            return;
        }
        if (event.players.contains(player.getUniqueId())) {
            plugin.messages().send(player, "events.already-joined");
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null || (profile.state() != PlayerState.LOBBY && profile.state() != PlayerState.QUEUE)) {
            plugin.messages().send(player, "events.busy");
            return;
        }
        if (plugin.parties().partyOf(player) != null) {
            plugin.messages().send(player, "events.in-party");
            return;
        }
        if (event.players.size() >= plugin.settings().eventMaxPlayers) {
            plugin.messages().send(player, "events.full");
            return;
        }
        plugin.queues().leave(player, false);
        event.players.add(player.getUniqueId());
        profile.state(PlayerState.EVENT);
        plugin.lobby().giveHotbar(player);
        plugin.scoreboards().refresh(player);
        broadcast("events.joined", Text.p("player", player.getName()), Text.p("players", event.players.size()),
                Text.p("max", plugin.settings().eventMaxPlayers));
    }

    public void leave(Player player, boolean notify) {
        if (event == null || !event.players.remove(player.getUniqueId())) {
            if (notify) {
                plugin.messages().send(player, "events.not-joined");
            }
            return;
        }
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && profile.state() == PlayerState.EVENT) {
            profile.state(PlayerState.LOBBY);
            plugin.lobby().giveHotbar(player);
            plugin.scoreboards().refresh(player);
        }
        if (plugin.spectators().isSpectating(player)) {
            plugin.spectators().stopSpectating(player, true);
        }
        if (notify) {
            plugin.messages().send(player, "events.left");
        }
        if (event.state == State.RUNNING && event.bracket != null) {
            forfeit(player.getUniqueId());
        }
    }

    public void cancel(Player by) {
        if (event == null) {
            plugin.messages().send(by, "events.none");
            return;
        }
        broadcastAll("events.cancelled");
        finish();
    }

    public void forceStart(Player by) {
        if (event == null || event.state != State.WAITING) {
            plugin.messages().send(by, "events.none");
            return;
        }
        event.countdown = 1;
    }

    private void tick() {
        if (event == null) {
            return;
        }
        if (event.state == State.WAITING) {
            event.countdown--;
            if (event.countdown == 30 || event.countdown == 10) {
                announce();
            } else if (event.countdown > 0 && event.countdown <= 5) {
                broadcast("events.starting-in", Text.p("seconds", event.countdown));
            }
            if (event.countdown <= 0) {
                if (event.players.size() < plugin.settings().eventMinPlayers) {
                    broadcastAll("events.not-enough", Text.p("min", plugin.settings().eventMinPlayers));
                    finish();
                    return;
                }
                start();
            }
            return;
        }
        // start fights that are still waiting (for a free arena, or for their turn in a sumo event)
        if (event.state == State.RUNNING && !event.betweenRounds && !event.bracket.roundComplete()) {
            runPairings();
        }
    }

    private void start() {
        List<UUID> seeds = new ArrayList<>(event.players);
        Collections.shuffle(seeds);
        event.bracket = new Bracket<>(seeds);
        event.state = State.RUNNING;
        broadcastAll("events.started-broadcast", Text.mm("event", event.displayName()), Text.p("players", seeds.size()),
                Text.p("rounds", event.bracket.totalRounds()));
        announceRound();
        runPairings();
    }

    private void announceRound() {
        Bracket<UUID> b = event.bracket;
        String roundName = b.round() == b.totalRounds() ? plugin.messages().raw("events.final")
                : b.round() == b.totalRounds() - 1 ? plugin.messages().raw("events.semi-final")
                : plugin.messages().raw("events.round").replace("<round>", String.valueOf(b.round()));
        broadcast("events.round-header", Text.mm("round_name", roundName));
        for (Bracket.Pairing<UUID> p : b.current()) {
            if (p.isBye()) {
                broadcast("events.bye", Text.p("player", name(p.first())));
            } else {
                broadcast("events.pairing", Text.p("first", name(p.first())), Text.p("second", name(p.second())));
            }
        }
    }

    /** Starts fights for undecided pairings. Sumo runs them one at a time. */
    private void runPairings() {
        if (event.type == Type.SUMO && !event.fighting.isEmpty()) {
            return;
        }
        for (Bracket.Pairing<UUID> p : event.bracket.current()) {
            if (p.decided() || event.fighting.contains(p.first())) {
                continue;
            }
            Player a = Bukkit.getPlayer(p.first());
            Player b = Bukkit.getPlayer(p.second());
            if (a == null || b == null) {
                event.bracket.report(a == null ? p.second() : p.first());
                continue;
            }
            ArenaCopy copy = plugin.arenas().allocate(event.kit, null);
            if (copy == null) {
                break;
            }
            event.fighting.add(p.first());
            event.fighting.add(p.second());
            Event current = event;
            Match match = plugin.matches().start(event.kit, copy, Match.Type.EVENT, List.of(List.of(a), List.of(b)),
                    result -> onMatchEnd(current, p, result));
            if (event.type == Type.SUMO) {
                for (UUID uuid : event.players) {
                    Player watcher = Bukkit.getPlayer(uuid);
                    if (watcher != null && !p.involves(uuid)) {
                        plugin.spectators().spectate(watcher, match);
                    }
                }
                break;
            }
        }
        checkRoundComplete();
    }

    private void onMatchEnd(Event ended, Bracket.Pairing<UUID> pairing, Match.Result result) {
        if (event != ended) {
            return;
        }
        event.fighting.remove(pairing.first());
        event.fighting.remove(pairing.second());
        if (pairing.decided()) {
            checkRoundComplete();
            return;
        }
        UUID winner;
        MatchTeam team = result.winner();
        if (team != null && team.leader() != null) {
            winner = team.leader().uuid();
        } else {
            winner = Math.random() < 0.5 ? pairing.first() : pairing.second();
        }
        event.bracket.report(winner);
        eliminate(pairing.loser());
        broadcast("events.result", Text.p("winner", name(winner)), Text.p("loser", name(pairing.loser())));
        checkRoundComplete();
    }

    private void forfeit(UUID quitter) {
        Bracket.Pairing<UUID> p = event.bracket.pairingOf(quitter);
        if (p != null && !p.decided() && !event.fighting.contains(quitter)) {
            UUID other = quitter.equals(p.first()) ? p.second() : p.first();
            if (other != null) {
                event.bracket.report(other);
            }
            checkRoundComplete();
        }
    }

    private void eliminate(UUID loser) {
        if (loser == null || event == null) {
            return;
        }
        event.players.remove(loser);
        Player player = Bukkit.getPlayer(loser);
        if (player != null) {
            plugin.messages().send(player, "events.eliminated");
            PlayerProfile profile = plugin.profiles().get(player);
            if (profile != null && profile.state() == PlayerState.EVENT) {
                profile.state(PlayerState.LOBBY);
                plugin.lobby().giveHotbar(player);
            }
        }
    }

    private void checkRoundComplete() {
        if (event == null || event.bracket == null || !event.bracket.roundComplete() || !event.fighting.isEmpty()) {
            return;
        }
        if (event.bracket.finished()) {
            UUID champion = event.bracket.champion();
            broadcastAll("events.winner", Text.p("player", name(champion)), Text.mm("event", event.displayName()));
            Player player = Bukkit.getPlayer(champion);
            if (player != null) {
                plugin.messages().title(player, "titles.event-winner", Text.mm("event", event.displayName()));
            }
            finish();
            return;
        }
        event.bracket.advance();
        event.betweenRounds = true;
        Event current = event;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event == current && event.state == State.RUNNING) {
                event.betweenRounds = false;
                announceRound();
                runPairings();
            }
        }, 60L);
    }

    private void finish() {
        Event ended = event;
        event = null;
        if (ended == null) {
            return;
        }
        for (UUID uuid : ended.players) {
            Player player = Bukkit.getPlayer(uuid);
            PlayerProfile profile = player == null ? null : plugin.profiles().get(player);
            if (profile == null) {
                continue;
            }
            if (profile.state() == PlayerState.EVENT) {
                profile.state(PlayerState.LOBBY);
                plugin.lobby().giveHotbar(player);
                plugin.scoreboards().refresh(player);
            } else if (profile.state() == PlayerState.SPECTATING) {
                plugin.spectators().stopSpectating(player, true);
            }
        }
    }

    private String name(UUID uuid) {
        if (uuid == null) {
            return "-";
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            return player.getName();
        }
        PlayerProfile profile = plugin.profiles().get(uuid);
        return profile == null ? "?" : profile.name();
    }

    private void broadcast(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        if (event == null) {
            return;
        }
        for (UUID uuid : event.players) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                plugin.messages().send(player, key, resolvers);
            }
        }
    }

    private void broadcastAll(String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.messages().send(player, key, resolvers);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (event != null && event.players.contains(e.getPlayer().getUniqueId())) {
            leave(e.getPlayer(), false);
        }
    }
}
