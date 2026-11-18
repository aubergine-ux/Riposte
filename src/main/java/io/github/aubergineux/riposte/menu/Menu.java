package io.github.aubergineux.riposte.menu;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base chest menu. Subclasses fill buttons in {@link #draw()}, which is called on open and on refresh.
 * Every click in the top inventory is cancelled; bottom inventory clicks are cancelled unless allowed.
 */
public abstract class Menu implements InventoryHolder {

    public record Click(Player player, ClickType type, InventoryClickEvent event) {
        public boolean right() {
            return type.isRightClick();
        }

        public boolean shift() {
            return type.isShiftClick();
        }
    }

    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<Click>> actions = new HashMap<>();

    protected Menu(Player viewer, int rows, Component title) {
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, title);
    }

    protected abstract void draw();

    public void open() {
        redraw();
        viewer.openInventory(inventory);
    }

    public void redraw() {
        inventory.clear();
        actions.clear();
        draw();
    }

    /** Called every second while open. Override to update live values such as queue counts. */
    public void tick() {
    }

    public void onClose() {
    }

    /** Return true to let the player move items in their own inventory while this menu is open. */
    public boolean allowBottomClicks() {
        return false;
    }

    public void onBottomClick(InventoryClickEvent event) {
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected void set(int slot, ItemStack item, Consumer<Click> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action != null) {
            actions.put(slot, action);
        } else {
            actions.remove(slot);
        }
    }

    protected void fill(ItemStack item) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, item);
            }
        }
    }

    protected void border(ItemStack item) {
        int size = inventory.getSize();
        for (int i = 0; i < size; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == size / 9 - 1 || col == 0 || col == 8) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, item);
                }
            }
        }
    }

    void handle(InventoryClickEvent event) {
        Consumer<Click> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(new Click(viewer, event.getClick(), event));
        }
    }

    public Player viewer() {
        return viewer;
    }

    public int size() {
        return inventory.getSize();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Slots of the inner area of a bordered menu, left to right, top to bottom. */
    public static int[] innerSlots(int rows) {
        int[] slots = new int[(rows - 2) * 7];
        int i = 0;
        for (int row = 1; row < rows - 1; row++) {
            for (int col = 1; col < 8; col++) {
                slots[i++] = row * 9 + col;
            }
        }
        return slots;
    }

    /** Rows needed to show {@code count} items inside a border, between 3 and 6. */
    public static int rowsFor(int count) {
        return Math.max(3, Math.min(6, 2 + (count + 6) / 7));
    }
}
