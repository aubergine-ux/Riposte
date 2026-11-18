package io.github.aubergineux.riposte.spectate;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.lobby.LobbyManager;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.Participant;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Spectators fly around invisibly in adventure mode so they keep their hotbar items. */
public final class SpectatorManager implements Listener {

    private final RipostePlugin plugin;
    private final Map<UUID, Match> watching = new HashMap<>();

    public SpectatorManager(RipostePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isSpectating(Player player) {
        return watching.containsKey(player.getUniqueId());
    }

    public Match watching(Player player) {
        return watching.get(player.getUniqueId());
    }

    /** Returns a message key describing why spectating failed, or null on success. */
    public String spectate(Player spectator, Match match) {
        PlayerProfile profile = plugin.profiles().get(spectator);
        if (profile == null) {
            return "errors.generic";
        }
        if (profile.state() == PlayerState.MATCH) {
            return "spectate.in-match";
        }
        if (profile.state() == PlayerState.QUEUE) {
            plugin.queues().leave(spectator, false);
        }
        if (match.state() == Match.State.ENDED) {
            return "spectate.ended";
        }
        if (!spectator.hasPermission("riposte.spectate.bypass")) {
            for (Participant p : match.participants()) {
                PlayerProfile other = plugin.profiles().get(p.uuid());
                if (other != null && !other.settings().allowSpectators) {
                    return "spectate.disabled";
                }
            }
        }
        Match previous = watching.remove(spectator.getUniqueId());
        if (previous != null) {
            previous.spectators().remove(spectator.getUniqueId());
        }
        watching.put(spectator.getUniqueId(), match);
        match.spectators().add(spectator.getUniqueId());
        profile.state(PlayerState.SPECTATING);
        plugin.lobby().reset(spectator);
        spectator.setGameMode(GameMode.ADVENTURE);
        spectator.setAllowFlight(true);
        spectator.setFlying(true);
        spectator.setInvulnerable(true);
        spectator.setCollidable(false);
        spectator.teleport(match.arena().center());
        hideFromEveryone(spectator);
        for (Player fighter : match.onlinePlayers()) {
            spectator.showPlayer(plugin, fighter);
        }
        plugin.lobby().giveHotbar(spectator, LobbyManager.Hotbar.SPECTATOR);
        plugin.lobby().applyTime(spectator);
        plugin.scoreboards().refresh(spectator);
        plugin.messages().send(spectator, "spectate.started", Text.p("match", match.title()), Text.mm("kit", match.kit().displayName()));
        if (plugin.messages().has("spectate.notify")) {
            for (Player fighter : match.onlinePlayers()) {
                plugin.messages().send(fighter, "spectate.notify", Text.p("player", spectator.getName()));
            }
        }
        return null;
    }

    public void stopSpectating(Player player, boolean toLobby) {
        Match match = watching.remove(player.getUniqueId());
        if (match == null) {
            return;
        }
        match.spectators().remove(player.getUniqueId());
        player.setCollidable(true);
        player.setInvulnerable(false);
        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(plugin, player);
        }
        if (toLobby) {
            plugin.lobby().toLobby(player);
        }
    }

    /** An eliminated fighter keeps watching their own match, hidden from those still fighting. */
    public void makeDeadSpectator(Player player, Match match) {
        player.getInventory().clear();
        player.setFireTicks(0);
        var max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setFallDistance(0);
        for (Player other : match.audience()) {
            if (other != player) {
                other.hidePlayer(plugin, player);
            }
        }
        if (match.kit().rules().winCondition != io.github.aubergineux.riposte.kit.KitRules.WinCondition.BRIDGE) {
            plugin.lobby().giveHotbar(player, LobbyManager.Hotbar.DEAD);
        }
    }

    /** Brings a respawned fighter back: visible, grounded, collidable. */
    public void revealParticipant(Player player, Match match) {
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setCollidable(true);
        for (Player other : match.onlinePlayers()) {
            other.showPlayer(plugin, player);
        }
    }

    private void hideFromEveryone(Player spectator) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other != spectator) {
                other.hidePlayer(plugin, spectator);
            }
        }
    }

    /** Teleports the spectator to the next fighter who is still alive. */
    public void cycleTarget(Player player) {
        Match match = watching.get(player.getUniqueId());
        if (match == null) {
            match = plugin.matches().matchOf(player);
        }
        if (match == null) {
            return;
        }
        List<Player> alive = new ArrayList<>();
        for (Participant p : match.participants()) {
            Player target = p.player();
            if (p.alive() && target != null && target != player) {
                alive.add(target);
            }
        }
        if (alive.isEmpty()) {
            return;
        }
        Player nearest = null;
        double best = Double.MAX_VALUE;
        // pick the fighter furthest from where we are looking now, which cycles naturally between two players
        for (Player target : alive) {
            double d = -target.getLocation().distanceSquared(player.getLocation());
            if (d < best) {
                best = d;
                nearest = target;
            }
        }
        player.teleport(nearest.getLocation().add(0, 2, 0));
        plugin.messages().actionBar(player, "spectate.now-watching", Text.p("player", nearest.getName()));
    }

    // ------------------------------------------------------------------ protection

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isSpectating(player)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                player.teleport(watching(player).arena().center());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectile(ProjectileHitEvent event) {
        if (event.getHitEntity() instanceof Player player && (isSpectating(player) || isDeadFighter(player))) {
            event.setCancelled(true);
        }
    }

    private boolean isDeadFighter(Player player) {
        Match match = plugin.matches().matchOf(player);
        if (match == null) {
            return false;
        }
        Participant p = match.participant(player.getUniqueId());
        return p != null && !p.alive();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isSpectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isSpectating(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (isSpectating(event.getPlayer()) || isDeadFighter(event.getPlayer())) {
            if (plugin.lobby().actionOf(event.getItem()) == null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent event) {
        if (isSpectating(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) {
            return;
        }
        Match match = watching.get(event.getPlayer().getUniqueId());
        if (match != null && !match.arena().contains(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ(), 20)) {
            event.setTo(match.arena().center());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopSpectating(event.getPlayer(), false);
    }
}
