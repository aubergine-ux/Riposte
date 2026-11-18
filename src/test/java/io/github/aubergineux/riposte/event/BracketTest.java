package io.github.aubergineux.riposte.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BracketTest {

    private static List<String> players(int n) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            out.add("p" + i);
        }
        return out;
    }

    @Test
    void powerOfTwoHasNoByes() {
        Bracket<String> b = new Bracket<>(players(8));
        assertEquals(3, b.totalRounds());
        assertEquals(4, b.current().size());
        assertTrue(b.current().stream().noneMatch(Bracket.Pairing::isBye));
    }

    @Test
    void oddCountGivesByesSoRoundTwoIsClean() {
        Bracket<String> b = new Bracket<>(players(5));
        assertEquals(3, b.totalRounds());
        assertEquals(4, b.current().size());
        assertEquals(3, b.current().stream().filter(Bracket.Pairing::isBye).count());
        // the one real pairing
        b.report("p4");
        assertTrue(b.roundComplete());
        assertEquals(2, b.advance().size());
    }

    @Test
    void playsThroughToAChampion() {
        Bracket<String> b = new Bracket<>(players(6));
        while (!b.finished()) {
            for (Bracket.Pairing<String> p : b.current()) {
                if (!p.decided()) {
                    // the first listed player always wins
                    b.report(p.first());
                }
            }
            if (!b.finished()) {
                b.advance();
            }
        }
        assertEquals("p1", b.champion());
        assertEquals(3, b.round());
    }

    @Test
    void loserIsTheOtherPlayer() {
        Bracket<String> b = new Bracket<>(players(2));
        Bracket.Pairing<String> p = b.current().getFirst();
        assertNull(p.loser());
        b.report("p2");
        assertEquals("p2", p.winner());
        assertEquals("p1", p.loser());
        assertTrue(b.finished());
        assertEquals("p2", b.champion());
    }

    @Test
    void reportingTwiceOrForStrangersFails() {
        Bracket<String> b = new Bracket<>(players(4));
        assertTrue(b.report("p1"));
        assertTrue(!b.report("p2"));
        assertTrue(!b.report("nobody"));
    }

    @Test
    void cannotAdvanceUnfinishedRound() {
        Bracket<String> b = new Bracket<>(players(4));
        assertThrows(IllegalStateException.class, b::advance);
    }

    @Test
    void needsTwoPlayers() {
        assertThrows(IllegalArgumentException.class, () -> new Bracket<>(players(1)));
    }

    @Test
    void largeBracketsHaveTheRightShape() {
        for (int n = 2; n <= 70; n++) {
            Bracket<String> b = new Bracket<>(players(n));
            int size = Integer.highestOneBit(n - 1) << 1;
            assertEquals(size / 2, b.current().size(), "players " + n);
            long people = b.current().stream().mapToLong(p -> p.isBye() ? 1 : 2).sum();
            assertEquals(n, people);
        }
    }
}
