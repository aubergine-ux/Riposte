package io.github.aubergineux.riposte.menu.menus;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.menu.Menu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerSettings;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Personal toggles: duel requests, scoreboard, spectators and time of day. */
public final class SettingsMenu extends Menu {

    private final RipostePlugin plugin;

    public SettingsMenu(RipostePlugin plugin, Player viewer) {
        super(viewer, 3, plugin.menus().title("settings"));
        this.plugin = plugin;
    }

    @Override
    protected void draw() {
        PlayerProfile profile = plugin.profiles().get(viewer);
        if (profile == null) {
            return;
        }
        PlayerSettings s = profile.settings();
        toggle(10, "duel-requests", s.duelRequests, () -> s.duelRequests = !s.duelRequests);
        toggle(12, "scoreboard", s.scoreboard, () -> s.scoreboard = !s.scoreboard);
        toggle(14, "spectators", s.allowSpectators, () -> s.allowSpectators = !s.allowSpectators);
        var m = plugin.menus();
        String timeName = m.string("settings.time.names." + s.time.name().toLowerCase(Locale.ROOT), Text.capitalize(s.time.name()));
        set(m.slot("settings.time.slot", 16), m.item("settings.time", Text.mm("value", timeName)), click -> {
            s.time = s.time.next();
            plugin.lobby().applyTime(viewer);
            changed();
        });
        fill(m.filler());
    }

    private void toggle(int defaultSlot, String key, boolean value, Runnable flip) {
        var m = plugin.menus();
        String path = "settings." + key;
        String state = m.string(value ? "settings.on" : "settings.off", value ? "<green>Enabled" : "<red>Disabled");
        Material material = value ? Items.material(m.string(path + ".material-on", null), Material.LIME_DYE)
                : Items.material(m.string(path + ".material-off", null), Material.GRAY_DYE);
        set(m.slot(path + ".slot", defaultSlot), m.item(path, material, Text.mm("value", state)), click -> {
            flip.run();
            changed();
        });
    }

    private void changed() {
        plugin.profiles().saveSettings(viewer);
        plugin.scoreboards().refresh(viewer);
        viewer.playSound(net.kyori.adventure.sound.Sound.sound(org.bukkit.Sound.UI_BUTTON_CLICK,
                net.kyori.adventure.sound.Sound.Source.MASTER, 0.5f, 1.2f));
        redraw();
    }
}
