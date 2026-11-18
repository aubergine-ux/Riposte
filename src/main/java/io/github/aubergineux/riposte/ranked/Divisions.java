package io.github.aubergineux.riposte.ranked;

import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Loads divisions.yml. Each tier can be split into sub-divisions (III, II, I) automatically. */
public final class Divisions {

    private static final String[] ROMAN = {"I", "II", "III", "IV", "V"};

    private final YamlFile file;
    private final List<Division> divisions = new ArrayList<>();

    public Divisions(JavaPlugin plugin) {
        this.file = new YamlFile(plugin, "divisions.yml", false);
    }

    public void reload() {
        YamlConfiguration yaml = file.load();
        divisions.clear();
        ConfigurationSection root = yaml.getConfigurationSection("divisions");
        if (root != null) {
            List<String> keys = new ArrayList<>(root.getKeys(false));
            List<ConfigurationSection> tiers = new ArrayList<>();
            for (String key : keys) {
                tiers.add(root.getConfigurationSection(key));
            }
            tiers.sort(Comparator.comparingInt(s -> s.getInt("min-elo")));
            for (int t = 0; t < tiers.size(); t++) {
                ConfigurationSection s = tiers.get(t);
                String name = s.getString("name", s.getName());
                String color = s.getString("color", "<white>");
                Material icon = Items.material(s.getString("icon"), Material.IRON_INGOT);
                int min = s.getInt("min-elo");
                int subs = Math.max(1, Math.min(ROMAN.length, s.getInt("sub-divisions", 1)));
                int next = t + 1 < tiers.size() ? tiers.get(t + 1).getInt("min-elo") : -1;
                if (subs == 1 || next < 0) {
                    divisions.add(new Division(name, min, color, icon));
                    continue;
                }
                int step = Math.max(1, (next - min) / subs);
                for (int i = 0; i < subs; i++) {
                    divisions.add(new Division(name + " " + ROMAN[subs - 1 - i], min + i * step, color, icon));
                }
            }
        }
        if (divisions.isEmpty()) {
            divisions.add(new Division("Unranked", 0, "<gray>", Material.IRON_INGOT));
        }
    }

    public Division of(int elo) {
        Division result = divisions.getFirst();
        for (Division division : divisions) {
            if (elo >= division.minElo()) {
                result = division;
            }
        }
        return result;
    }

    public Division next(int elo) {
        for (Division division : divisions) {
            if (division.minElo() > elo) {
                return division;
            }
        }
        return null;
    }

    public List<Division> all() {
        return divisions;
    }
}
