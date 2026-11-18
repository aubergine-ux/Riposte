package io.github.aubergineux.riposte.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * A YAML file in the plugin folder that is backed by a bundled default.
 * Keys added in newer versions are copied into the user's file on load, existing values are never touched.
 */
public final class YamlFile {

    private final JavaPlugin plugin;
    private final String name;
    private final boolean mergeDefaults;
    private final File file;
    private YamlConfiguration config;

    public YamlFile(JavaPlugin plugin, String name, boolean mergeDefaults) {
        this.plugin = plugin;
        this.name = name;
        this.mergeDefaults = mergeDefaults;
        this.file = new File(plugin.getDataFolder(), name);
    }

    public YamlConfiguration load() {
        if (!file.exists() && plugin.getResource(name) != null) {
            plugin.saveResource(name, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        if (mergeDefaults) {
            try (InputStream in = plugin.getResource(name)) {
                if (in != null) {
                    YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
                    boolean changed = false;
                    for (String key : defaults.getKeys(true)) {
                        if (!defaults.isConfigurationSection(key) && !config.contains(key, true)) {
                            config.set(key, defaults.get(key));
                            changed = true;
                        }
                    }
                    config.setDefaults(defaults);
                    if (changed) {
                        save();
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Could not read bundled " + name, e);
            }
        }
        return config;
    }

    public YamlConfiguration get() {
        return config;
    }

    public void save() {
        try {
            file.getParentFile().mkdirs();
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + name, e);
        }
    }

    public File file() {
        return file;
    }
}
