package io.github.aubergineux.riposte.profile;

/** Lifetime stats for one player in one kit (ranked and unranked combined). */
public final class KitStats {

    public int wins;
    public int losses;
    public int kills;
    public int deaths;
    public int streak;
    public int bestStreak;

    public void recordWin(int kills) {
        wins++;
        this.kills += kills;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
    }

    public void recordLoss(int kills, int deaths) {
        losses++;
        this.kills += kills;
        this.deaths += deaths;
        streak = 0;
    }

    public int games() {
        return wins + losses;
    }
}
