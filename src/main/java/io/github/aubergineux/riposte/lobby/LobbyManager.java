package io.github.aubergineux.riposte.lobby;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerSettings;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Locale;

/** Sends players back to the lobby and hands out the hotbar for their current situation. */
public final class LobbyManager {

    public enum Hotbar {
        LOBBY("lobby"), QUEUE("queue"), PARTY_LEADER("party-leader"), PARTY_MEMBER("party-member"),
        SPECTATOR("spectator"), EVENT("event"), DEAD("dead");

        final String key;

        Hotbar(String key) {
            this.key = key;
        }
    }

    private final RipostePlugin plugin;
    private final YamlFile locations;
    private final NamespacedKey actionKey;
    private Location lobby;

    public LobbyManager(RipostePlugin plugin) {
        this.plugin = plugin;
        this.locations = new YamlFile(plugin, "data/locations.yml", false);
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public void load() {
        var yaml = locations.load();
        String s = yaml.getString("lobby");
        lobby = null;
        if (s != null) {
            String[] p = s.split(",");
            World world = Bukkit.getWorld(p[0]);
            if (world != null) {
                lobby = new Location(world, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                        Float.parseFloat(p[4]), Float.parseFloat(p[5]));
            }
        }
    }

    public void setLobby(Location location) {
        this.lobby = location.clone();
        locations.get().set("lobby", String.format(Locale.ROOT, "%s,%.2f,%.2f,%.2f,%.1f,%.1f", location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()));
        locations.save();
    }

    public boolean hasLobby() {
        return lobby != null;
    }

    public Location lobby() {
        if (lobby != null) {
            return lobby;
        }
        return Bukkit.getWorlds().getFirst().getSpawnLocation().add(0.5, 0, 0.5);
    }

    public NamespacedKey actionKey() {
        return actionKey;
    }

    /** Full reset and teleport to the lobby. */
    public void toLobby(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        boolean inEvent = plugin.events().isWaiting(player);
        profile.state(inEvent ? PlayerState.EVENT : PlayerState.LOBBY);
        reset(player);
        player.teleport(lobby());
        giveHotbar(player);
        applyTime(player);
        updateVisibility(player);
        plugin.scoreboards().refresh(player);
    }

    public void reset(Player player) {
        player.setGameMode(GameMode.ADVENTURE);
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFreezeTicks(0);
        player.setArrowsInBody(0, false);
        player.setLevel(0);
        player.setExp(0);
        player.setInvisible(false);
        player.setInvulnerable(false);
        player.setCollidable(true);
        player.setMaximumNoDamageTicks(20);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.getInventory().clear();
        player.setItemOnCursor(null);
        boolean fly = plugin.settings().lobbyFlight && player.hasPermission("riposte.lobby.fly");
        player.setAllowFlight(fly);
        player.setFlying(false);
    }

    /** Gives the hotbar that matches the player's state (lobby, queue, party, event). */
    public void giveHotbar(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        Hotbar hotbar;
        if (profile.state() == PlayerState.QUEUE) {
            hotbar = Hotbar.QUEUE;
        } else if (profile.state() == PlayerState.EVENT) {
            hotbar = Hotbar.EVENT;
        } else if (plugin.parties().partyOf(player) != null) {
            hotbar = plugin.parties().partyOf(player).isLeader(player.getUniqueId()) ? Hotbar.PARTY_LEADER : Hotbar.PARTY_MEMBER;
        } else {
            hotbar = Hotbar.LOBBY;
        }
        giveHotbar(player, hotbar);
    }

    public void giveHotbar(Player player, Hotbar hotbar) {
        player.getInventory().clear();
        ConfigurationSection section = plugin.menus().yaml().getConfigurationSection("hotbar." + hotbar.key);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null || !item.getBoolean("enabled", true)) {
                continue;
            }
            int slot = item.getInt("slot", 0);
            ItemStack stack = Items.of(plugin.menus().item("hotbar." + hotbar.key + "." + key))
                    .tag(actionKey, item.getString("action", "NONE").toUpperCase(Locale.ROOT))
                    .unbreakable()
                    .build();
            player.getInventory().setItem(slot, stack);
        }
        player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, plugin.menus().yaml().getInt("hotbar." + hotbar.key + "-held-slot", 0))));
    }

    public HotbarAction actionOf(ItemStack item) {
        String tag = Items.tagOf(item, actionKey);
        if (tag == null) {
            return null;
        }
        try {
            return HotbarAction.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return HotbarAction.NONE;
        }
    }

    public void applyTime(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null) {
            return;
        }
        PlayerSettings.TimeOfDay time = profile.settings().time;
        if (time == PlayerSettings.TimeOfDay.SERVER) {
            player.resetPlayerTime();
        } else {
            player.setPlayerTime(time.ticks, false);
        }
    }

    /** In the lobby, either everyone is visible or (if configured) other players are hidden. */
    public void updateVisibility(Player player) {
        boolean hide = plugin.settings().lobbyHidePlayers;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == player) {
                continue;
            }
            PlayerProfile otherProfile = plugin.profiles().get(other);
            boolean otherInLobby = otherProfile != null && isLobbyState(otherProfile.state());
            if (otherInLobby && !hide) {
                player.showPlayer(plugin, other);
                other.showPlayer(plugin, player);
            } else if (otherInLobby) {
                player.hidePlayer(plugin, other);
                other.hidePlayer(plugin, player);
            } else if (plugin.spectators().isSpectating(other)) {
                // spectators stay invisible to everyone
                player.hidePlayer(plugin, other);
            } else {
                player.showPlayer(plugin, other);
            }
        }
    }

    public static boolean isLobbyState(PlayerState state) {
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE || state == PlayerState.EDITING_KIT || state == PlayerState.EVENT;
    }

    public boolean isProtected(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        return profile != null && isLobbyState(profile.state()) && !profile.buildMode();
    }

    public static Material material(String s) {
        return Items.material(s, Material.PAPER);
    }
}
