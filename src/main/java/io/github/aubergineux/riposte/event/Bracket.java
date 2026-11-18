package io.github.aubergineux.riposte.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single elimination bracket. When the player count is not a power of two, the first seeds get a bye
 * in round one so that round two is always a clean power of two. Pure logic, unit tested.
 */
public final class Bracket<T> {

    public static final class Pairing<T> {
        private final T first;
        private final T second;
        private T winner;

        Pairing(T first, T second) {
            this.first = first;
            this.second = second;
            if (second == null) {
                this.winner = first;
            }
        }

        public T first() {
            return first;
        }

        public T second() {
            return second;
        }

        public boolean isBye() {
            return second == null;
        }

        public T winner() {
            return winner;
        }

        public boolean decided() {
            return winner != null;
        }

        public T loser() {
            if (winner == null || second == null) {
                return null;
            }
            return winner.equals(first) ? second : first;
        }

        public boolean involves(T player) {
            return player.equals(first) || player.equals(second);
        }
    }

    private final List<List<Pairing<T>>> rounds = new ArrayList<>();
    private final int totalRounds;

    public Bracket(List<T> seeds) {
        if (seeds.size() < 2) {
            throw new IllegalArgumentException("A bracket needs at least two players");
        }
        int size = 1;
        while (size < seeds.size()) {
            size <<= 1;
        }
        this.totalRounds = Integer.numberOfTrailingZeros(size);
        int byes = size - seeds.size();
        List<Pairing<T>> first = new ArrayList<>();
        int i = 0;
        for (int b = 0; b < byes; b++) {
            first.add(new Pairing<>(seeds.get(i++), null));
        }
        while (i < seeds.size()) {
            first.add(new Pairing<>(seeds.get(i), seeds.get(i + 1)));
            i += 2;
        }
        rounds.add(first);
    }

    /** Current round number, starting at 1. */
    public int round() {
        return rounds.size();
    }

    public int totalRounds() {
        return totalRounds;
    }

    public List<Pairing<T>> current() {
        return Collections.unmodifiableList(rounds.getLast());
    }

    public List<List<Pairing<T>>> rounds() {
        return Collections.unmodifiableList(rounds);
    }

    /** Records a result in the current round. Returns false if the player has no undecided pairing. */
    public boolean report(T winner) {
        for (Pairing<T> p : rounds.getLast()) {
            if (!p.decided() && p.involves(winner)) {
                p.winner = winner;
                return true;
            }
        }
        return false;
    }

    /** The pairing in the current round that involves this player, if any. */
    public Pairing<T> pairingOf(T player) {
        for (Pairing<T> p : rounds.getLast()) {
            if (p.involves(player)) {
                return p;
            }
        }
        return null;
    }

    public boolean roundComplete() {
        for (Pairing<T> p : rounds.getLast()) {
            if (!p.decided()) {
                return false;
            }
        }
        return true;
    }

    public boolean finished() {
        return roundComplete() && rounds.getLast().size() == 1;
    }

    public T champion() {
        return finished() ? rounds.getLast().getFirst().winner() : null;
    }

    /** Builds the next round from this round's winners, in bracket order. */
    public List<Pairing<T>> advance() {
        if (!roundComplete()) {
            throw new IllegalStateException("Round " + round() + " is not complete");
        }
        if (finished()) {
            throw new IllegalStateException("Bracket is finished");
        }
        List<Pairing<T>> last = rounds.getLast();
        List<Pairing<T>> next = new ArrayList<>();
        for (int i = 0; i < last.size(); i += 2) {
            next.add(new Pairing<>(last.get(i).winner(), last.get(i + 1).winner()));
        }
        rounds.add(next);
        return next;
    }
}
