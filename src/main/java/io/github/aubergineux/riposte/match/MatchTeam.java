package io.github.aubergineux.riposte.match;

import java.util.ArrayList;
import java.util.List;

public final class MatchTeam {

    private final int index;
    private final List<Participant> members = new ArrayList<>();
    private int score;

    MatchTeam(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    public List<Participant> members() {
        return members;
    }

    public int score() {
        return score;
    }

    public void addScore() {
        score++;
    }

    public boolean hasAlive() {
        for (Participant p : members) {
            if (p.alive()) {
                return true;
            }
        }
        return false;
    }

    public int aliveCount() {
        int count = 0;
        for (Participant p : members) {
            if (p.alive()) {
                count++;
            }
        }
        return count;
    }

    public boolean allDisconnected() {
        for (Participant p : members) {
            if (!p.disconnected()) {
                return false;
            }
        }
        return true;
    }

    public String names() {
        StringBuilder sb = new StringBuilder();
        for (Participant p : members) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(p.name());
        }
        return sb.toString();
    }

    public Participant leader() {
        return members.isEmpty() ? null : members.getFirst();
    }
}
