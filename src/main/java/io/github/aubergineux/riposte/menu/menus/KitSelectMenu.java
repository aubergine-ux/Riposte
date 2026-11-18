package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Consumer;

/** Generic kit picker used by the kit editor, duels, party fights and events. */
public final class KitSelectMenu extends Menu {

    public enum Purpose {
        EDIT("kit-select-editor"), DUEL("kit-select-duel"), PARTY("kit-select-party"), EVENT("kit-select-event");

        final String path;

        Purpose(String path) {
            this.path = path;
        }
    }

    private final RipostePlugin plugin;
    private final Purpose purpose;
    private final Consumer<Kit> onPick;
    private final List<Kit> kits;

    public KitSelectMenu(RipostePlugin plugin, Player viewer, Purpose purpose, Consumer<Kit> onPick) {
        this(plugin, viewer, purpose, onPick, plugin.kits().enabled());
    }

    private KitSelectMenu(RipostePlugin plugin, Player viewer, Purpose purpose, Consumer<Kit> onPick, List<Kit> kits) {
        super(viewer, rowsFor(kits.size()), plugin.menus().title(purpose.path));
        this.plugin = plugin;
        this.purpose = purpose;
        this.onPick = onPick;
        this.kits = kits;
    }

    @Override
    protected void draw() {
        var m = plugin.menus();
        int[] slots = innerSlots(size() / 9);
        for (int i = 0; i < kits.size() && i < slots.length; i++) {
            Kit kit = kits.get(i);
            boolean custom = purpose == Purpose.EDIT && plugin.profiles().get(viewer) != null
                    && plugin.profiles().get(viewer).layout(kit.id()) != null;
            var item = Items.of(kit.icon())
                    .name(m.messages().formatItem(m.string(purpose.path + ".kit.name", "<kit>"), Text.mm("kit", kit.displayName())))
                    .lore(m.lore(m.lines(purpose.path + ".kit.lore"), Text.mm("kit", kit.displayName()),
                            Text.p("description", kit.description()),
                            Text.mm("layout", m.string(custom ? "kit-select-editor.custom" : "kit-select-editor.default", ""))))
                    .hideAll()
                    .build();
            set(slots[i], item, click -> {
                viewer.closeInventory();
                onPick.accept(kit);
            });
        }
        border(m.border());
    }
}
