package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.ranked.Division;
import io.github.aubergineux.riposte.ranked.RankedManager;
import io.github.aubergineux.riposte.storage.DataStore;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Top players per kit and globally. Clicking a board prints it to chat. */
public final class LeaderboardMenu extends Menu {

    private final RipostePlugin plugin;
    private final List<Kit> kits;

    public LeaderboardMenu(RipostePlugin plugin, Player viewer) {
        this(plugin, viewer, plugin.kits().ranked());
    }

    private LeaderboardMenu(RipostePlugin plugin, Player viewer, List<Kit> kits) {
        super(viewer, rowsFor(kits.size() + 1) + (kits.size() + 1 > 7 * (rowsFor(kits.size() + 1) - 2) ? 1 : 0),
                plugin.menus().title("leaderboards", Text.p("season", plugin.ranked().season().name())));
        this.plugin = plugin;
        this.kits = kits;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        int[] slots = innerSlots(size() / 9);
        set(slots[0], board(RankedManager.GLOBAL, Material.NETHER_STAR, m.messages().raw("leaderboard.global-name")),
                click -> printChat(plugin, viewer, RankedManager.GLOBAL));
        for (int i = 0; i < kits.size() && i + 1 < slots.length; i++) {
            Kit kit = kits.get(i);
            set(slots[i + 1], board(kit.id(), kit.icon(), kit.displayName()), click -> printChat(plugin, viewer, kit.id()));
        }
        border(m.border());
    }

    private org.bukkit.inventory.ItemStack board(String key, Material icon, String name) {
        var m = plugin.menus();
        List<Component> lore = new ArrayList<>();
        List<DataStore.LeaderboardEntry> entries = plugin.ranked().board(key);
        for (String line : m.lines("leaderboards.board.lore-header")) {
            lore.add(m.messages().formatItem(line, Text.p("season", plugin.ranked().season().name())));
        }
        if (entries.isEmpty()) {
            lore.add(m.messages().formatItem(m.string("leaderboards.board.empty", "<gray>No ranked games yet")));
        }
        for (int i = 0; i < entries.size(); i++) {
            DataStore.LeaderboardEntry e = entries.get(i);
            Division division = plugin.ranked().division(e.elo());
            lore.add(m.messages().formatItem(m.string("leaderboards.board.line", "<rank>. <name> <elo>"),
                    Text.p("rank", i + 1), Text.p("name", e.name()), Text.p("elo", e.elo()),
                    Text.mm("rank_color", m.messages().raw("leaderboard.rank-colors." + Math.min(4, i + 1))),
                    Text.mm("division", division.formatted()), Text.mm("division_color", division.color())));
        }
        for (String line : m.lines("leaderboards.board.lore-footer")) {
            lore.add(m.messages().formatItem(line));
        }
        return Items.of(icon).name(m.messages().formatItem(m.string("leaderboards.board.name", "<board>"), Text.mm("board", name)))
                .lore(lore).hideAll().build();
    }

    /** Chat version of a leaderboard, also used by /leaderboard <kit>. */
    public static void printChat(RipostePlugin plugin, Player viewer, String key) {
        var msg = plugin.messages();
        String name;
        if (key.equals(RankedManager.GLOBAL)) {
            name = msg.raw("leaderboard.global-name");
        } else {
            Kit kit = plugin.kits().get(key);
            name = kit == null ? key : kit.displayName();
        }
        msg.send(viewer, "leaderboard.chat-header", Text.mm("board", name), Text.p("season", plugin.ranked().season().name()));
        List<DataStore.LeaderboardEntry> entries = plugin.ranked().board(key);
        if (entries.isEmpty()) {
            msg.send(viewer, "leaderboard.chat-empty");
        }
        for (int i = 0; i < entries.size(); i++) {
            DataStore.LeaderboardEntry e = entries.get(i);
            Division division = plugin.ranked().division(e.elo());
            msg.send(viewer, "leaderboard.chat-line", Text.p("rank", i + 1), Text.p("name", e.name()), Text.p("elo", e.elo()),
                    Text.p("wins", e.wins()), Text.p("losses", e.losses()),
                    Text.mm("rank_color", msg.raw("leaderboard.rank-colors." + Math.min(4, i + 1))),
                    Text.mm("division", division.formatted()));
        }
        msg.send(viewer, "leaderboard.chat-footer");
    }
}
