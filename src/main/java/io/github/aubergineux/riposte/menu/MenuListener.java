package io.github.aubergineux.riposte.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/** Routes inventory events to open menus and ticks them once per second. */
public final class MenuListener implements Listener {

    public MenuListener(Plugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Inventory top = player.getOpenInventory().getTopInventory();
                if (top.getHolder(false) instanceof Menu menu) {
                    menu.tick();
                }
            }
        }, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Menu menu)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked != null && clicked.equals(top)) {
            event.setCancelled(true);
            menu.handle(event);
            return;
        }
        if (!menu.allowBottomClicks()) {
            event.setCancelled(true);
            return;
        }
        menu.onBottomClick(event);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof Menu menu)) {
            return;
        }
        // a click-and-release on one slot of the player's own inventory arrives as a drag
        if (menu.allowBottomClicks() && event.getRawSlots().size() == 1) {
            int raw = event.getRawSlots().iterator().next();
            int slot = event.getView().convertSlot(raw);
            if (raw >= top.getSize() && (slot < 36 || slot > 39)) {
                return;
            }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu menu) {
            menu.onClose();
        }
    }
}
