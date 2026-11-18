package io.github.aubergineux.riposte.ranked;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EloCalculatorTest {

    private final EloCalculator elo = new EloCalculator(32, 48, 10, 100);

    @Test
    void expectedScoreIsHalfForEqualRatings() {
        assertEquals(0.5, EloCalculator.expected(1000, 1000), 1e-9);
    }

    @Test
    void expectedScoresAreComplementary() {
        double a = EloCalculator.expected(1200, 1000);
        double b = EloCalculator.expected(1000, 1200);
        assertEquals(1.0, a + b, 1e-9);
        assertEquals(0.7597, a, 1e-4);
    }

    @Test
    void equalPlayersSwapHalfTheKFactor() {
        EloCalculator.Outcome o = elo.calculate(1000, 50, 1000, 50);
        assertEquals(1016, o.winnerNew());
        assertEquals(984, o.loserNew());
        assertEquals(16, o.winnerDelta());
        assertEquals(-16, o.loserDelta());
    }

    @Test
    void upsetWinsMoreThanExpectedWin() {
        EloCalculator.Outcome upset = elo.calculate(1000, 50, 1400, 50);
        EloCalculator.Outcome expected = elo.calculate(1400, 50, 1000, 50);
        assertTrue(upset.winnerDelta() > expected.winnerDelta());
        assertEquals(29, upset.winnerDelta());
        assertEquals(3, expected.winnerDelta());
    }

    @Test
    void zeroSumBetweenEstablishedPlayers() {
        for (int gap = -600; gap <= 600; gap += 37) {
            EloCalculator.Outcome o = elo.calculate(1300 + gap, 100, 1300, 100);
            assertEquals(0, o.winnerDelta() + o.loserDelta(), "gap " + gap);
        }
    }

    @Test
    void provisionalPlayersMoveFaster() {
        EloCalculator.Outcome fresh = elo.calculate(1000, 0, 1000, 50);
        assertEquals(24, fresh.winnerDelta());
        assertEquals(-16, fresh.loserDelta());
        assertEquals(48, elo.kFor(9));
        assertEquals(32, elo.kFor(10));
    }

    @Test
    void winAlwaysGainsAtLeastOnePoint() {
        EloCalculator.Outcome o = elo.calculate(3000, 100, 500, 100);
        assertEquals(1, o.winnerDelta());
        assertEquals(-1, o.loserDelta());
    }

    @Test
    void ratingNeverDropsBelowFloor() {
        EloCalculator.Outcome o = elo.calculate(120, 100, 110, 100);
        assertEquals(100, o.loserNew());
        assertEquals(-10, o.loserDelta());
    }

    @Test
    void softResetMovesTowardStartingRating() {
        assertEquals(1250, EloCalculator.softReset(1500, 1000, 0.5));
        assertEquals(900, EloCalculator.softReset(800, 1000, 0.5));
        assertEquals(1000, EloCalculator.softReset(1700, 1000, 0.0));
    }
}
