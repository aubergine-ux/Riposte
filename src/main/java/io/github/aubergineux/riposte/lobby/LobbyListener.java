package io.github.aubergineux.riposte.lobby;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.menu.menus.KitSelectMenu;
import io.github.aubergineux.riposte.menu.menus.LeaderboardMenu;
import io.github.aubergineux.riposte.menu.menus.PartyMenu;
import io.github.aubergineux.riposte.menu.menus.QueueMenu;
import io.github.aubergineux.riposte.menu.menus.SettingsMenu;
import io.github.aubergineux.riposte.menu.menus.SpectateMenu;
import io.github.aubergineux.riposte.menu.menus.StatsMenu;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lobby protection and hotbar item handling. */
public final class LobbyListener implements Listener {

    private final RipostePlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public LobbyListener(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (plugin.settings().lobbyOnJoin || plugin.arenas().copyAt(player.getLocation()) != null
                || player.getWorld() == plugin.arenas().arenaWorld()) {
            plugin.lobby().toLobby(player);
        } else {
            plugin.lobby().giveHotbar(player);
            plugin.lobby().applyTime(player);
            plugin.scoreboards().refresh(player);
        }
        // hide spectators from the new player
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (plugin.spectators().isSpectating(other)) {
                player.hidePlayer(plugin, other);
            }
        }
        plugin.messages().send(player, "lobby.welcome", Text.p("player", player.getName()));
        if (player.hasPermission("riposte.admin") && (!plugin.lobby().hasLobby() || plugin.arenas().all().isEmpty())) {
            plugin.messages().send(player, "setup.reminder");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // right click only: left clicks fire on every arm swing
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            if (plugin.lobby().actionOf(event.getItem()) != null) {
                event.setCancelled(true);
            }
            return;
        }
        ItemStack item = event.getItem();
        HotbarAction action = plugin.lobby().actionOf(item);
        if (action == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < 250) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        run(player, action);
    }

    public void run(Player player, HotbarAction action) {
        switch (action) {
            case UNRANKED -> new QueueMenu(plugin, player, false).open();
            case RANKED -> new QueueMenu(plugin, player, true).open();
            case KIT_EDITOR -> new KitSelectMenu(plugin, player, KitSelectMenu.Purpose.EDIT, kit -> plugin.kitEditor().open(player, kit)).open();
            case LEADERBOARDS -> new LeaderboardMenu(plugin, player).open();
            case SETTINGS -> new SettingsMenu(plugin, player).open();
            case STATS -> StatsMenu.open(plugin, player, player.getName());
            case SPECTATE_MENU -> new SpectateMenu(plugin, player).open();
            case PARTY_CREATE -> plugin.parties().create(player);
            case PARTY_MENU -> new PartyMenu(plugin, player).open();
            case PARTY_INFO -> plugin.parties().info(player);
            case PARTY_LEAVE -> plugin.parties().leave(player);
            case LEAVE_QUEUE -> plugin.queues().leave(player, true);
            case LEAVE_SPECTATE -> plugin.spectators().stopSpectating(player, true);
            case SPECTATE_NEXT -> plugin.spectators().cycleTarget(player);
            case EVENT_JOIN -> plugin.events().join(player);
            case EVENT_LEAVE -> plugin.events().leave(player, true);
            case NONE -> {
            }
        }
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && plugin.lobby().isProtected(player)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(plugin.lobby().lobby());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && plugin.lobby().isProtected(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrop(PlayerDropItemEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isLocked(player)) {
            return;
        }
        // menus handle their own clicks; this only stops players shuffling hotbar items around
        if (event.getView().getTopInventory().getHolder(false) instanceof io.github.aubergineux.riposte.menu.Menu menu && menu.allowBottomClicks()) {
            return;
        }
        if (event.getClickedInventory() == player.getInventory() || event.getClick().isShiftClick() || event.getHotbarButton() >= 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.lobby().isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.lobby().isProtected(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock() || !plugin.settings().lobbyVoidTeleport) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getTo().getY() < event.getTo().getWorld().getMinHeight() - 10 && plugin.lobby().isProtected(player)) {
            player.teleport(plugin.lobby().lobby());
        }
    }

    /** Lobby, queue, spectator and event players cannot move or drop their hotbar items. */
    private boolean isLocked(Player player) {
        PlayerProfile profile = plugin.profiles().get(player);
        if (profile == null || profile.buildMode()) {
            return false;
        }
        PlayerState state = profile.state();
        return state == PlayerState.LOBBY || state == PlayerState.QUEUE || state == PlayerState.SPECTATING || state == PlayerState.EVENT;
    }
}
