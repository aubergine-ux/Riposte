package io.github.aubergineux.riposte.duel;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.party.Party;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Duel requests between players (or party leaders), with accept/deny buttons and rematches. */
public final class DuelManager implements Listener {

    public record Request(UUID sender, String senderName, UUID target, String kit, String arena, boolean party, long expires) {
    }

    private final RipostePlugin plugin;
    /** target -> (sender -> request) */
    private final Map<UUID, Map<UUID, Request>> incoming = new HashMap<>();
    private final Map<UUID, Long> lastSent = new HashMap<>();

    public DuelManager(RipostePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L, 20L);
    }

    private void expire() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<UUID, Request>>> it = incoming.entrySet().iterator(); it.hasNext(); ) {
            Map<UUID, Request> requests = it.next().getValue();
            requests.values().removeIf(r -> {
                if (r.expires() > now) {
                    return false;
                }
                Player sender = Bukkit.getPlayer(r.sender());
                Player target = Bukkit.getPlayer(r.target());
                if (sender != null) {
                    plugin.messages().send(sender, "duel.expired-sender", Text.p("player", target == null ? "?" : target.getName()));
                }
                return true;
            });
            if (requests.isEmpty()) {
                it.remove();
            }
        }
    }

    private boolean available(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        return profile != null && (profile.state() == PlayerState.LOBBY || profile.state() == PlayerState.QUEUE);
    }

    /** Sends a request. Party leaders automatically challenge the other party. */
    public void send(Player sender, Player target, Kit kit, String arena) {
        if (sender == target) {
            plugin.messages().send(sender, "duel.self");
            return;
        }
        PlayerProfile targetProfile = plugin.profiles().get(target);
        if (targetProfile == null) {
            return;
        }
        Party senderParty = plugin.parties().partyOf(sender);
        Party targetParty = plugin.parties().partyOf(target);
        boolean party = senderParty != null;
        if (party) {
            if (!senderParty.isLeader(sender.getUniqueId())) {
                plugin.messages().send(sender, "party.not-leader");
                return;
            }
            if (targetParty == null || !targetParty.isLeader(target.getUniqueId())) {
                plugin.messages().send(sender, "duel.target-not-party-leader", Text.p("player", target.getName()));
                return;
            }
            if (targetParty == senderParty) {
                plugin.messages().send(sender, "duel.self");
                return;
            }
        } else if (targetParty != null) {
            plugin.messages().send(sender, "duel.target-in-party", Text.p("player", target.getName()));
            return;
        }
        if (!targetProfile.settings().duelRequests && !sender.hasPermission("riposte.duel.bypass")) {
            plugin.messages().send(sender, "duel.disabled", Text.p("player", target.getName()));
            return;
        }
        if (!available(sender)) {
            plugin.messages().send(sender, "duel.busy-self");
            return;
        }
        if (!available(target)) {
            plugin.messages().send(sender, "duel.busy", Text.p("player", target.getName()));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastSent.get(sender.getUniqueId());
        if (last != null && now - last < plugin.settings().duelCooldownSeconds * 1000L) {
            plugin.messages().send(sender, "duel.cooldown");
            return;
        }
        if (plugin.arenas().arenasFor(kit).isEmpty()) {
            plugin.messages().send(sender, "queue.no-arenas", Text.mm("kit", kit.displayName()));
            return;
        }
        lastSent.put(sender.getUniqueId(), now);
        Request request = new Request(sender.getUniqueId(), sender.getName(), target.getUniqueId(), kit.id(), arena, party,
                now + plugin.settings().duelExpireSeconds * 1000L);
        incoming.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>()).put(sender.getUniqueId(), request);

        String arenaName = arena == null ? plugin.messages().raw("duel.random-arena") : plugin.arenas().get(arena) == null ? arena : plugin.arenas().get(arena).displayName();
        plugin.messages().send(sender, party ? "duel.sent-party" : "duel.sent", Text.p("player", target.getName()),
                Text.mm("kit", kit.displayName()), Text.mm("arena", arenaName));
        Component accept = plugin.messages().get("duel.accept-button")
                .clickEvent(ClickEvent.runCommand("/duel accept " + sender.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("duel.accept-hover")));
        Component deny = plugin.messages().get("duel.deny-button")
                .clickEvent(ClickEvent.runCommand("/duel deny " + sender.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("duel.deny-hover")));
        for (Component line : plugin.messages().getList(party ? "duel.received-party" : "duel.received",
                Text.p("player", sender.getName()), Text.mm("kit", kit.displayName()), Text.mm("arena", arenaName),
                Text.p("size", senderParty == null ? 1 : senderParty.size()), Text.p("seconds", plugin.settings().duelExpireSeconds),
                Text.c("accept", accept), Text.c("deny", deny))) {
            target.sendMessage(line);
        }
        target.playSound(net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING,
                net.kyori.adventure.sound.Sound.Source.MASTER, 0.7f, 1.6f));
    }

    public void accept(Player target, String senderName) {
        Request request = find(target, senderName);
        if (request == null) {
            plugin.messages().send(target, "duel.no-request", Text.p("player", senderName));
            return;
        }
        incoming.get(target.getUniqueId()).remove(request.sender());
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender == null) {
            plugin.messages().send(target, "errors.player-offline", Text.p("player", senderName));
            return;
        }
        Kit kit = plugin.kits().get(request.kit());
        if (kit == null) {
            plugin.messages().send(target, "errors.unknown-kit", Text.p("kit", request.kit()));
            return;
        }
        List<List<Player>> teams;
        Match.Type type;
        if (request.party()) {
            Party a = plugin.parties().partyOf(sender);
            Party b = plugin.parties().partyOf(target);
            if (a == null || b == null || a == b || !a.isLeader(sender.getUniqueId()) || !b.isLeader(target.getUniqueId())) {
                plugin.messages().send(target, "duel.no-longer-valid");
                return;
            }
            if (!a.allAvailable(plugin) || !b.allAvailable(plugin)) {
                plugin.messages().send(target, "party.members-busy");
                return;
            }
            teams = List.of(a.onlineMembers(), b.onlineMembers());
            type = Match.Type.PARTY_VS_PARTY;
        } else {
            if (!available(sender) || !available(target)) {
                plugin.messages().send(target, "duel.busy", Text.p("player", sender.getName()));
                return;
            }
            teams = List.of(List.of(sender), List.of(target));
            type = Match.Type.DUEL;
        }
        ArenaCopy copy = plugin.arenas().allocate(kit, request.arena());
        if (copy == null) {
            plugin.messages().send(target, "duel.no-free-arena");
            plugin.messages().send(sender, "duel.no-free-arena");
            return;
        }
        plugin.messages().send(sender, "duel.accepted", Text.p("player", target.getName()));
        plugin.matches().start(kit, copy, type, teams, null);
    }

    public void deny(Player target, String senderName) {
        Request request = find(target, senderName);
        if (request == null) {
            plugin.messages().send(target, "duel.no-request", Text.p("player", senderName));
            return;
        }
        incoming.get(target.getUniqueId()).remove(request.sender());
        plugin.messages().send(target, "duel.denied-target", Text.p("player", request.senderName()));
        Player sender = Bukkit.getPlayer(request.sender());
        if (sender != null) {
            plugin.messages().send(sender, "duel.denied-sender", Text.p("player", target.getName()));
        }
    }

    private Request find(Player target, String senderName) {
        Map<UUID, Request> requests = incoming.get(target.getUniqueId());
        if (requests == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (Request r : requests.values()) {
            if (r.senderName().equalsIgnoreCase(senderName) && r.expires() > now) {
                return r;
            }
        }
        return null;
    }

    public boolean hasRequestFrom(Player target, UUID sender) {
        Map<UUID, Request> requests = incoming.get(target.getUniqueId());
        return requests != null && requests.containsKey(sender);
    }

    /** Rematch: accepts if the opponent already asked, otherwise sends them a request with the same kit and arena. */
    public void rematch(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null || profile.rematchTarget == null || profile.rematchExpires < System.currentTimeMillis()) {
            plugin.messages().send(player, "duel.no-rematch");
            return;
        }
        Player target = Bukkit.getPlayer(profile.rematchTarget);
        if (target == null) {
            plugin.messages().send(player, "errors.player-offline", Text.p("player", "?"));
            return;
        }
        Kit kit = plugin.kits().get(profile.rematchKit);
        if (kit == null) {
            plugin.messages().send(player, "duel.no-rematch");
            return;
        }
        if (hasRequestFrom(player, target.getUniqueId())) {
            accept(player, target.getName());
            return;
        }
        send(player, target, kit, profile.rematchArena);
    }

    public void clearRequests(Player player) {
        incoming.remove(player.getUniqueId());
        for (Map<UUID, Request> requests : incoming.values()) {
            requests.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearRequests(event.getPlayer());
        lastSent.remove(event.getPlayer().getUniqueId());
    }
}
