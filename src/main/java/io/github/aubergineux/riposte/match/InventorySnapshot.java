package io.github.aubergineux.riposte.match;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A frozen copy of a player's inventory and state at the end of their fight. */
public final class InventorySnapshot {

    private final UUID id = UUID.randomUUID();
    private final UUID owner;
    private final String ownerName;
    private final ItemStack[] contents;
    private final double health;
    private final double maxHealth;
    private final int food;
    private final List<PotionEffect> effects;
    private final MatchStats stats;
    private final String kit;
    private final long created = System.currentTimeMillis();

    public InventorySnapshot(Player player, MatchStats stats, String kit) {
        this.owner = player.getUniqueId();
        this.ownerName = player.getName();
        ItemStack[] raw = player.getInventory().getContents();
        this.contents = new ItemStack[raw.length];
        for (int i = 0; i < raw.length; i++) {
            contents[i] = raw[i] == null ? null : raw[i].clone();
        }
        this.health = Math.max(0, player.getHealth());
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        this.maxHealth = attribute == null ? 20 : attribute.getValue();
        this.food = player.getFoodLevel();
        this.effects = new ArrayList<>(player.getActivePotionEffects());
        this.stats = stats;
        this.kit = kit;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public ItemStack[] contents() {
        return contents;
    }

    public double health() {
        return health;
    }

    public double maxHealth() {
        return maxHealth;
    }

    public int food() {
        return food;
    }

    public List<PotionEffect> effects() {
        return effects;
    }

    public MatchStats stats() {
        return stats;
    }

    public String kit() {
        return kit;
    }

    public long created() {
        return created;
    }

    public int healingPotionsLeft() {
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && (item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION)
                    && item.getItemMeta() instanceof PotionMeta meta) {
                PotionType type = meta.getBasePotionType();
                if (type == PotionType.HEALING || type == PotionType.STRONG_HEALING) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }
}
