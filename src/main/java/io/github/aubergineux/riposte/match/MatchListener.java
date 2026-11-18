package io.github.aubergineux.riposte.match;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.KitRules;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.UUID;

/** Combat rules, stat tracking and win conditions while players are in a match. */
public final class MatchListener implements Listener {

    private final RipostePlugin plugin;
    private final MatchManager matches;

    public MatchListener(RipostePlugin plugin) {
        this.plugin = plugin;
        this.matches = plugin.matches();
    }

    // ------------------------------------------------------------------ movement

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        Player player = event.getPlayer();
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        Participant p = match.participant(player.getUniqueId());
        if (p == null || !p.alive()) {
            return;
        }
        Location to = event.getTo();
        if (match.state() == Match.State.STARTING) {
            Location from = event.getFrom();
            if (from.getX() != to.getX() || from.getZ() != to.getZ()) {
                Location frozen = from.clone();
                frozen.setYaw(to.getYaw());
                frozen.setPitch(to.getPitch());
                frozen.setY(Math.min(from.getY(), to.getY()));
                event.setTo(frozen);
            }
            return;
        }
        if (match.state() != Match.State.FIGHTING || !event.hasChangedBlock()) {
            return;
        }
        KitRules rules = match.kit().rules();
        if (to.getY() < match.arena().floorY() - plugin.settings().voidDepth) {
            matches.handleDeath(match, p, null, MatchManager.DeathCause.VOID);
            return;
        }
        if (rules.winCondition == KitRules.WinCondition.SUMO) {
            double spawnY = Math.min(match.arena().spawn(0).getY(), match.arena().spawn(1).getY());
            Material feet = to.getBlock().getType();
            if (to.getY() < spawnY - 1.5 || feet == Material.WATER || feet == Material.LAVA) {
                matches.handleDeath(match, p, null, MatchManager.DeathCause.SUMO);
            }
            return;
        }
        if (rules.winCondition == KitRules.WinCondition.BRIDGE) {
            int goal = matches.goalAt(match, to);
            if (goal >= 0) {
                matches.handleGoal(match, p, goal);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CONSUMABLE_EFFECT) {
            return;
        }
        Match match = matches.matchOf(event.getPlayer());
        if (match != null && !match.arena().contains(event.getTo().getBlockX(), event.getTo().getBlockY(), event.getTo().getBlockZ(), 8)) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ damage

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match match = matches.matchOf(victim);
        if (match == null) {
            return;
        }
        Participant p = match.participant(victim.getUniqueId());
        if (p == null || !p.alive() || match.state() != Match.State.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        KitRules rules = match.kit().rules();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && !rules.fallDamage) {
            event.setCancelled(true);
            return;
        }
        if (rules.damageDisabled()) {
            if (event instanceof EntityDamageByEntityEvent) {
                // a zero damage hit gives no knockback and no hit delay, so deal a sliver and heal it right back
                event.setDamage(0.01);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    var max = victim.getAttribute(Attribute.MAX_HEALTH);
                    if (victim.isValid() && !victim.isDead()) {
                        victim.setHealth(max == null ? 20 : max.getValue());
                    }
                });
            } else if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Match attackerMatch = matches.matchOf(attacker);
        Match victimMatch = matches.matchOf(victim);
        if (attackerMatch == null && victimMatch == null) {
            return;
        }
        if (attackerMatch != victimMatch || attacker == victim && !(event.getDamager() instanceof org.bukkit.entity.Projectile)) {
            event.setCancelled(attacker != victim);
            return;
        }
        Participant a = attackerMatch.participant(attacker.getUniqueId());
        Participant v = attackerMatch.participant(victim.getUniqueId());
        if (a == null || v == null || !a.alive() || !v.alive()) {
            event.setCancelled(true);
            return;
        }
        if (a != v && a.team() == v.team()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackRecorded(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event);
        if (attacker == null || !(event.getEntity() instanceof Player victim) || attacker == victim) {
            return;
        }
        Match match = matches.matchOf(attacker);
        if (match == null || match.state() != Match.State.FIGHTING || matches.matchOf(victim) != match) {
            return;
        }
        Participant a = match.participant(attacker.getUniqueId());
        Participant v = match.participant(victim.getUniqueId());
        if (a == null || v == null) {
            return;
        }
        double dealt = match.kit().rules().damageDisabled() ? 0 : Math.min(event.getFinalDamage(), victim.getHealth());
        a.stats().landHit(dealt);
        if (event.isCritical()) {
            a.stats().criticalHits++;
        }
        v.stats().takeHit(dealt);
        v.attackedBy(attacker.getUniqueId());
        if (plugin.settings().comboActionBar && a.stats().combo >= plugin.settings().comboActionBarMin) {
            plugin.messages().actionBar(attacker, "match.combo", Text.p("combo", a.stats().combo));
        }
        if (match.kit().rules().winCondition == KitRules.WinCondition.BOXING) {
            matches.checkBoxing(match, a);
        }
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        Entity causing = event.getDamageSource().getCausingEntity();
        if (causing instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        Participant p = match.participant(player.getUniqueId());
        if (p == null) {
            return;
        }
        // nobody actually dies in a match: no death screen, no drops, no death message
        event.setCancelled(true);
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        event.setReviveHealth(max == null ? 20 : max.getValue());
        Player killer = player.getKiller();
        if (killer == null && event.getDamageSource().getCausingEntity() instanceof Player causing) {
            killer = causing;
        }
        MatchManager.DeathCause cause = event.getDamageSource().getDamageType() == org.bukkit.damage.DamageType.OUT_OF_WORLD
                ? MatchManager.DeathCause.VOID : MatchManager.DeathCause.KILLED;
        UUID killerId = killer == null || killer == player ? null : killer.getUniqueId();
        // defer one tick so the revive is applied before we move the player around
        Bukkit.getScheduler().runTask(plugin, () -> matches.handleDeath(match, p, killerId, cause));
    }

    // ------------------------------------------------------------------ survival rules

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match != null && !match.kit().rules().hunger && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null || match.kit().rules().naturalRegen) {
            return;
        }
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED
                || event.getRegainReason() == EntityRegainHealthEvent.RegainReason.REGEN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player target) {
                Match match = matches.matchOf(target);
                Participant p = match == null ? null : match.participant(target.getUniqueId());
                if (p == null || !p.alive() || plugin.spectators().isSpectating(target)) {
                    event.setIntensity(target, 0);
                }
            }
        }
        if (!(potion.getShooter() instanceof Player thrower)) {
            return;
        }
        Match match = matches.matchOf(thrower);
        if (match == null) {
            return;
        }
        Participant p = match.participant(thrower.getUniqueId());
        if (p == null || !(potion.getItem().getItemMeta() instanceof PotionMeta meta)) {
            return;
        }
        PotionType type = meta.getBasePotionType();
        if (type == PotionType.HEALING || type == PotionType.STRONG_HEALING) {
            p.stats().potionsUsed++;
            if (event.getIntensity(thrower) < 0.5) {
                p.stats().potionsMissed++;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Match match = matches.matchOf(event.getPlayer());
        if (match == null || event.getItem().getType() != Material.POTION) {
            return;
        }
        // empty bottles just clutter the inventory
        Bukkit.getScheduler().runTask(plugin, () -> event.getPlayer().getInventory().remove(Material.GLASS_BOTTLE));
    }

    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl pearl) || !(pearl.getShooter() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        if (match.state() != Match.State.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        int seconds = match.kit().rules().pearlCooldownSeconds;
        if (seconds > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> player.setCooldown(Material.ENDER_PEARL, seconds * 20));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Match match = matches.matchOf(event.getPlayer());
        if (match == null) {
            return;
        }
        Participant p = match.participant(event.getPlayer().getUniqueId());
        if (p == null || !p.alive() || match.state() != Match.State.FIGHTING) {
            event.setCancelled(true);
            return;
        }
        if (event.getItemDrop().getItemStack().getType() == Material.GLASS_BOTTLE) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Match match = matches.matchOf(player);
        if (match == null) {
            return;
        }
        Participant p = match.participant(player.getUniqueId());
        if (p == null || !p.alive()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        matches.handleQuit(event.getPlayer());
    }
}
