package io.github.aubergineux.riposte.party;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.match.Match;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Parties, invites and party fights (split teams or free for all). */
public final class PartyManager implements Listener {

    private final RipostePlugin plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();

    public PartyManager(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    public Party partyOf(Player player) {
        return byMember.get(player.getUniqueId());
    }

    public Party partyOf(UUID uuid) {
        return byMember.get(uuid);
    }

    public int count() {
        return (int) byMember.values().stream().distinct().count();
    }

    private boolean inLobby(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        return profile != null && (profile.state() == PlayerState.LOBBY || profile.state() == PlayerState.QUEUE);
    }

    public void create(Player player) {
        if (partyOf(player) != null) {
            plugin.messages().send(player, "party.already-in");
            return;
        }
        if (!inLobby(player)) {
            plugin.messages().send(player, "party.busy");
            return;
        }
        plugin.queues().leave(player, false);
        Party party = new Party(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        plugin.messages().send(player, "party.created");
        refresh(player);
    }

    public void invite(Player leader, Player target) {
        Party party = partyOf(leader);
        if (party == null) {
            create(leader);
            party = partyOf(leader);
            if (party == null) {
                return;
            }
        }
        if (!party.isLeader(leader.getUniqueId())) {
            plugin.messages().send(leader, "party.not-leader");
            return;
        }
        if (leader == target) {
            plugin.messages().send(leader, "party.invite-self");
            return;
        }
        if (partyOf(target) != null) {
            plugin.messages().send(leader, "party.target-in-party", Text.p("player", target.getName()));
            return;
        }
        if (party.size() >= plugin.settings().partyMaxSize) {
            plugin.messages().send(leader, "party.full", Text.p("max", plugin.settings().partyMaxSize));
            return;
        }
        party.invites().put(target.getUniqueId(), System.currentTimeMillis() + plugin.settings().partyInviteSeconds * 1000L);
        plugin.messages().send(leader, "party.invite-sent", Text.p("player", target.getName()));
        Component join = plugin.messages().get("party.join-button")
                .clickEvent(ClickEvent.runCommand("/party accept " + leader.getName()))
                .hoverEvent(HoverEvent.showText(plugin.messages().get("party.join-hover")));
        for (Component line : plugin.messages().getList("party.invite-received", Text.p("player", leader.getName()),
                Text.p("size", party.size()), Text.p("seconds", plugin.settings().partyInviteSeconds), Text.c("join", join))) {
            target.sendMessage(line);
        }
    }

    public void accept(Player player, String leaderName) {
        Player leader = Bukkit.getPlayerExact(leaderName);
        Party party = leader == null ? null : partyOf(leader);
        Long expires = party == null ? null : party.invites().get(player.getUniqueId());
        if (party == null || expires == null || expires < System.currentTimeMillis()) {
            plugin.messages().send(player, "party.no-invite", Text.p("player", leaderName));
            return;
        }
        if (partyOf(player) != null) {
            plugin.messages().send(player, "party.already-in");
            return;
        }
        if (!inLobby(player)) {
            plugin.messages().send(player, "party.busy");
            return;
        }
        if (party.size() >= plugin.settings().partyMaxSize) {
            plugin.messages().send(player, "party.full", Text.p("max", plugin.settings().partyMaxSize));
            return;
        }
        party.invites().remove(player.getUniqueId());
        plugin.queues().leave(player, false);
        party.members().add(player.getUniqueId());
        byMember.put(player.getUniqueId(), party);
        broadcast(party, "party.joined", Text.p("player", player.getName()), Text.p("size", party.size()));
        refresh(player);
    }

    public void leave(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        removeMember(party, player.getUniqueId(), player.getName(), "party.left");
        plugin.messages().send(player, "party.you-left");
    }

    private void removeMember(Party party, UUID uuid, String name, String messageKey) {
        party.members().remove(uuid);
        byMember.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            refresh(player);
        }
        if (party.members().isEmpty()) {
            return;
        }
        broadcast(party, messageKey, Text.p("player", name), Text.p("size", party.size()));
        if (party.isLeader(uuid)) {
            UUID next = party.members().iterator().next();
            party.leader(next);
            Player newLeader = Bukkit.getPlayer(next);
            broadcast(party, "party.new-leader", Text.p("player", newLeader == null ? "?" : newLeader.getName()));
            if (newLeader != null) {
                refresh(newLeader);
            }
        }
    }

    public void kick(Player leader, String targetName) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            plugin.messages().send(leader, "party.not-leader");
            return;
        }
        for (UUID member : party.members()) {
            Player target = Bukkit.getPlayer(member);
            if (target != null && target.getName().equalsIgnoreCase(targetName) && target != leader) {
                removeMember(party, member, target.getName(), "party.kicked");
                plugin.messages().send(target, "party.you-were-kicked");
                return;
            }
        }
        plugin.messages().send(leader, "party.not-member", Text.p("player", targetName));
    }

    public void disband(Player leader) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            plugin.messages().send(leader, "party.not-leader");
            return;
        }
        broadcast(party, "party.disbanded");
        for (UUID member : new ArrayList<>(party.members())) {
            byMember.remove(member);
            Player player = Bukkit.getPlayer(member);
            if (player != null) {
                refresh(player);
            }
        }
        party.members().clear();
    }

    public void info(Player player) {
        Party party = partyOf(player);
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        List<String> names = new ArrayList<>();
        for (Player member : party.onlineMembers()) {
            if (!party.isLeader(member.getUniqueId())) {
                names.add(member.getName());
            }
        }
        plugin.messages().send(player, "party.info", Text.p("leader", party.leaderName()), Text.p("size", party.size()),
                Text.p("max", plugin.settings().partyMaxSize), Text.p("members", names.isEmpty() ? "-" : String.join(", ", names)));
    }

    public void chat(Player player, String message) {
        Party party = partyOf(player);
        if (party == null) {
            plugin.messages().send(player, "party.not-in");
            return;
        }
        broadcast(party, "party.chat", Text.p("player", player.getName()), Text.p("message", message));
    }

    /** Party fight: split into two random teams, or everyone for themselves. */
    public void startFight(Player leader, Kit kit, boolean ffa) {
        Party party = partyOf(leader);
        if (party == null || !party.isLeader(leader.getUniqueId())) {
            plugin.messages().send(leader, "party.not-leader");
            return;
        }
        List<Player> members = party.onlineMembers();
        if (members.size() < 2) {
            plugin.messages().send(leader, "party.too-small");
            return;
        }
        if (!party.allAvailable(plugin)) {
            plugin.messages().send(leader, "party.members-busy");
            return;
        }
        ArenaCopy copy = plugin.arenas().allocate(kit, null);
        if (copy == null) {
            plugin.messages().send(leader, "duel.no-free-arena");
            return;
        }
        Collections.shuffle(members);
        List<List<Player>> teams = new ArrayList<>();
        if (ffa) {
            for (Player member : members) {
                teams.add(List.of(member));
            }
        } else {
            List<Player> a = new ArrayList<>();
            List<Player> b = new ArrayList<>();
            for (int i = 0; i < members.size(); i++) {
                (i % 2 == 0 ? a : b).add(members.get(i));
            }
            teams.add(a);
            teams.add(b);
        }
        broadcast(party, ffa ? "party.ffa-start" : "party.split-start", Text.mm("kit", kit.displayName()));
        plugin.matches().start(kit, copy, ffa ? Match.Type.FFA : Match.Type.PARTY_SPLIT, teams, null);
    }

    public void broadcast(Party party, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        for (Player member : party.onlineMembers()) {
            plugin.messages().send(member, key, resolvers);
        }
    }

    private void refresh(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && profile.state() == PlayerState.LOBBY) {
            plugin.lobby().giveHotbar(player);
            plugin.scoreboards().refresh(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Party party = partyOf(event.getPlayer());
        if (party != null) {
            removeMember(party, event.getPlayer().getUniqueId(), event.getPlayer().getName(), "party.left");
        }
    }
}
