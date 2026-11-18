package io.github.aubergineux.riposte.kit;

import org.bukkit.configuration.ConfigurationSection;

/** Gameplay switches for a kit. Everything here is editable in kits.yml. */
public final class KitRules {

    public enum WinCondition {
        /** Last team standing wins the round. */
        ELIMINATION,
        /** Knock the opponent into water or off the platform. Damage is disabled. */
        SUMO,
        /** First to land the target number of hits. Damage is disabled. */
        BOXING,
        /** Score by jumping into the opponent's goal. Deaths respawn you. */
        BRIDGE
    }

    public WinCondition winCondition = WinCondition.ELIMINATION;
    public int roundsToWin = 1;
    public int boxingHits = 100;
    public boolean build = false;
    public int buildHeight = 12;
    public boolean hunger = false;
    public boolean naturalRegen = true;
    public boolean fallDamage = true;
    public boolean ranked = true;
    public boolean resetArenaBetweenRounds = false;
    public boolean showHealth = true;
    public int pearlCooldownSeconds = 0;
    /** Damage immunity after a hit, in ticks. Vanilla is 20 (10 for 1.8 style "no hit delay" set lower). */
    public int hitDelayTicks = 20;
    public String arenaTag = "standard";
    public int respawnSeconds = 2;

    public void load(ConfigurationSection s) {
        if (s == null) {
            return;
        }
        try {
            winCondition = WinCondition.valueOf(s.getString("win-condition", winCondition.name()).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }
        roundsToWin = Math.max(1, s.getInt("rounds-to-win", roundsToWin));
        boxingHits = Math.max(1, s.getInt("boxing-hits", boxingHits));
        build = s.getBoolean("build", build);
        buildHeight = s.getInt("build-height", buildHeight);
        hunger = s.getBoolean("hunger", hunger);
        naturalRegen = s.getBoolean("natural-regen", naturalRegen);
        fallDamage = s.getBoolean("fall-damage", fallDamage);
        ranked = s.getBoolean("ranked", ranked);
        resetArenaBetweenRounds = s.getBoolean("reset-arena-between-rounds", resetArenaBetweenRounds);
        showHealth = s.getBoolean("show-health", showHealth);
        pearlCooldownSeconds = s.getInt("pearl-cooldown-seconds", pearlCooldownSeconds);
        hitDelayTicks = Math.max(0, Math.min(20, s.getInt("hit-delay-ticks", hitDelayTicks)));
        arenaTag = s.getString("arena-tag", arenaTag);
        respawnSeconds = Math.max(0, s.getInt("respawn-seconds", respawnSeconds));
    }

    public void save(ConfigurationSection s) {
        s.set("win-condition", winCondition.name());
        s.set("rounds-to-win", roundsToWin);
        s.set("boxing-hits", boxingHits);
        s.set("build", build);
        s.set("build-height", buildHeight);
        s.set("hunger", hunger);
        s.set("natural-regen", naturalRegen);
        s.set("fall-damage", fallDamage);
        s.set("ranked", ranked);
        s.set("reset-arena-between-rounds", resetArenaBetweenRounds);
        s.set("show-health", showHealth);
        s.set("pearl-cooldown-seconds", pearlCooldownSeconds);
        s.set("hit-delay-ticks", hitDelayTicks);
        s.set("arena-tag", arenaTag);
        s.set("respawn-seconds", respawnSeconds);
    }

    public boolean damageDisabled() {
        return winCondition == WinCondition.SUMO || winCondition == WinCondition.BOXING;
    }
}
