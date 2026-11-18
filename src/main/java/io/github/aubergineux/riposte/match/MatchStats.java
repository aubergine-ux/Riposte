package io.github.aubergineux.riposte.match;

/** Per-player numbers shown on the scoreboard and in the end-of-match summary. */
public final class MatchStats {

    public int hits;
    public int combo;
    public int longestCombo;
    public double damageDealt;
    public double damageTaken;
    public int potionsUsed;
    public int potionsMissed;
    public int kills;
    public int deaths;
    public int goals;
    public int criticalHits;

    public void landHit(double damage) {
        hits++;
        combo++;
        longestCombo = Math.max(longestCombo, combo);
        damageDealt += damage;
    }

    public void takeHit(double damage) {
        combo = 0;
        damageTaken += damage;
    }

    /** Fraction of thrown healing potions that mostly hit the thrower, as a whole percentage. */
    public int potionAccuracy() {
        if (potionsUsed == 0) {
            return 100;
        }
        return (int) Math.round((potionsUsed - potionsMissed) * 100.0 / potionsUsed);
    }
}
