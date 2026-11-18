package io.github.aubergineux.riposte.match;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.arena.ArenaCopy;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.kit.KitRules;
import io.github.aubergineux.riposte.profile.KitStats;
import io.github.aubergineux.riposte.profile.PlayerProfile;
import io.github.aubergineux.riposte.profile.PlayerState;
import io.github.aubergineux.riposte.util.BlockPos;
import io.github.aubergineux.riposte.util.Text;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Creates matches, drives their countdowns and rounds, and cleans up afterwards. */
public final class MatchManager {

    private static final long KILL_CREDIT_MILLIS = 12_000;

    private final RipostePlugin plugin;
    private final Map<UUID, Match> matches = new LinkedHashMap<>();
    private final Map<UUID, Match> byPlayer = new HashMap<>();
    private final Map<UUID, InventorySnapshot> snapshots = new HashMap<>();

    public MatchManager(RipostePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::secondTick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::fastTick, 5L, 5L);
    }

    // ------------------------------------------------------------------ queries

    public Match matchOf(Player player) {
        return byPlayer.get(player.getUniqueId());
    }

    public Match matchOf(UUID uuid) {
        return byPlayer.get(uuid);
    }

    public Match byId(UUID id) {
        return matches.get(id);
    }

    public Collection<Match> all() {
        return Collections.unmodifiableCollection(matches.values());
    }

    public int count() {
        return matches.size();
    }

    public int fighting(Kit kit) {
        int count = 0;
        for (Match match : matches.values()) {
            if (match.kit() == kit) {
                count += match.participants().size();
            }
        }
        return count;
    }

    public int playersInMatches() {
        return byPlayer.size();
    }

    public InventorySnapshot snapshot(UUID id) {
        return snapshots.get(id);
    }

    // ------------------------------------------------------------------ starting

    /**
     * Starts a match in an already allocated copy. Teams are lists of online players.
     * Returns the match, or null if a player could not be prepared.
     */
    public Match start(Kit kit, ArenaCopy copy, Match.Type type, List<List<Player>> teams, Consumer<Match.Result> onEnd) {
        Match match = new Match(kit, copy, type, teams, onEnd);
        matches.put(match.id(), match);
        List<Player> everyone = new ArrayList<>();
        teams.forEach(everyone::addAll);
        for (Player player : everyone) {
            plugin.queues().leave(player, false);
            plugin.duels().clearRequests(player);
            plugin.spectators().stopSpectating(player, false);
            byPlayer.put(player.getUniqueId(), match);
            plugin.profiles().get(player).state(PlayerState.MATCH);
            player.closeInventory();
        }
        // everybody in the fight sees everybody else, whatever their lobby settings were
        for (Player a : everyone) {
            for (Player b : everyone) {
                if (a != b) {
                    a.showPlayer(plugin, b);
                }
            }
        }
        spawnAll(match, true);
        int startingElo = plugin.settings().startingElo;
        for (Participant p : match.participants()) {
            Player player = p.player();
            if (player == null) {
                continue;
            }
            Participant opponent = match.isDuel() ? match.opponentOf(p.uuid()) : null;
            int opponentElo = startingElo;
            if (opponent != null) {
                PlayerProfile other = plugin.profiles().get(opponent.uuid());
                opponentElo = other == null ? startingElo : other.rating(kit.id(), startingElo).elo;
            }
            plugin.messages().send(player, match.type().ranked() ? "match.start-ranked" : "match.start",
                    Text.mm("kit", kit.displayName()),
                    Text.mm("arena", copy.arena().displayName()),
                    Text.p("opponent", opponent != null ? opponent.name() : match.title()),
                    Text.p("elo", opponentElo));
        }
        beginCountdown(match);
        plugin.scoreboards().refreshAll(match.audience());
        return match;
    }

    private void spawnAll(Match match, boolean firstSpawn) {
        List<Location> ffaSpawns = match.teams().size() > 2 ? ffaSpawns(match) : null;
        int ffaIndex = 0;
        for (MatchTeam team : match.teams()) {
            for (Participant p : team.members()) {
                Player player = p.player();
                if (player == null || p.disconnected()) {
                    continue;
                }
                p.alive(true);
                Location spawn = ffaSpawns != null ? ffaSpawns.get(ffaIndex++ % ffaSpawns.size()) : match.arena().spawn(team.index() % 2);
                preparePlayer(player, match.kit());
                player.teleport(spawn);
                giveKit(player, match, team.index());
                if (!firstSpawn) {
                    plugin.spectators().revealParticipant(player, match);
                }
            }
        }
    }

    private List<Location> ffaSpawns(Match match) {
        Location a = match.arena().spawn(0);
        Location b = match.arena().spawn(1);
        double cx = (a.getX() + b.getX()) / 2;
        double cz = (a.getZ() + b.getZ()) / 2;
        double radius = Math.max(3, a.distance(b) / 2);
        int count = match.participants().size();
        List<Location> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            double x = cx + Math.cos(angle) * radius;
            double z = cz + Math.sin(angle) * radius;
            Location l = new Location(a.getWorld(), x, a.getY(), z);
            l.setDirection(new org.bukkit.util.Vector(cx - x, 0, cz - z));
            out.add(l);
        }
        return out;
    }

    public void preparePlayer(Player player, Kit kit) {
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvisible(false);
        player.setInvulnerable(false);
        var max = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(max == null ? 20 : max.getValue());
        player.setFoodLevel(20);
        player.setSaturation(kit.rules().hunger ? 5f : 20f);
        player.setExhaustion(0f);
        player.setFireTicks(0);
        player.setFallDistance(0);
        player.setFreezeTicks(0);
        player.setArrowsInBody(0, false);
        player.setLevel(0);
        player.setExp(0);
        player.setMaximumNoDamageTicks(kit.rules().hitDelayTicks);
        player.setNoDamageTicks(0);
        player.setCollidable(true);
        player.setVelocity(new org.bukkit.util.Vector());
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
        player.getInventory().clear();
        player.setItemOnCursor(null);
        for (Material material : new Material[]{Material.ENDER_PEARL, Material.WIND_CHARGE, Material.GOLDEN_APPLE, Material.SHIELD}) {
            player.setCooldown(material, 0);
        }
    }

    private void giveKit(Player player, Match match, int teamIndex) {
        Kit kit = match.kit();
        PlayerProfile profile = plugin.profiles().get(player);
        kit.apply(player, profile == null ? null : profile.layout(kit.id()));
        if (match.teams().size() == 2 && (kit.rules().winCondition == KitRules.WinCondition.BRIDGE || match.teams().get(0).members().size() > 1)) {
            colorize(player.getInventory(), teamIndex);
        }
    }

    /** Dyes leather armor and swaps terracotta/wool/glass colors to the team color (red or blue). */
    private void colorize(PlayerInventory inv, int team) {
        Color color = team == 0 ? Color.fromRGB(0xE5484D) : Color.fromRGB(0x3E8BFF);
        String prefix = team == 0 ? "RED_" : "BLUE_";
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null) {
                continue;
            }
            if (item.getItemMeta() instanceof LeatherArmorMeta) {
                item.editMeta(LeatherArmorMeta.class, meta -> meta.setColor(color));
                inv.setItem(i, item);
                continue;
            }
            String name = item.getType().name();
            for (String suffix : new String[]{"_TERRACOTTA", "_WOOL", "_CONCRETE", "_STAINED_GLASS"}) {
                if (name.endsWith(suffix) && !name.contains("GLAZED")) {
                    Material target = Material.matchMaterial(prefix + suffix.substring(1));
                    if (target != null && target != item.getType()) {
                        inv.setItem(i, item.withType(target));
                    }
                    break;
                }
            }
        }
    }

    private void beginCountdown(Match match) {
        match.state(Match.State.STARTING);
        match.countdown(Math.max(0, plugin.settings().countdownSeconds));
        if (match.countdown() == 0) {
            beginFight(match);
        } else {
            countdownStep(match);
        }
    }

    private void countdownStep(Match match) {
        int n = match.countdown();
        for (Player player : match.audience()) {
            plugin.messages().title(player, "titles.countdown", Text.p("seconds", n), Text.p("round", match.round()));
            player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, Sound.Source.MASTER, 1f, 1f));
        }
    }

    private void beginFight(Match match) {
        match.state(Match.State.FIGHTING);
        if (match.fightStartedAt() == 0) {
            match.fightStartedAt(System.currentTimeMillis());
        }
        for (Player player : match.audience()) {
            plugin.messages().title(player, "titles.fight", Text.p("round", match.round()));
            player.playSound(Sound.sound(org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 2f));
        }
    }

    // ------------------------------------------------------------------ ticking

    private void secondTick() {
        long maxDuration = plugin.settings().maxMatchMinutes * 60_000L;
        for (Match match : new ArrayList<>(matches.values())) {
            switch (match.state()) {
                case STARTING -> {
                    match.countdown(match.countdown() - 1);
                    if (match.countdown() <= 0) {
                        beginFight(match);
                    } else {
                        countdownStep(match);
                    }
                }
                case FIGHTING -> {
                    if (maxDuration > 0 && match.duration() > maxDuration) {
                        broadcast(match, "match.time-limit");
                        end(match, null);
                    }
                }
                default -> {
                }
            }
        }
        snapshots.values().removeIf(s -> System.currentTimeMillis() - s.created() > plugin.settings().snapshotMinutes * 60_000L);
    }

    /** Bridge respawns and live checks that do not need the move event. */
    private void fastTick() {
        long now = System.currentTimeMillis();
        for (Match match : matches.values()) {
            if (match.state() != Match.State.FIGHTING || match.kit().rules().winCondition != KitRules.WinCondition.BRIDGE) {
                continue;
            }
            for (Participant p : match.participants()) {
                if (!p.alive() && !p.disconnected() && p.respawnAt() > 0 && now >= p.respawnAt()) {
                    p.respawnAt(0);
                    Player player = p.player();
                    if (player != null) {
                        respawnBridge(match, p, player);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ fight events

    /** A participant died (real death, void, or sumo fall). */
    public void handleDeath(Match match, Participant victim, UUID killerId, DeathCause cause) {
        if (match.state() != Match.State.FIGHTING || !victim.alive()) {
            return;
        }
        Player player = victim.player();
        if (killerId == null) {
            killerId = victim.recentAttacker(KILL_CREDIT_MILLIS);
        }
        Participant killer = killerId == null ? null : match.participant(killerId);
        if (killer != null && killer.team() == victim.team()) {
            killer = null;
        }
        victim.stats().deaths++;
        if (killer != null) {
            killer.stats().kills++;
        }
        String key = switch (cause) {
            case VOID -> killer == null ? "match.death.void" : "match.death.void-killed";
            case SUMO -> "match.death.sumo";
            case DISCONNECT -> "match.death.disconnect";
            default -> killer == null ? "match.death.generic" : "match.death.killed";
        };
        broadcast(match, key, Text.p("victim", victim.name()), Text.p("killer", killer == null ? "" : killer.name()),
                Text.p("health", killer == null || killer.player() == null ? "0" : Text.hearts(killer.player().getHealth())));

        if (match.kit().rules().winCondition == KitRules.WinCondition.BRIDGE && cause != DeathCause.DISCONNECT) {
            victim.alive(false);
            victim.respawnAt(System.currentTimeMillis() + match.kit().rules().respawnSeconds * 1000L);
            if (player != null) {
                plugin.spectators().makeDeadSpectator(player, match);
                if (match.kit().rules().respawnSeconds == 0) {
                    victim.respawnAt(0);
                    respawnBridge(match, victim, player);
                }
            }
            return;
        }

        if (player != null) {
            victim.snapshot(new InventorySnapshot(player, victim.stats(), match.kit().id(), 0));
            if (player.getLocation().getY() < match.arena().floorY()) {
                player.teleport(match.arena().center());
            }
        }
        victim.alive(false);
        if (player != null && cause != DeathCause.DISCONNECT) {
            player.playSound(Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_HURT, Sound.Source.MASTER, 1f, 0.8f));
            plugin.spectators().makeDeadSpectator(player, match);
        }
        checkRoundOver(match);
    }

    private void respawnBridge(Match match, Participant p, Player player) {
        p.alive(true);
        preparePlayer(player, match.kit());
        player.teleport(match.arena().spawn(p.team().index() % 2));
        giveKit(player, match, p.team().index());
        plugin.spectators().revealParticipant(player, match);
    }

    /** Bridge: the player dropped into a goal. */
    public void handleGoal(Match match, Participant scorer, int goalTeam) {
        if (match.state() != Match.State.FIGHTING) {
            return;
        }
        Player player = scorer.player();
        if (goalTeam == scorer.team().index()) {
            // own goal: just send them home
            if (player != null) {
                respawnBridge(match, scorer, player);
            }
            return;
        }
        scorer.stats().goals++;
        scorer.team().addScore();
        for (Player viewer : match.audience()) {
            plugin.messages().title(viewer, "titles.goal", Text.p("player", scorer.name()),
                    Text.p("score", scoreLine(match)));
        }
        broadcast(match, "match.goal", Text.p("player", scorer.name()), Text.p("score", scoreLine(match)));
        if (scorer.team().score() >= match.kit().rules().roundsToWin) {
            end(match, scorer.team());
            return;
        }
        match.nextRound();
        spawnAll(match, false);
        beginCountdown(match);
    }

    /** Boxing: called after each landed hit. */
    public void checkBoxing(Match match, Participant attacker) {
        if (match.kit().rules().winCondition == KitRules.WinCondition.BOXING
                && attacker.stats().hits >= match.kit().rules().boxingHits && match.state() == Match.State.FIGHTING) {
            for (Participant p : match.participants()) {
                if (p.team() != attacker.team()) {
                    Player player = p.player();
                    if (player != null) {
                        p.snapshot(new InventorySnapshot(player, p.stats(), match.kit().id()));
                    }
                    p.alive(false);
                }
            }
            checkRoundOver(match);
        }
    }

    public String scoreLine(Match match) {
        StringBuilder sb = new StringBuilder();
        for (MatchTeam team : match.teams()) {
            if (!sb.isEmpty()) {
                sb.append(" - ");
            }
            sb.append(team.score());
        }
        return sb.toString();
    }

    private void checkRoundOver(Match match) {
        List<MatchTeam> alive = match.teamsAlive();
        if (alive.size() > 1) {
            return;
        }
        MatchTeam winner = alive.isEmpty() ? null : alive.getFirst();
        if (winner == null) {
            end(match, null);
            return;
        }
        winner.addScore();
        if (winner.score() >= match.kit().rules().roundsToWin || otherTeamsGone(match, winner)) {
            end(match, winner);
            return;
        }
        match.state(Match.State.ROUND_OVER);
        for (Player viewer : match.audience()) {
            plugin.messages().title(viewer, "titles.round-won", Text.p("team", winner.names()), Text.p("score", scoreLine(match)));
        }
        broadcast(match, "match.round-won", Text.p("team", winner.names()), Text.p("score", scoreLine(match)), Text.p("round", match.round()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match.state() != Match.State.ROUND_OVER) {
                return;
            }
            if (match.kit().rules().resetArenaBetweenRounds || match.kit().rules().build) {
                plugin.arenas().resetNow(match.arena());
            } else {
                plugin.arenas().removeEntities(match.arena());
            }
            match.nextRound();
            spawnAll(match, false);
            beginCountdown(match);
        }, 60L);
    }

    private boolean otherTeamsGone(Match match, MatchTeam winner) {
        for (MatchTeam team : match.teams()) {
            if (team != winner && !team.allDisconnected()) {
                return false;
            }
        }
        return true;
    }

    public void handleQuit(Player player) {
        Match match = byPlayer.get(player.getUniqueId());
        if (match == null) {
            return;
        }
        Participant p = match.participant(player.getUniqueId());
        if (p == null) {
            return;
        }
        if (p.snapshot() == null) {
            p.snapshot(new InventorySnapshot(player, p.stats(), match.kit().id()));
        }
        switch (match.state()) {
            case STARTING, FIGHTING, ROUND_OVER -> {
                if (p.alive()) {
                    p.stats().deaths++;
                }
                p.disconnected(true);
                p.alive(false);
                broadcast(match, "match.death.disconnect", Text.p("victim", p.name()));
                forfeitIfAbandoned(match);
            }
            case ENDED -> p.disconnected(true);
        }
        byPlayer.remove(player.getUniqueId());
    }

    private void forfeitIfAbandoned(Match match) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        List<MatchTeam> present = new ArrayList<>();
        for (MatchTeam team : match.teams()) {
            if (!team.allDisconnected()) {
                present.add(team);
            }
        }
        if (present.size() <= 1) {
            end(match, present.isEmpty() ? null : present.getFirst());
        } else if (match.state() == Match.State.FIGHTING) {
            checkRoundOver(match);
        }
    }

    // ------------------------------------------------------------------ ending

    public void end(Match match, MatchTeam winner) {
        if (match.state() == Match.State.ENDED) {
            return;
        }
        match.state(Match.State.ENDED);
        match.winner(winner);
        for (Participant p : match.participants()) {
            Player player = p.player();
            if (p.snapshot() == null && player != null) {
                p.snapshot(new InventorySnapshot(player, p.stats(), match.kit().id()));
            }
            if (p.snapshot() != null) {
                snapshots.put(p.snapshot().id(), p.snapshot());
            }
            if (player != null && !p.disconnected()) {
                player.setInvulnerable(true);
                player.setFireTicks(0);
            }
        }
        if (winner != null && match.type().recordsStats && match.isDuel()) {
            recordStats(match, winner);
        }
        if (winner != null && match.type().ranked() && match.isDuel()) {
            plugin.ranked().applyResult(match, winner);
        }
        for (Participant p : match.participants()) {
            Player player = p.player();
            if (player == null || p.disconnected()) {
                continue;
            }
            boolean won = winner != null && p.team() == winner;
            String title = winner == null ? "titles.draw" : won ? "titles.victory" : "titles.defeat";
            plugin.messages().title(player, title, Text.p("winner", winner == null ? "" : winner.names()));
            player.playSound(Sound.sound((won ? org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE : org.bukkit.Sound.ENTITY_WITHER_SPAWN),
                    Sound.Source.MASTER, won ? 0.8f : 0.4f, 1f));
        }
        for (Player spectator : match.spectatorPlayers()) {
            plugin.messages().title(spectator, winner == null ? "titles.draw" : "titles.spectator-winner", Text.p("winner", winner == null ? "" : winner.names()));
        }
        MatchSummary.send(plugin, match);
        if (match.isDuel() && match.type().recordsStats) {
            setupRematch(match);
        }
        int delay = Math.max(1, plugin.settings().endDelaySeconds) * 20;
        Bukkit.getScheduler().runTaskLater(plugin, () -> cleanup(match), delay);
    }

    private void recordStats(Match match, MatchTeam winner) {
        for (Participant p : match.participants()) {
            PlayerProfile profile = plugin.profiles().get(p.uuid());
            if (profile == null) {
                continue;
            }
            KitStats stats = profile.stats(match.kit().id());
            if (p.team() == winner) {
                stats.recordWin(p.stats().kills);
            } else {
                stats.recordLoss(p.stats().kills, Math.max(1, p.stats().deaths));
            }
            plugin.dataStore().saveStats(p.uuid(), match.kit().id(), stats);
        }
    }

    private void setupRematch(Match match) {
        long expires = System.currentTimeMillis() + plugin.settings().rematchSeconds * 1000L;
        for (Participant p : match.participants()) {
            Participant other = match.opponentOf(p.uuid());
            PlayerProfile profile = plugin.profiles().get(p.uuid());
            if (profile == null || other == null) {
                continue;
            }
            profile.rematchTarget = other.uuid();
            profile.rematchKit = match.kit().id();
            profile.rematchArena = match.arena().arena().name();
            profile.rematchExpires = expires;
        }
    }

    private void cleanup(Match match) {
        for (Participant p : match.participants()) {
            if (byPlayer.get(p.uuid()) == match) {
                byPlayer.remove(p.uuid());
            }
            Player player = p.player();
            if (player != null && !p.disconnected() && plugin.profiles().get(player) != null
                    && plugin.profiles().get(player).state() == PlayerState.MATCH) {
                player.setInvulnerable(false);
                plugin.lobby().toLobby(player);
            }
        }
        for (Player spectator : match.spectatorPlayers()) {
            plugin.spectators().stopSpectating(spectator, true);
        }
        match.spectators().clear();
        matches.remove(match.id());
        plugin.arenas().release(match.arena());
        if (match.onEnd() != null) {
            try {
                match.onEnd().accept(new Match.Result(match, match.winner()));
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Error in match end callback", e);
            }
        }
        // rematch offers only make sense once both players are back in the lobby
        if (match.isDuel() && match.type().recordsStats) {
            for (Participant p : match.participants()) {
                Player player = p.player();
                Participant other = match.opponentOf(p.uuid());
                if (player != null && other != null && other.player() != null && !p.disconnected() && !other.disconnected()) {
                    plugin.messages().send(player, "match.rematch-offer", Text.p("opponent", other.name()),
                            Text.p("seconds", plugin.settings().rematchSeconds));
                }
            }
        }
    }

    /** Ends every match as a draw, used on shutdown and reload. */
    public void endAll() {
        for (Match match : new ArrayList<>(matches.values())) {
            if (match.state() != Match.State.ENDED) {
                match.state(Match.State.ENDED);
            }
            cleanup(match);
        }
    }

    public void broadcast(Match match, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... resolvers) {
        for (Player player : match.audience()) {
            plugin.messages().send(player, key, resolvers);
        }
    }

    /** Bridge goal check, called from the move listener. Returns the goal team index or -1. */
    public int goalAt(Match match, Location location) {
        for (int team = 0; team < 2; team++) {
            BlockPos goal = match.arena().goal(team);
            if (goal == null) {
                continue;
            }
            if (Math.abs(location.getBlockX() - goal.x()) <= 1 && Math.abs(location.getBlockZ() - goal.z()) <= 1
                    && location.getY() >= goal.y() - 1 && location.getY() <= goal.y() + 1.2) {
                return team;
            }
        }
        return -1;
    }

    public enum DeathCause {
        KILLED, VOID, SUMO, DISCONNECT
    }
}
