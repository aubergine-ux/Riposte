package io.github.aubergineux.riposte.kit;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Hotbar layout editor. The kit is placed in the player's own inventory and a small menu above it
 * offers save, reset and cancel. Only plain moves are allowed, so a layout can always be computed.
 */
public final class KitEditor {

    private final RipostePlugin plugin;

    public KitEditor(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, Kit kit) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        if (profile.state() != PlayerState.LOBBY) {
            plugin.messages().send(player, "editor.busy");
            return;
        }
        profile.state(PlayerState.EDITING_KIT);
        arrangeInto(player, kit, profile.layout(kit.id()));
        new EditorMenu(player, kit).open();
        plugin.messages().send(player, "editor.opened", Text.mm("kit", kit.displayName()));
    }

    private void arrangeInto(Player player, Kit kit, int[] layout) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ItemStack[] arranged = KitLayouts.arrange(kit.contents(), layout);
        for (int i = 0; i < Kit.SIZE; i++) {
            inv.setItem(i, arranged[i] == null ? null : arranged[i].clone());
        }
    }

    private void finish(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile != null && profile.state() == PlayerState.EDITING_KIT) {
            profile.state(PlayerState.LOBBY);
            player.setItemOnCursor(null);
            plugin.lobby().giveHotbar(player);
        }
    }

    private final class EditorMenu extends Menu {

        private final Kit kit;
        private boolean handled;

        EditorMenu(Player viewer, Kit kit) {
            super(viewer, 3, plugin.menus().title("kit-editor", Text.mm("kit", kit.displayName())));
            this.kit = kit;
        }

        @Override
        protected void draw() {
            var m = plugin.menus();
            set(m.slot("kit-editor.info.slot", 4), m.item("kit-editor.info", kit.icon(), Text.mm("kit", kit.displayName())));
            set(m.slot("kit-editor.save.slot", 11), m.item("kit-editor.save"), click -> save());
            set(m.slot("kit-editor.reset.slot", 13), m.item("kit-editor.reset"), click -> reset());
            set(m.slot("kit-editor.cancel.slot", 15), m.item("kit-editor.cancel"), click -> {
                handled = true;
                viewer.closeInventory();
                plugin.messages().send(viewer, "editor.cancelled");
                finish(viewer);
            });
            fill(m.filler());
        }

        private void save() {
            if (viewer.getItemOnCursor() != null && !viewer.getItemOnCursor().isEmpty()) {
                plugin.messages().send(viewer, "editor.cursor");
                return;
            }
            ItemStack[] contents = viewer.getInventory().getContents();
            int[] layout = KitLayouts.compute(kit.contents(), contents);
            if (layout == null) {
                plugin.messages().send(viewer, "editor.invalid");
                return;
            }
            PlayerProfile profile = plugin.profiles().get(viewer);
            if (profile != null) {
                profile.layout(kit.id(), layout);
                plugin.dataStore().saveLayout(viewer.getUniqueId(), kit.id(), layout);
            }
            handled = true;
            viewer.closeInventory();
            plugin.messages().send(viewer, "editor.saved", Text.mm("kit", kit.displayName()));
            viewer.playSound(net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP,
                    net.kyori.adventure.sound.Sound.Source.MASTER, 0.6f, 1.6f));
            finish(viewer);
        }

        private void reset() {
            PlayerProfile profile = plugin.profiles().get(viewer);
            if (profile != null) {
                profile.layout(kit.id(), null);
                plugin.dataStore().saveLayout(viewer.getUniqueId(), kit.id(), null);
            }
            viewer.setItemOnCursor(null);
            arrangeInto(viewer, kit, null);
            plugin.messages().send(viewer, "editor.reset", Text.mm("kit", kit.displayName()));
        }

        @Override
        public boolean allowBottomClicks() {
            return true;
        }

        @Override
        public void onBottomClick(InventoryClickEvent event) {
            ClickType click = event.getClick();
            int slot = event.getSlot();
            boolean armor = slot >= Kit.ARMOR_START && slot < Kit.OFFHAND;
            boolean allowed = (click == ClickType.LEFT || click == ClickType.NUMBER_KEY || click == ClickType.SWAP_OFFHAND) && !armor;
            if (!allowed) {
                event.setCancelled(true);
            }
        }

        @Override
        public void onClose() {
            if (handled) {
                return;
            }
            // closed with escape: treat as cancel, but only after the inventory is really closed
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!(viewer.getOpenInventory().getTopInventory().getHolder(false) instanceof EditorMenu)) {
                    plugin.messages().send(viewer, "editor.cancelled");
                    finish(viewer);
                }
            });
        }
    }
}
