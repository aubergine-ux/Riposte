package io.github.aubergineux.riposte.ranked;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.storage.DataStore;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Leaderboard holograms built from vanilla Text Display entities, so no hologram plugin is needed.
 * The entities are not saved with the world; they are spawned from holograms.yml and kept up to date.
 */
public final class HologramManager implements Listener {

    public record Hologram(String id, Location location, String board) {
    }

    private final RipostePlugin plugin;
    private final YamlFile file;
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();
    private final Map<String, UUID> entities = new LinkedHashMap<>();

    public HologramManager(RipostePlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "data/holograms.yml", false);
    }

    public void load() {
        removeAll();
        holograms.clear();
        YamlConfiguration yaml = file.load();
        ConfigurationSection root = yaml.getConfigurationSection("holograms");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            World world = s == null ? null : Bukkit.getWorld(s.getString("world", ""));
            if (world == null) {
                continue;
            }
            Location location = new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
            holograms.put(id, new Hologram(id, location, s.getString("board", RankedManager.GLOBAL)));
        }
        update();
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Hologram h : holograms.values()) {
            ConfigurationSection s = yaml.createSection("holograms." + h.id());
            s.set("world", h.location().getWorld().getName());
            s.set("x", h.location().getX());
            s.set("y", h.location().getY());
            s.set("z", h.location().getZ());
            s.set("board", h.board());
        }
        try {
            file.file().getParentFile().mkdirs();
            yaml.save(file.file());
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("Could not save holograms: " + e.getMessage());
        }
    }

    public boolean create(String id, Location location, String board) {
        String key = id.toLowerCase(Locale.ROOT);
        if (holograms.containsKey(key)) {
            return false;
        }
        holograms.put(key, new Hologram(key, location.clone(), board));
        save();
        update();
        return true;
    }

    public boolean delete(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        if (holograms.remove(key) == null) {
            return false;
        }
        despawn(key);
        save();
        return true;
    }

    public Map<String, Hologram> all() {
        return holograms;
    }

    public void update() {
        for (Hologram h : holograms.values()) {
            if (!h.location().isChunkLoaded()) {
                continue;
            }
            TextDisplay display = entity(h);
            if (display == null) {
                display = spawn(h);
            }
            display.text(render(h.board()));
        }
    }

    private TextDisplay entity(Hologram h) {
        UUID uuid = entities.get(h.id());
        if (uuid == null) {
            return null;
        }
        if (Bukkit.getEntity(uuid) instanceof TextDisplay display && display.isValid()) {
            return display;
        }
        entities.remove(h.id());
        return null;
    }

    private TextDisplay spawn(Hologram h) {
        TextDisplay display = h.location().getWorld().spawn(h.location(), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(false);
            d.setLineWidth(400);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(110, 10, 10, 16));
        });
        entities.put(h.id(), display.getUniqueId());
        return display;
    }

    private void despawn(String id) {
        UUID uuid = entities.remove(id);
        if (uuid != null && Bukkit.getEntity(uuid) != null) {
            Bukkit.getEntity(uuid).remove();
        }
    }

    public void removeAll() {
        for (String id : List.copyOf(entities.keySet())) {
            despawn(id);
        }
    }

    public Component render(String board) {
        var m = plugin.messages();
        String boardName;
        if (board.equals(RankedManager.GLOBAL)) {
            boardName = m.raw("leaderboard.global-name");
        } else {
            Kit kit = plugin.kits().get(board);
            boardName = kit == null ? board : kit.displayName();
        }
        Component text = m.get("hologram.title", Text.mm("board", boardName));
        List<DataStore.LeaderboardEntry> entries = plugin.ranked().board(board);
        int size = plugin.settings().leaderboardSize;
        for (int i = 0; i < size; i++) {
            text = text.appendNewline();
            if (i < entries.size()) {
                DataStore.LeaderboardEntry e = entries.get(i);
                Division division = plugin.ranked().division(e.elo());
                text = text.append(m.get("hologram.line", Text.p("rank", i + 1), Text.p("name", e.name()), Text.p("elo", e.elo()),
                        Text.mm("rank_color", m.raw("leaderboard.rank-colors." + Math.min(4, i + 1))),
                        Text.mm("division", division.formatted()), Text.mm("division_color", division.color())));
            } else {
                text = text.append(m.get("hologram.empty", Text.p("rank", i + 1)));
            }
        }
        text = text.appendNewline().append(m.get("hologram.footer", Text.p("season", plugin.ranked().season().name())));
        return text;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Hologram h : holograms.values()) {
            if (h.location().getWorld() == event.getWorld()
                    && h.location().getBlockX() >> 4 == event.getChunk().getX()
                    && h.location().getBlockZ() >> 4 == event.getChunk().getZ()) {
                Bukkit.getScheduler().runTask(plugin, this::update);
                return;
            }
        }
    }
}
