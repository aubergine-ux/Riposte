package io.github.aubergineux.riposte.profile;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads profiles during async pre-login (so joining never waits on the database on the main thread)
 * and drops them after quit. Stats are written as they change, so nothing is batched on quit.
 */
public final class ProfileManager implements Listener {

    private final RipostePlugin plugin;
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();

    public ProfileManager(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerProfile get(Player player) {
        return profiles.get(player.getUniqueId());
    }

    public PlayerProfile get(UUID uuid) {
        return profiles.get(uuid);
    }

    public Iterable<PlayerProfile> all() {
        return profiles.values();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            PlayerProfile profile = plugin.dataStore().loadProfileSync(event.getUniqueId(), event.getName(), plugin.ranked().season().id());
            profiles.put(event.getUniqueId(), profile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load profile for " + event.getName(), e);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    Text.parse(plugin.messages().raw("errors.profile-load")).append(Component.empty()));
        }
    }

    /** Players already online when the plugin (re)loads have no pre-login event, so load them here. */
    public void loadOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (profiles.containsKey(player.getUniqueId())) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            int season = plugin.ranked().season().id();
            plugin.dataStore().database().async(c -> plugin.dataStore().loadProfileSync(uuid, name, season))
                    .thenAccept(profile -> Bukkit.getScheduler().runTask(plugin, () -> {
                        profiles.put(uuid, profile);
                        Player online = Bukkit.getPlayer(uuid);
                        if (online != null) {
                            plugin.lobby().toLobby(online);
                        }
                    }));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profiles.get(player.getUniqueId());
        if (profile == null) {
            // the pre-login load failed silently (e.g. a reload in between), fall back to a fresh load
            profile = new PlayerProfile(player.getUniqueId(), player.getName());
            profiles.put(player.getUniqueId(), profile);
        }
        profile.name(player.getName());
        profile.state(PlayerState.LOBBY);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // keep the profile one tick so other quit handlers can still read it
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getPlayer(uuid) == null) {
                profiles.remove(uuid);
            }
        });
    }

    public void saveSettings(Player player) {
        PlayerProfile profile = get(player);
        if (profile != null) {
            plugin.dataStore().saveSettings(profile.uuid(), profile.settings());
        }
    }
}
