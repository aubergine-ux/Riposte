package io.github.aubergineux.riposte.match;

import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** State of one fight. Lifecycle is driven by {@link MatchManager}. */
public final class Match {

    public enum State {
        STARTING, FIGHTING, ROUND_OVER, ENDED
    }

    public enum Type {
        UNRANKED(true), RANKED(true), DUEL(true), PARTY_SPLIT(false), PARTY_VS_PARTY(false), FFA(false), EVENT(false);

        /** Whether the result counts toward wins, losses and streaks. */
        public final boolean recordsStats;

        Type(boolean recordsStats) {
            this.recordsStats = recordsStats;
        }

        public boolean ranked() {
            return this == RANKED;
        }
    }

    public record Result(Match match, MatchTeam winner) {
        public boolean draw() {
            return winner == null;
        }
    }

    private final UUID id = UUID.randomUUID();
    private final Kit kit;
    private final ArenaCopy arena;
    private final Type type;
    private final List<MatchTeam> teams = new ArrayList<>();
    private final Map<UUID, Participant> participants = new LinkedHashMap<>();
    private final Set<UUID> spectators = new LinkedHashSet<>();
    private final Consumer<Result> onEnd;
    private State state = State.STARTING;
    private int countdown;
    private int round = 1;
    private long startedAt;
    private long fightStartedAt;
    private MatchTeam winner;

    Match(Kit kit, ArenaCopy arena, Type type, List<List<Player>> teamPlayers, Consumer<Result> onEnd) {
        this.kit = kit;
        this.arena = arena;
        this.type = type;
        this.onEnd = onEnd;
        for (int i = 0; i < teamPlayers.size(); i++) {
            MatchTeam team = new MatchTeam(i);
            for (Player player : teamPlayers.get(i)) {
                Participant participant = new Participant(player.getUniqueId(), player.getName(), team);
                team.members().add(participant);
                participants.put(player.getUniqueId(), participant);
            }
            teams.add(team);
        }
        this.startedAt = System.currentTimeMillis();
    }

    public UUID id() {
        return id;
    }

    public Kit kit() {
        return kit;
    }

    public ArenaCopy arena() {
        return arena;
    }

    public Type type() {
        return type;
    }

    public List<MatchTeam> teams() {
        return teams;
    }

    public Collection<Participant> participants() {
        return participants.values();
    }

    public Participant participant(UUID uuid) {
        return participants.get(uuid);
    }

    public Set<UUID> spectators() {
        return spectators;
    }

    public State state() {
        return state;
    }

    void state(State state) {
        this.state = state;
    }

    public int countdown() {
        return countdown;
    }

    void countdown(int countdown) {
        this.countdown = countdown;
    }

    public int round() {
        return round;
    }

    void nextRound() {
        round++;
    }

    public long startedAt() {
        return startedAt;
    }

    public long fightStartedAt() {
        return fightStartedAt;
    }

    void fightStartedAt(long time) {
        this.fightStartedAt = time;
    }

    /** Fight time so far, excluding the opening countdown. */
    public long duration() {
        return fightStartedAt == 0 ? 0 : System.currentTimeMillis() - fightStartedAt;
    }

    public MatchTeam winner() {
        return winner;
    }

    void winner(MatchTeam winner) {
        this.winner = winner;
    }

    Consumer<Result> onEnd() {
        return onEnd;
    }

    public boolean isDuel() {
        return teams.size() == 2 && teams.get(0).members().size() == 1 && teams.get(1).members().size() == 1;
    }

    /** For 1v1 matches: the other participant. */
    public Participant opponentOf(UUID uuid) {
        Participant self = participants.get(uuid);
        if (self == null) {
            return null;
        }
        for (Participant p : participants.values()) {
            if (p.team() != self.team()) {
                return p;
            }
        }
        return null;
    }

    public List<Player> onlinePlayers() {
        List<Player> out = new ArrayList<>();
        for (Participant p : participants.values()) {
            Player player = p.player();
            if (player != null && !p.disconnected()) {
                out.add(player);
            }
        }
        return out;
    }

    public List<Player> spectatorPlayers() {
        List<Player> out = new ArrayList<>();
        for (UUID uuid : spectators) {
            Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player != null) {
                out.add(player);
            }
        }
        return out;
    }

    /** Everyone who should hear about this match: fighters and spectators. */
    public List<Player> audience() {
        List<Player> out = onlinePlayers();
        out.addAll(spectatorPlayers());
        return out;
    }

    public List<MatchTeam> teamsAlive() {
        List<MatchTeam> out = new ArrayList<>();
        for (MatchTeam team : teams) {
            if (team.hasAlive()) {
                out.add(team);
            }
        }
        return out;
    }

    public String title() {
        if (teams.size() == 2) {
            return teams.get(0).names() + " vs " + teams.get(1).names();
        }
        return "FFA (" + participants.size() + " players)";
    }
}
