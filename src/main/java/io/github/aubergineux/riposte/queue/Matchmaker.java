package io.github.aubergineux.riposte.queue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pairs queued players. Unranked is first come, first served. Ranked pairs players whose ELO is
 * within both players' search range; the range starts narrow and widens the longer someone waits.
 * No server types, so it is unit tested directly.
 */
public final class Matchmaker {

    public record Entry(UUID id, int elo, long joinedAt) {
    }

    public record Pair(Entry first, Entry second) {
    }

    private final int rangeStart;
    private final int rangeGrowth;
    private final long growthIntervalMillis;
    private final int rangeMax;

    public Matchmaker(int rangeStart, int rangeGrowth, long growthIntervalMillis, int rangeMax) {
        this.rangeStart = rangeStart;
        this.rangeGrowth = rangeGrowth;
        this.growthIntervalMillis = Math.max(1, growthIntervalMillis);
        this.rangeMax = rangeMax;
    }

    /** How far from their own ELO this player accepts an opponent right now. */
    public int range(Entry entry, long now) {
        long waited = Math.max(0, now - entry.joinedAt());
        long steps = waited / growthIntervalMillis;
        long range = rangeStart + steps * rangeGrowth;
        return (int) Math.min(rangeMax, range);
    }

    public List<Pair> unranked(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Entry::joinedAt));
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < sorted.size(); i += 2) {
            pairs.add(new Pair(sorted.get(i), sorted.get(i + 1)));
        }
        return pairs;
    }

    /**
     * Longest waiting player picks first, taking the closest acceptable opponent.
     * A pair is acceptable when the ELO gap fits inside both players' current ranges.
     */
    public List<Pair> ranked(List<Entry> entries, long now) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingLong(Entry::joinedAt));
        boolean[] taken = new boolean[sorted.size()];
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (taken[i]) {
                continue;
            }
            Entry a = sorted.get(i);
            int rangeA = range(a, now);
            int best = -1;
            int bestGap = Integer.MAX_VALUE;
            for (int j = i + 1; j < sorted.size(); j++) {
                if (taken[j]) {
                    continue;
                }
                Entry b = sorted.get(j);
                int gap = Math.abs(a.elo() - b.elo());
                if (gap <= rangeA && gap <= range(b, now) && gap < bestGap) {
                    best = j;
                    bestGap = gap;
                }
            }
            if (best >= 0) {
                taken[i] = true;
                taken[best] = true;
                pairs.add(new Pair(a, sorted.get(best)));
            }
        }
        return pairs;
    }
}
