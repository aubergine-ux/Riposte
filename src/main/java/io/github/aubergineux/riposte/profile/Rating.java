package io.github.aubergineux.riposte.profile;

/** Ranked rating for one player, kit and season. */
public final class Rating {

    public int elo;
    public int peak;
    public int wins;
    public int losses;

    public Rating(int elo) {
        this.elo = elo;
        this.peak = elo;
    }

    public int games() {
        return wins + losses;
    }
}
