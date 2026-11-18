package io.github.aubergineux.riposte.match;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.config.Messages;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Builds the end-of-match chat summary with clickable inventory previews. */
final class MatchSummary {

    private MatchSummary() {
    }

    static void send(RipostePlugin plugin, Match match) {
        List<Component> lines = build(plugin, match);
        for (Player player : match.audience()) {
            for (Component line : lines) {
                player.sendMessage(line);
            }
        }
    }

    private static List<Component> build(RipostePlugin plugin, Match match) {
        Messages m = plugin.messages();
        List<Component> lines = new ArrayList<>();
        lines.add(m.get("summary.header"));
        lines.add(m.get("summary.title", Text.mm("kit", match.kit().displayName()),
                Text.p("duration", Text.formatDuration(match.duration())),
                Text.p("type", m.raw("summary.types." + match.type().name().toLowerCase(java.util.Locale.ROOT)))));
        MatchTeam winner = match.winner();
        if (winner == null) {
            lines.add(m.get("summary.draw"));
        } else {
            lines.add(m.get("summary.winner", Text.p("names", winner.names()),
                    Text.p("score", match.kit().rules().roundsToWin > 1 ? plugin.matches().scoreLine(match) : "")));
        }
        lines.add(Component.empty());
        // winners first
        List<Participant> ordered = new ArrayList<>(match.participants());
        ordered.sort((a, b) -> Boolean.compare(b.team() == winner, a.team() == winner));
        for (Participant p : ordered) {
            lines.add(playerLine(plugin, match, p, winner != null && p.team() == winner));
        }
        lines.add(Component.empty());
        lines.add(m.get("summary.hint"));
        lines.add(m.get("summary.footer"));
        return lines;
    }

    private static Component playerLine(RipostePlugin plugin, Match match, Participant p, boolean won) {
        Messages m = plugin.messages();
        InventorySnapshot snap = p.snapshot();
        MatchStats s = p.stats();
        Component name = Component.text(p.name());
        if (snap != null) {
            name = name.clickEvent(ClickEvent.runCommand("/riposte inventory " + snap.id()))
                    .hoverEvent(HoverEvent.showText(m.get("summary.hover", Text.p("name", p.name()))));
        }
        String health = snap == null || p.disconnected() ? "0" : Text.hearts(snap.health());
        Component potions = s.potionsUsed > 0
                ? m.get("summary.potions", Text.p("count", s.potionsUsed), Text.p("accuracy", s.potionAccuracy()))
                : Component.empty();
        Component elo = Component.empty();
        if (match.type().ranked() && p.eloAfter != 0) {
            int delta = p.eloAfter - p.eloBefore;
            elo = m.get(delta >= 0 ? "summary.elo-gain" : "summary.elo-loss", Text.p("before", p.eloBefore),
                    Text.p("after", p.eloAfter), Text.p("delta", (delta >= 0 ? "+" : "") + delta));
        }
        var rules = match.kit().rules();
        String key = rules.winCondition == io.github.aubergineux.riposte.kit.KitRules.WinCondition.BRIDGE ? "summary.player-bridge"
                : rules.damageDisabled() ? "summary.player-no-damage" : "summary.player";
        return m.get(key,
                Text.c("name", name),
                Text.mm("icon", m.raw(won ? "summary.icon-win" : "summary.icon-loss")),
                Text.p("health", health),
                Text.p("hits", s.hits),
                Text.p("combo", s.longestCombo),
                Text.p("damage", Text.hearts(s.damageDealt)),
                Text.p("goals", s.goals),
                Text.p("kills", s.kills),
                Text.c("potions", potions),
                Text.c("elo", elo));
    }
}
