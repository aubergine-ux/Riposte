package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.match.InventorySnapshot;
import io.github.aubergineux.riposte.match.MatchStats;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/** Read-only view of a player's inventory at the end of a match, linked from the chat summary. */
public final class InventoryPreviewMenu extends Menu {

    private final RipostePlugin plugin;
    private final InventorySnapshot snapshot;

    public InventoryPreviewMenu(RipostePlugin plugin, Player viewer, InventorySnapshot snapshot) {
        super(viewer, 6, plugin.menus().title("inventory-preview", Text.p("player", snapshot.ownerName())));
        this.plugin = plugin;
        this.snapshot = snapshot;
    }

    @Override
    protected void draw() {
        ItemStack[] c = snapshot.contents();
        // storage rows first, hotbar on row four, like the real inventory
        for (int i = 9; i < 36 && i < c.length; i++) {
            set(i - 9, c[i]);
        }
        for (int i = 0; i < 9 && i < c.length; i++) {
            set(27 + i, c[i]);
        }
        // armor and offhand
        for (int i = 0; i < 4 && 39 - i < c.length; i++) {
            set(36 + i, c[39 - i]);
        }
        if (c.length > 40) {
            set(41, c[40]);
        }
        var m = plugin.menus();
        MatchStats s = snapshot.stats();
        set(45, m.item("inventory-preview.health", Text.p("health", Text.hearts(snapshot.health())),
                Text.p("max", Text.hearts(snapshot.maxHealth()))));
        set(46, m.item("inventory-preview.food", Text.p("food", snapshot.food())));
        set(47, m.item("inventory-preview.potions", Text.p("left", snapshot.healingPotionsLeft()), Text.p("used", s.potionsUsed),
                Text.p("accuracy", s.potionAccuracy())));
        List<Component> effects = new ArrayList<>();
        for (PotionEffect effect : snapshot.effects()) {
            effects.add(plugin.messages().formatItem(m.string("inventory-preview.effects.line", "<effect> <level> <time>"),
                    Text.p("effect", Text.capitalize(effect.getType().getKey().getKey())),
                    Text.p("level", effect.getAmplifier() + 1),
                    Text.p("time", effect.isInfinite() ? "∞" : Text.formatDuration(effect.getDuration() * 50L))));
        }
        if (effects.isEmpty()) {
            effects.add(plugin.messages().formatItem(m.string("inventory-preview.effects.none", "<gray>None")));
        }
        set(48, Items.of(m.item("inventory-preview.effects")).lore(effects).build());
        set(50, m.item("inventory-preview.stats", Text.p("hits", s.hits), Text.p("combo", s.longestCombo),
                Text.p("damage", Text.hearts(s.damageDealt)), Text.p("taken", Text.hearts(s.damageTaken)),
                Text.p("crits", s.criticalHits), Text.p("kills", s.kills)));
        set(53, m.item("common.close"), click -> viewer.closeInventory());
        for (int i = 36; i < 54; i++) {
            if (getInventory().getItem(i) == null) {
                set(i, m.filler());
            }
        }
    }

    static boolean isAir(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }
}
