package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.Rating;
import io.github.aubergineux.riposte.ranked.Division;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.entity.Player;

import java.util.List;

/** Kit picker for the unranked or ranked queue, with live queue and fight counts. */
public final class QueueMenu extends Menu {

    private final RipostePlugin plugin;
    private final boolean ranked;
    private final List<Kit> kits;

    public QueueMenu(RipostePlugin plugin, Player viewer, boolean ranked) {
        this(plugin, viewer, ranked, ranked ? plugin.kits().ranked() : plugin.kits().enabled());
    }

    private QueueMenu(RipostePlugin plugin, Player viewer, boolean ranked, List<Kit> kits) {
        super(viewer, rowsFor(kits.size()), plugin.menus().title(ranked ? "queue-ranked" : "queue-unranked"));
        this.plugin = plugin;
        this.ranked = ranked;
        this.kits = kits;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        String path = ranked ? "queue-ranked" : "queue-unranked";
        PlayerProfile profile = plugin.profiles().get(viewer);
        int[] slots = innerSlots(size() / 9);
        for (int i = 0; i < kits.size() && i < slots.length; i++) {
            Kit kit = kits.get(i);
            int queued = plugin.queues().size(kit, ranked);
            int fighting = plugin.matches().fighting(kit);
            Rating rating = profile == null ? null : profile.rating(kit.id(), plugin.settings().startingElo);
            int elo = rating == null ? plugin.settings().startingElo : rating.elo;
            Division division = plugin.ranked().division(elo);
            var item = Items.of(kit.icon())
                    .amount(Math.max(1, Math.min(64, queued)))
                    .name(m.messages().formatItem(m.string(path + ".kit.name", "<kit>"), Text.mm("kit", kit.displayName())))
                    .lore(m.lore(m.lines(path + ".kit.lore"),
                            Text.mm("kit", kit.displayName()),
                            Text.p("description", kit.description()),
                            Text.p("queued", queued),
                            Text.p("fighting", fighting),
                            Text.p("elo", elo),
                            Text.mm("division", division.formatted())))
                    .glow(queued > 0)
                    .hideAll()
                    .build();
            set(slots[i], item, click -> {
                viewer.closeInventory();
                plugin.queues().join(viewer, kit, ranked);
            });
        }
        if (kits.isEmpty()) {
            set(13, m.item("common.empty"));
        }
        border(m.border());
    }

    @Override
    public void tick() {
        redraw();
    }
}
