package io.github.aubergineux.riposte.ranked;

/**
 * Standard Elo with a larger K factor during a player's first (provisional) games and a rating floor.
 * Pure math, no server types, so it is unit tested directly.
 */
public final class EloCalculator {

    public record Outcome(int winnerNew, int loserNew, int winnerDelta, int loserDelta) {
    }

    private final int kFactor;
    private final int provisionalK;
    private final int provisionalGames;
    private final int floor;

    public EloCalculator(int kFactor, int provisionalK, int provisionalGames, int floor) {
        this.kFactor = kFactor;
        this.provisionalK = provisionalK;
        this.provisionalGames = provisionalGames;
        this.floor = floor;
    }

    /** Probability that a player rated {@code rating} beats one rated {@code opponent}. */
    public static double expected(int rating, int opponent) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponent - rating) / 400.0));
    }

    public int kFor(int gamesPlayed) {
        return gamesPlayed < provisionalGames ? provisionalK : kFactor;
    }

    public Outcome calculate(int winnerElo, int winnerGames, int loserElo, int loserGames) {
        double winnerExpected = expected(winnerElo, loserElo);
        double loserExpected = 1.0 - winnerExpected;
        int winnerGain = (int) Math.round(kFor(winnerGames) * (1.0 - winnerExpected));
        int loserLoss = (int) Math.round(kFor(loserGames) * loserExpected);
        // a win always moves you up and a loss always moves you down, even against a huge gap
        winnerGain = Math.max(1, winnerGain);
        loserLoss = Math.max(1, loserLoss);
        int winnerNew = winnerElo + winnerGain;
        int loserNew = Math.max(floor, loserElo - loserLoss);
        return new Outcome(winnerNew, loserNew, winnerNew - winnerElo, loserNew - loserElo);
    }

    /** Elo after a season reset that keeps part of the distance from the starting rating. */
    public static int softReset(int elo, int starting, double keep) {
        return (int) Math.round(starting + (elo - starting) * keep);
    }
}
