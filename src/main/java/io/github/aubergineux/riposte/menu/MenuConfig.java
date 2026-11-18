package io.github.aubergineux.riposte.menu;

import io.github.aubergineux.riposte.config.Messages;
import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Reads menu titles and item templates from menus.yml. */
public final class MenuConfig {

    private final YamlFile file;
    private final Messages messages;
    private YamlConfiguration yaml;

    public MenuConfig(JavaPlugin plugin, Messages messages) {
        this.file = new YamlFile(plugin, "menus.yml", true);
        this.messages = messages;
    }

    public void reload() {
        yaml = file.load();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }

    public Component title(String menu, TagResolver... resolvers) {
        return messages.format(yaml.getString(menu + ".title", menu), resolvers);
    }

    public String string(String path, String def) {
        return yaml.getString(path, def);
    }

    public List<String> lines(String path) {
        return yaml.getStringList(path);
    }

    /** Builds an item from a section with material, name, lore and glow keys. */
    public ItemStack item(String path, TagResolver... resolvers) {
        return item(path, null, resolvers);
    }

    public ItemStack item(String path, Material materialOverride, TagResolver... resolvers) {
        ConfigurationSection s = yaml.getConfigurationSection(path);
        if (s == null) {
            return Items.of(materialOverride == null ? Material.BARRIER : materialOverride).name("<red>" + path).build();
        }
        Material material = materialOverride != null ? materialOverride : Items.material(s.getString("material"), Material.PAPER);
        return Items.of(material, Math.max(1, s.getInt("amount", 1)))
                .name(messages.formatItem(s.getString("name", ""), resolvers))
                .lore(lore(s.getStringList("lore"), resolvers))
                .glow(s.getBoolean("glow", false))
                .hideAll()
                .build();
    }

    public List<Component> lore(List<String> lines, TagResolver... resolvers) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(messages.formatItem(line, resolvers));
        }
        return out;
    }

    public ItemStack filler() {
        Material material = Items.material(yaml.getString("filler.material"), Material.GRAY_STAINED_GLASS_PANE);
        return Items.of(material).name(Component.text(" ")).hideAll().meta(meta -> meta.setHideTooltip(true)).build();
    }

    public ItemStack border() {
        Material material = Items.material(yaml.getString("border.material"), Material.BLACK_STAINED_GLASS_PANE);
        return Items.of(material).name(Component.text(" ")).hideAll().meta(meta -> meta.setHideTooltip(true)).build();
    }

    public int slot(String path, int def) {
        return yaml.getInt(path, def);
    }

    public Messages messages() {
        return messages;
    }

    public static String plain(Component c) {
        return Text.plain(c);
    }
}
