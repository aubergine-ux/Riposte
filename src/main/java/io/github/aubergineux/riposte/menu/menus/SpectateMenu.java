package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.Participant;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Live matches that can be spectated. */
public final class SpectateMenu extends Menu {

    private final RipostePlugin plugin;

    public SpectateMenu(RipostePlugin plugin, Player viewer) {
        super(viewer, 6, plugin.menus().title("spectate"));
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        int[] slots = innerSlots(6);
        List<Match> live = new ArrayList<>();
        for (Match match : plugin.matches().all()) {
            if (match.state() != Match.State.ENDED) {
                live.add(match);
            }
        }
        if (live.isEmpty()) {
            set(22, m.item("spectate.empty"));
        }
        for (int i = 0; i < live.size() && i < slots.length; i++) {
            Match match = live.get(i);
            Participant first = match.teams().getFirst().leader();
            List<String> health = new ArrayList<>();
            for (Participant p : match.participants()) {
                Player player = p.player();
                health.add(m.string("spectate.match.health-line", "<name> <health>")
                        .replace("<name>", p.name())
                        .replace("<health>", player == null || !p.alive() ? "0" : Text.hearts(player.getHealth())));
            }
            var lore = new ArrayList<>(m.lore(m.lines("spectate.match.lore"), Text.mm("kit", match.kit().displayName()),
                    Text.mm("arena", match.arena().arena().displayName()), Text.p("duration", Text.formatDuration(match.duration())),
                    Text.p("spectators", match.spectators().size()),
                    Text.p("type", plugin.messages().raw("summary.types." + match.type().name().toLowerCase(java.util.Locale.ROOT)))));
            for (String line : health) {
                lore.add(plugin.messages().formatItem(line));
            }
            lore.addAll(m.lore(m.lines("spectate.match.lore-footer")));
            var item = Items.of(Material.PLAYER_HEAD)
                    .skull(Bukkit.getOfflinePlayer(first.uuid()))
                    .name(m.messages().formatItem(m.string("spectate.match.name", "<title>"), Text.p("title", match.title())))
                    .lore(lore)
                    .hideAll()
                    .build();
            set(slots[i], item, click -> {
                viewer.closeInventory();
                String error = plugin.spectators().spectate(viewer, match);
                if (error != null) {
                    plugin.messages().send(viewer, error);
                }
            });
        }
        border(m.border());
    }

    @Override
    public void tick() {
        redraw();
    }
}
