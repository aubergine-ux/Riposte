package io.github.aubergineux.riposte.party;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Party {

    private final UUID id = UUID.randomUUID();
    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();
    private final Map<UUID, Long> invites = new HashMap<>();

    Party(UUID leader) {
        this.leader = leader;
        members.add(leader);
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    void leader(UUID leader) {
        this.leader = leader;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }

    public Set<UUID> members() {
        return members;
    }

    public int size() {
        return members.size();
    }

    Map<UUID, Long> invites() {
        return invites;
    }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : members) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    public boolean allAvailable(RipostePlugin plugin) {
        for (Player player : onlineMembers()) {
            PlayerProfile profile = plugin.profiles().get(player);
            if (profile == null || profile.state() != PlayerState.LOBBY) {
                return false;
            }
        }
        return true;
    }

    public String leaderName() {
        Player player = Bukkit.getPlayer(leader);
        return player == null ? "?" : player.getName();
    }
}
