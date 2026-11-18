package io.github.aubergineux.riposte.match;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Participant {

    private final UUID uuid;
    private final String name;
    private final MatchTeam team;
    private final MatchStats stats = new MatchStats();
    private boolean alive = true;
    private boolean disconnected;
    private UUID lastAttacker;
    private long lastAttackTime;
    private InventorySnapshot snapshot;
    private long respawnAt;
    /** ELO before the match, for ranked summaries. */
    public int eloBefore;
    public int eloAfter;

    Participant(UUID uuid, String name, MatchTeam team) {
        this.uuid = uuid;
        this.name = name;
        this.team = team;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public MatchTeam team() {
        return team;
    }

    public MatchStats stats() {
        return stats;
    }

    public boolean alive() {
        return alive && !disconnected;
    }

    public void alive(boolean alive) {
        this.alive = alive;
    }

    public boolean disconnected() {
        return disconnected;
    }

    public void disconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    public Player player() {
        return Bukkit.getPlayer(uuid);
    }

    public void attackedBy(UUID attacker) {
        this.lastAttacker = attacker;
        this.lastAttackTime = System.currentTimeMillis();
    }

    /** The last player who hit us within the given window, for void and fall kill credit. */
    public UUID recentAttacker(long windowMillis) {
        if (lastAttacker == null || System.currentTimeMillis() - lastAttackTime > windowMillis) {
            return null;
        }
        return lastAttacker;
    }

    public InventorySnapshot snapshot() {
        return snapshot;
    }

    public void snapshot(InventorySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public long respawnAt() {
        return respawnAt;
    }

    public void respawnAt(long respawnAt) {
        this.respawnAt = respawnAt;
    }
}
