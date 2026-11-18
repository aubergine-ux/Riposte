package io.github.aubergineux.riposte.profile;

/** Per-player toggles from the settings menu. */
public final class PlayerSettings {

    public enum TimeOfDay {
        DAY(6000), SUNSET(12500), NIGHT(18000), SUNRISE(23500), SERVER(-1);

        public final long ticks;

        TimeOfDay(long ticks) {
            this.ticks = ticks;
        }

        public TimeOfDay next() {
            TimeOfDay[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static TimeOfDay parse(String s) {
            try {
                return valueOf(s);
            } catch (IllegalArgumentException | NullPointerException e) {
                return DAY;
            }
        }
    }

    public boolean duelRequests = true;
    public boolean scoreboard = true;
    public boolean allowSpectators = true;
    public TimeOfDay time = TimeOfDay.DAY;

    public int pack() {
        return (duelRequests ? 1 : 0) | (scoreboard ? 2 : 0) | (allowSpectators ? 4 : 0);
    }

    public void unpack(int flags) {
        duelRequests = (flags & 1) != 0;
        scoreboard = (flags & 2) != 0;
        allowSpectators = (flags & 4) != 0;
    }
}
