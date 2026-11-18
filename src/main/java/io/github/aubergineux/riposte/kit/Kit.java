package io.github.aubergineux.riposte.kit;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * A loadout plus its rules. Contents use the player inventory layout:
 * 0-8 hotbar, 9-35 storage, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand.
 */
public final class Kit {

    public static final int SIZE = 41;
    public static final int ARMOR_START = 36;
    public static final int OFFHAND = 40;

    private final String id;
    private String displayName;
    private Material icon;
    private String description;
    private boolean enabled = true;
    private int order;
    private ItemStack[] contents = new ItemStack[SIZE];
    private final List<PotionEffect> effects = new ArrayList<>();
    private final KitRules rules = new KitRules();

    public Kit(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName == null ? id : displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public Material icon() {
        return icon == null ? Material.IRON_SWORD : icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public String description() {
        return description == null ? "" : description;
    }

    public void description(String description) {
        this.description = description;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int order() {
        return order;
    }

    public void order(int order) {
        this.order = order;
    }

    public ItemStack[] contents() {
        return contents;
    }

    public void contents(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[SIZE];
        for (int i = 0; i < Math.min(SIZE, contents.length); i++) {
            copy[i] = contents[i] == null || contents[i].isEmpty() ? null : contents[i].clone();
        }
        this.contents = copy;
    }

    public List<PotionEffect> effects() {
        return effects;
    }

    public KitRules rules() {
        return rules;
    }

    /** Clears the player and equips this kit, applying the player's saved layout when it is still valid. */
    public void apply(Player player, int[] layout) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        ItemStack[] arranged = KitLayouts.arrange(contents, layout);
        for (int i = 0; i < SIZE; i++) {
            ItemStack item = arranged[i];
            inv.setItem(i, item == null ? null : item.clone());
        }
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }
        player.updateInventory();
    }
}
