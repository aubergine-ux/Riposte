package io.github.aubergineux.riposte.kit;

import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.util.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads and saves kits.yml. */
public final class KitManager {

    private final JavaPlugin plugin;
    private final YamlFile file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "kits.yml", false);
    }

    public void load() {
        boolean fresh = !file.file().exists();
        YamlConfiguration yaml = file.load();
        kits.clear();
        if (fresh) {
            for (Kit kit : DefaultKits.create()) {
                kits.put(kit.id(), kit);
            }
            save();
            plugin.getLogger().info("Created kits.yml with " + kits.size() + " built-in kits");
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("kits");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            try {
                kits.put(id.toLowerCase(Locale.ROOT), read(id.toLowerCase(Locale.ROOT), s));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Skipping kit '" + id + "': " + e.getMessage());
            }
        }
    }

    private Kit read(String id, ConfigurationSection s) {
        Kit kit = new Kit(id);
        kit.displayName(s.getString("display-name", id));
        kit.icon(Items.material(s.getString("icon"), org.bukkit.Material.IRON_SWORD));
        kit.description(s.getString("description", ""));
        kit.enabled(s.getBoolean("enabled", true));
        kit.order(s.getInt("order", kits.size()));
        kit.rules().load(s.getConfigurationSection("rules"));
        kit.contents(Items.decode(s.getString("contents"), Kit.SIZE));
        for (String line : s.getStringList("effects")) {
            String[] parts = line.split(":");
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Unknown effect '" + parts[0] + "' in kit " + id);
                continue;
            }
            int amplifier = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            kit.effects().add(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false));
        }
        return kit;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Riposte kits. Edit rules here and run /riposte reload.",
                "To change items, put them in your inventory and run /kit setinv <kit>.",
                "win-condition: ELIMINATION, SUMO, BOXING or BRIDGE.",
                "arena-tag: the kit only uses arenas that carry this tag."));
        for (Kit kit : sorted()) {
            ConfigurationSection s = yaml.createSection("kits." + kit.id());
            s.set("display-name", kit.displayName());
            s.set("icon", kit.icon().name());
            s.set("description", kit.description());
            s.set("enabled", kit.enabled());
            s.set("order", kit.order());
            kit.rules().save(s.createSection("rules"));
            List<String> effects = new ArrayList<>();
            for (PotionEffect effect : kit.effects()) {
                effects.add(effect.getType().getKey().getKey() + ":" + effect.getAmplifier());
            }
            s.set("effects", effects);
            s.set("contents", Items.encode(kit.contents()));
        }
        try {
            file.file().getParentFile().mkdirs();
            yaml.save(file.file());
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save kits.yml: " + e.getMessage());
        }
    }

    public Kit get(String id) {
        return id == null ? null : kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        return kits.values();
    }

    public List<Kit> sorted() {
        List<Kit> list = new ArrayList<>(kits.values());
        list.sort(Comparator.comparingInt((Kit k) -> k.order()).thenComparing(k -> k.id()));
        return list;
    }

    public List<Kit> enabled() {
        return sorted().stream().filter(Kit::enabled).toList();
    }

    public List<Kit> ranked() {
        return enabled().stream().filter(k -> k.rules().ranked).toList();
    }

    public Kit create(String id) {
        Kit kit = new Kit(id.toLowerCase(Locale.ROOT));
        kit.order(kits.size());
        kits.put(kit.id(), kit);
        save();
        return kit;
    }

    public boolean delete(String id) {
        boolean removed = kits.remove(id.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    /** Restores a built-in kit to its original items and rules. */
    public boolean restoreDefault(String id) {
        Kit def = DefaultKits.byId(id);
        if (def == null) {
            return false;
        }
        Kit existing = kits.get(def.id());
        if (existing != null) {
            def.order(existing.order());
        }
        kits.put(def.id(), def);
        save();
        return true;
    }
}
