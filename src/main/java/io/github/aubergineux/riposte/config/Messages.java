package io.github.aubergineux.riposte.config;

import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** All player-facing chat text, read from messages.yml and rendered with MiniMessage. */
public final class Messages {

    private final YamlFile file;
    private YamlConfiguration yaml;
    private TagResolver globals = TagResolver.empty();

    public Messages(JavaPlugin plugin) {
        this.file = new YamlFile(plugin, "messages.yml", true);
    }

    public void reload() {
        yaml = file.load();
        TagResolver.Builder builder = TagResolver.builder();
        builder.resolver(Placeholder.parsed("prefix", yaml.getString("prefix", "")));
        var colors = yaml.getConfigurationSection("colors");
        if (colors != null) {
            for (String key : colors.getKeys(false)) {
                // <primary>text</primary> style tags, so the whole palette can be changed in one place
                TextColor color = parseColor(colors.getString(key, "white"));
                builder.resolver(Placeholder.styling(key, color));
            }
        }
        globals = builder.build();
    }

    public static TextColor parseColor(String value) {
        TextColor color = value.startsWith("#") ? TextColor.fromHexString(value) : NamedTextColor.NAMES.value(value.toLowerCase(java.util.Locale.ROOT));
        return color == null ? NamedTextColor.WHITE : color;
    }

    public String raw(String key) {
        String value = yaml.getString(key);
        return value == null ? "<red>Missing message: " + key : value;
    }

    public boolean has(String key) {
        String value = yaml.getString(key);
        return value != null && !value.isEmpty();
    }

    public TagResolver globals() {
        return globals;
    }

    public Component get(String key, TagResolver... resolvers) {
        return Text.parse(raw(key), withGlobals(resolvers));
    }

    public Component format(String miniMessage, TagResolver... resolvers) {
        return Text.parse(miniMessage, withGlobals(resolvers));
    }

    public Component formatItem(String miniMessage, TagResolver... resolvers) {
        return Text.item(miniMessage, withGlobals(resolvers));
    }

    public List<Component> getList(String key, TagResolver... resolvers) {
        List<String> lines = yaml.getStringList(key);
        List<Component> out = new ArrayList<>(lines.size());
        TagResolver all = withGlobals(resolvers);
        for (String line : lines) {
            out.add(Text.parse(line, all));
        }
        return out;
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        if (yaml.isList(key)) {
            getList(key, resolvers).forEach(to::sendMessage);
            return;
        }
        String value = yaml.getString(key);
        if (value == null || value.isEmpty()) {
            return;
        }
        to.sendMessage(get(key, resolvers));
    }

    public void actionBar(Audience to, String key, TagResolver... resolvers) {
        if (has(key)) {
            to.sendActionBar(get(key, resolvers));
        }
    }

    public void title(Audience to, String key, TagResolver... resolvers) {
        String title = yaml.getString(key + ".title", "");
        String subtitle = yaml.getString(key + ".subtitle", "");
        int fadeIn = yaml.getInt(key + ".fade-in", 0);
        int stay = yaml.getInt(key + ".stay", 20);
        int fadeOut = yaml.getInt(key + ".fade-out", 5);
        TagResolver all = withGlobals(resolvers);
        to.showTitle(Title.title(Text.parse(title, all), Text.parse(subtitle, all),
                Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
    }

    private TagResolver withGlobals(TagResolver... resolvers) {
        if (resolvers.length == 0) {
            return globals;
        }
        return TagResolver.builder().resolver(globals).resolvers(resolvers).build();
    }

    public YamlConfiguration yaml() {
        return yaml;
    }
}
