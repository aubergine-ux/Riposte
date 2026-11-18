package io.github.aubergineux.riposte.queue;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Unranked and ranked queues for every kit. Matching runs once per second. */
public final class QueueManager {

    public record Ticket(UUID player, Kit kit, boolean ranked, int elo, long joinedAt) {
    }

    private final RipostePlugin plugin;
    private final Map<String, Map<UUID, Ticket>> unranked = new HashMap<>();
    private final Map<String, Map<UUID, Ticket>> ranked = new HashMap<>();
    private final Map<UUID, Ticket> tickets = new HashMap<>();
    private long lastNoArenaWarning;

    public QueueManager(RipostePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private Matchmaker matchmaker() {
        var s = plugin.settings();
        return new Matchmaker(s.rankedRangeStart, s.rankedRangeGrowth, s.rankedRangeIntervalSeconds * 1000L, s.rankedRangeMax);
    }

    private Map<UUID, Ticket> queue(Kit kit, boolean isRanked) {
        return (isRanked ? ranked : unranked).computeIfAbsent(kit.id(), k -> new LinkedHashMap<>());
    }

    public Ticket ticket(Player player) {
        return tickets.get(player.getUniqueId());
    }

    public boolean isQueued(Player player) {
        return tickets.containsKey(player.getUniqueId());
    }

    public int size(Kit kit, boolean isRanked) {
        Map<UUID, Ticket> q = (isRanked ? ranked : unranked).get(kit.id());
        return q == null ? 0 : q.size();
    }

    public int totalQueued() {
        return tickets.size();
    }

    public void join(Player player, Kit kit, boolean isRanked) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        if (profile.state() != PlayerState.LOBBY && profile.state() != PlayerState.QUEUE) {
            plugin.messages().send(player, "queue.busy");
            return;
        }
        if (plugin.parties().partyOf(player) != null) {
            plugin.messages().send(player, "queue.in-party");
            return;
        }
        if (!kit.enabled()) {
            plugin.messages().send(player, "queue.kit-disabled", Text.mm("kit", kit.displayName()));
            return;
        }
        if (isRanked) {
            if (!kit.rules().ranked) {
                plugin.messages().send(player, "queue.not-ranked", Text.mm("kit", kit.displayName()));
                return;
            }
            int required = plugin.settings().rankedRequiredUnrankedWins;
            int wins = 0;
            for (var stats : profile.allStats().values()) {
                wins += stats.wins;
            }
            if (wins < required && !player.hasPermission("riposte.ranked.bypass")) {
                plugin.messages().send(player, "queue.ranked-locked", Text.p("required", required), Text.p("wins", wins));
                return;
            }
        }
        if (plugin.arenas().arenasFor(kit).isEmpty()) {
            plugin.messages().send(player, "queue.no-arenas", Text.mm("kit", kit.displayName()));
            return;
        }
        leave(player, false);
        int elo = profile.rating(kit.id(), plugin.settings().startingElo).elo;
        Ticket ticket = new Ticket(player.getUniqueId(), kit, isRanked, elo, System.currentTimeMillis());
        queue(kit, isRanked).put(player.getUniqueId(), ticket);
        tickets.put(player.getUniqueId(), ticket);
        profile.state(PlayerState.QUEUE);
        plugin.lobby().giveHotbar(player);
        plugin.messages().send(player, isRanked ? "queue.joined-ranked" : "queue.joined",
                Text.mm("kit", kit.displayName()), Text.p("elo", elo));
        plugin.scoreboards().refresh(player);
        player.playSound(net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK,
                net.kyori.adventure.sound.Sound.Source.MASTER, 0.6f, 1.4f));
    }

    public void leave(Player player, boolean notify) {
        Ticket ticket = tickets.remove(player.getUniqueId());
        if (ticket == null) {
            return;
        }
        queue(ticket.kit(), ticket.ranked()).remove(player.getUniqueId());
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && profile.state() == PlayerState.QUEUE) {
            profile.state(PlayerState.LOBBY);
            plugin.lobby().giveHotbar(player);
            plugin.scoreboards().refresh(player);
        }
        if (notify) {
            plugin.messages().send(player, "queue.left", Text.mm("kit", ticket.kit().displayName()));
        }
    }

    public void removeOffline(UUID uuid) {
        Ticket ticket = tickets.remove(uuid);
        if (ticket != null) {
            queue(ticket.kit(), ticket.ranked()).remove(uuid);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Matchmaker matchmaker = matchmaker();
        matchQueues(unranked, false, matchmaker, now);
        matchQueues(ranked, true, matchmaker, now);
        for (Ticket ticket : tickets.values()) {
            Player player = Bukkit.getPlayer(ticket.player());
            if (player == null) {
                continue;
            }
            long waited = now - ticket.joinedAt();
            if (ticket.ranked()) {
                int range = matchmaker.range(new Matchmaker.Entry(ticket.player(), ticket.elo(), ticket.joinedAt()), now);
                plugin.messages().actionBar(player, "queue.action-bar-ranked", Text.mm("kit", ticket.kit().displayName()),
                        Text.p("time", Text.formatDuration(waited)), Text.p("min", Math.max(0, ticket.elo() - range)),
                        Text.p("max", ticket.elo() + range), Text.p("count", size(ticket.kit(), true)));
            } else {
                plugin.messages().actionBar(player, "queue.action-bar", Text.mm("kit", ticket.kit().displayName()),
                        Text.p("time", Text.formatDuration(waited)), Text.p("count", size(ticket.kit(), false)));
            }
        }
    }

    private void matchQueues(Map<String, Map<UUID, Ticket>> queues, boolean isRanked, Matchmaker matchmaker, long now) {
        for (Map.Entry<String, Map<UUID, Ticket>> entry : queues.entrySet()) {
            Map<UUID, Ticket> q = entry.getValue();
            if (q.size() < 2) {
                continue;
            }
            List<Matchmaker.Entry> entries = new ArrayList<>(q.size());
            for (Ticket t : q.values()) {
                entries.add(new Matchmaker.Entry(t.player(), t.elo(), t.joinedAt()));
            }
            List<Matchmaker.Pair> pairs = isRanked ? matchmaker.ranked(entries, now) : matchmaker.unranked(entries);
            for (Matchmaker.Pair pair : pairs) {
                Ticket a = q.get(pair.first().id());
                Ticket b = q.get(pair.second().id());
                Player pa = Bukkit.getPlayer(a.player());
                Player pb = Bukkit.getPlayer(b.player());
                if (pa == null || pb == null) {
                    if (pa == null) {
                        removeOffline(a.player());
                    }
                    if (pb == null) {
                        removeOffline(b.player());
                    }
                    continue;
                }
                ArenaCopy copy = plugin.arenas().allocate(a.kit(), null);
                if (copy == null) {
                    if (now - lastNoArenaWarning > 10_000) {
                        lastNoArenaWarning = now;
                        plugin.messages().send(pa, "queue.waiting-arena");
                        plugin.messages().send(pb, "queue.waiting-arena");
                    }
                    break;
                }
                plugin.matches().start(a.kit(), copy, isRanked ? Match.Type.RANKED : Match.Type.UNRANKED,
                        List.of(List.of(pa), List.of(pb)), null);
            }
        }
    }
}
