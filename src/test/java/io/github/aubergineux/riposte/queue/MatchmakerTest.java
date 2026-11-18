package io.github.aubergineux.riposte.queue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchmakerTest {

    // start 50, +25 every 5 seconds, capped at 200
    private final Matchmaker mm = new Matchmaker(50, 25, 5_000, 200);

    private static Matchmaker.Entry entry(int elo, long joinedAt) {
        return new Matchmaker.Entry(UUID.randomUUID(), elo, joinedAt);
    }

    @Test
    void unrankedPairsInJoinOrder() {
        Matchmaker.Entry a = entry(1000, 1);
        Matchmaker.Entry b = entry(1000, 2);
        Matchmaker.Entry c = entry(1000, 3);
        List<Matchmaker.Pair> pairs = mm.unranked(List.of(c, a, b));
        assertEquals(1, pairs.size());
        assertEquals(a, pairs.getFirst().first());
        assertEquals(b, pairs.getFirst().second());
    }

    @Test
    void unrankedPairsEveryoneWhenEven() {
        List<Matchmaker.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(entry(1000 + i * 300, i));
        }
        assertEquals(5, mm.unranked(entries).size());
    }

    @Test
    void rangeGrowsWithWaitTimeAndCaps() {
        Matchmaker.Entry e = entry(1000, 0);
        assertEquals(50, mm.range(e, 0));
        assertEquals(50, mm.range(e, 4_999));
        assertEquals(75, mm.range(e, 5_000));
        assertEquals(125, mm.range(e, 15_000));
        assertEquals(200, mm.range(e, 600_000));
    }

    @Test
    void rankedRefusesWideGapAtFirst() {
        Matchmaker.Entry a = entry(1000, 0);
        Matchmaker.Entry b = entry(1100, 0);
        assertTrue(mm.ranked(List.of(a, b), 1_000).isEmpty());
    }

    @Test
    void rankedAcceptsGapOnceBothRangesWiden() {
        Matchmaker.Entry a = entry(1000, 0);
        Matchmaker.Entry b = entry(1100, 0);
        // after 10s both ranges are 100
        assertEquals(1, mm.ranked(List.of(a, b), 10_000).size());
    }

    @Test
    void rankedNeedsBothPlayersToAccept() {
        Matchmaker.Entry veteran = entry(1000, 0);
        Matchmaker.Entry newcomer = entry(1100, 59_000);
        // veteran waited 60s (range 200) but the newcomer just joined (range 50)
        assertTrue(mm.ranked(List.of(veteran, newcomer), 60_000).isEmpty());
        // ten seconds later the newcomer accepts 100 too
        assertEquals(1, mm.ranked(List.of(veteran, newcomer), 69_000).size());
    }

    @Test
    void rankedPicksClosestOpponentForLongestWaiter() {
        Matchmaker.Entry first = entry(1000, 0);
        Matchmaker.Entry far = entry(1040, 1);
        Matchmaker.Entry close = entry(1010, 2);
        List<Matchmaker.Pair> pairs = mm.ranked(List.of(far, close, first), 3);
        assertEquals(1, pairs.size());
        assertEquals(first, pairs.getFirst().first());
        assertEquals(close, pairs.getFirst().second());
    }

    @Test
    void rankedNeverUsesAPlayerTwice() {
        List<Matchmaker.Entry> entries = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            entries.add(entry(900 + (i * 7919) % 400, i * 10L));
        }
        List<Matchmaker.Pair> pairs = mm.ranked(entries, 120_000);
        Set<UUID> seen = new HashSet<>();
        for (Matchmaker.Pair pair : pairs) {
            assertTrue(seen.add(pair.first().id()));
            assertTrue(seen.add(pair.second().id()));
            assertTrue(Math.abs(pair.first().elo() - pair.second().elo()) <= 200);
        }
        assertTrue(pairs.size() >= 95, "most players should be matched, got " + pairs.size());
    }
}
