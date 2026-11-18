package io.github.aubergineux.riposte.scoreboard;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * One player's sidebar. Lines are scores with a custom display name and a hidden number,
 * so no team prefix tricks are needed and only changed lines are re-sent.
 */
final class Sidebar {

    private static final int MAX_LINES = 15;

    private final Scoreboard board;
    private final Objective objective;
    private Objective health;
    private final List<Component> current = new ArrayList<>();
    private Component title = Component.empty();

    Sidebar(Player player) {
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        this.objective = board.registerNewObjective("riposte", Criteria.DUMMY, Component.empty());
        objective.numberFormat(NumberFormat.blank());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
    }

    Scoreboard board() {
        return board;
    }

    void title(Component title) {
        if (!Objects.equals(this.title, title)) {
            this.title = title;
            objective.displayName(title);
        }
    }

    void lines(List<Component> lines) {
        int count = Math.min(MAX_LINES, lines.size());
        for (int i = 0; i < count; i++) {
            Component line = lines.get(i);
            if (i < current.size() && Objects.equals(current.get(i), line)) {
                continue;
            }
            Score score = objective.getScore(entry(i));
            score.setScore(MAX_LINES - i);
            score.customName(line);
            if (i < current.size()) {
                current.set(i, line);
            } else {
                current.add(line);
            }
        }
        for (int i = current.size() - 1; i >= count; i--) {
            board.resetScores(entry(i));
            current.remove(i);
        }
    }

    void visible(boolean visible) {
        objective.setDisplaySlot(visible ? DisplaySlot.SIDEBAR : null);
    }

    /** Shows health under names during fights. Values are pushed manually so they are right from the first tick. */
    void health(Component label, Collection<Player> targets) {
        if (label == null) {
            if (health != null) {
                health.unregister();
                health = null;
            }
            return;
        }
        if (health == null) {
            health = board.registerNewObjective("riposte_hp", Criteria.DUMMY, label);
            health.setDisplaySlot(DisplaySlot.BELOW_NAME);
        }
        for (Player target : targets) {
            health.getScore(target.getName()).setScore((int) Math.ceil(target.getHealth()));
        }
    }

    /** Colors nametags: index 0 red, 1 blue. Empty lists clear teams. */
    void teams(List<List<String>> teams, List<net.kyori.adventure.text.format.NamedTextColor> colors) {
        for (int i = 0; i < 8; i++) {
            Team team = board.getTeam("riposte_t" + i);
            if (i >= teams.size()) {
                if (team != null) {
                    team.unregister();
                }
                continue;
            }
            if (team == null) {
                team = board.registerNewTeam("riposte_t" + i);
            }
            team.color(colors.get(i % colors.size()));
            team.setAllowFriendlyFire(true);
            for (String entry : new ArrayList<>(team.getEntries())) {
                if (!teams.get(i).contains(entry)) {
                    team.removeEntry(entry);
                }
            }
            for (String name : teams.get(i)) {
                if (!team.hasEntry(name)) {
                    team.addEntry(name);
                }
            }
        }
    }

    private static String entry(int index) {
        return "#rp" + index;
    }
}
