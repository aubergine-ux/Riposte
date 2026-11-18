package io.github.aubergineux.riposte.profile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Everything Riposte keeps in memory for an online player. */
public final class PlayerProfile {

    private final UUID uuid;
    private String name;
    private final PlayerSettings settings = new PlayerSettings();
    private final Map<String, KitStats> stats = new HashMap<>();
    private final Map<String, Rating> ratings = new HashMap<>();
    private final Map<String, int[]> layouts = new HashMap<>();
    private volatile PlayerState state = PlayerState.LOBBY;
    private boolean buildMode;

    /** Last opponent and kit, used by the rematch button. */
    public UUID rematchTarget;
    public String rematchKit;
    public String rematchArena;
    public long rematchExpires;

    public PlayerProfile(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name;
    }

    public PlayerSettings settings() {
        return settings;
    }

    public KitStats stats(String kit) {
        return stats.computeIfAbsent(kit, k -> new KitStats());
    }

    public Map<String, KitStats> allStats() {
        return stats;
    }

    public Rating rating(String kit, int startingElo) {
        return ratings.computeIfAbsent(kit, k -> new Rating(startingElo));
    }

    public Map<String, Rating> allRatings() {
        return ratings;
    }

    public int[] layout(String kit) {
        return layouts.get(kit);
    }

    public void layout(String kit, int[] layout) {
        if (layout == null) {
            layouts.remove(kit);
        } else {
            layouts.put(kit, layout);
        }
    }

    public Map<String, int[]> layouts() {
        return layouts;
    }

    public PlayerState state() {
        return state;
    }

    public void state(PlayerState state) {
        this.state = state;
    }

    public boolean buildMode() {
        return buildMode;
    }

    public void buildMode(boolean buildMode) {
        this.buildMode = buildMode;
    }
}
