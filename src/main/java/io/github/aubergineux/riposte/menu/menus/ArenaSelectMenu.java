package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.Arena;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Arena picker for duels. The first entry picks a random arena. */
public final class ArenaSelectMenu extends Menu {

    private final RipostePlugin plugin;
    private final Kit kit;
    private final Consumer<String> onPick;
    private final List<Arena> arenas;

    public ArenaSelectMenu(RipostePlugin plugin, Player viewer, Kit kit, Consumer<String> onPick) {
        this(plugin, viewer, kit, onPick, plugin.arenas().arenasFor(kit));
    }

    private ArenaSelectMenu(RipostePlugin plugin, Player viewer, Kit kit, Consumer<String> onPick, List<Arena> arenas) {
        super(viewer, rowsFor(arenas.size() + 1), plugin.menus().title("arena-select", Text.mm("kit", kit.displayName())));
        this.plugin = plugin;
        this.kit = kit;
        this.onPick = onPick;
        this.arenas = arenas;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        int[] slots = innerSlots(size() / 9);
        set(slots[0], m.item("arena-select.random"), click -> {
            viewer.closeInventory();
            onPick.accept(null);
        });
        for (int i = 0; i < arenas.size() && i + 1 < slots.length; i++) {
            Arena arena = arenas.get(i);
            var item = Items.of(arena.icon())
                    .name(m.messages().formatItem(m.string("arena-select.arena.name", "<arena>"), Text.mm("arena", arena.displayName())))
                    .lore(m.lore(m.lines("arena-select.arena.lore"), Text.p("free", arena.available()),
                            Text.p("copies", arena.copies().size()), Text.mm("kit", kit.displayName())))
                    .hideAll()
                    .build();
            set(slots[i + 1], item, click -> {
                viewer.closeInventory();
                onPick.accept(arena.name());
            });
        }
        border(m.border());
    }

    @Override
    public void tick() {
        redraw();
    }
}
