package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.kit.KitRules;
import io.github.aubergineux.riposte.match.Match;
import io.github.aubergineux.riposte.match.Participant;
import io.github.aubergineux.riposte.util.BlockPos;
import io.github.aubergineux.riposte.util.Items;
import io.github.aubergineux.riposte.util.Text;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/**
 * Records every block change inside copies that are in use, so they can be reset, and enforces
 * build rules (only build kits may place, only player-placed blocks may be broken, stay inside the arena).
 */
public final class ArenaListener implements Listener {

    private final RipostePlugin plugin;
    private final ArenaManager arenas;
    private final NamespacedKey wandKey;

    public ArenaListener(RipostePlugin plugin) {
        this.plugin = plugin;
        this.arenas = plugin.arenas();
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    public NamespacedKey wandKey() {
        return wandKey;
    }

    private boolean inArenaWorld(World world) {
        return world == arenas.arenaWorld();
    }

    /** Records the block if it lies inside an in-use copy. Returns the copy, or null when outside one. */
    private ArenaCopy track(Block block) {
        if (!inArenaWorld(block.getWorld())) {
            return null;
        }
        ArenaCopy copy = arenas.copyAt(block.getX(), block.getY(), block.getZ(), 0);
        if (copy == null || copy.state() != ArenaCopy.State.IN_USE) {
            return null;
        }
        copy.tracker().record(BlockPos.pack(block.getX(), block.getY(), block.getZ()), block::getBlockData);
        return copy;
    }

    private void track(BlockState state) {
        if (!inArenaWorld(state.getWorld())) {
            return;
        }
        ArenaCopy copy = arenas.copyAt(state.getX(), state.getY(), state.getZ(), 0);
        if (copy != null && copy.state() == ArenaCopy.State.IN_USE) {
            copy.tracker().record(BlockPos.pack(state.getX(), state.getY(), state.getZ()), state::getBlockData);
        }
    }

    /** Whether a player in a match may change this block at all. */
    private boolean canBuild(Player player, Block block, boolean breaking) {
        Match match = plugin.matches().matchOf(player);
        if (match == null) {
            return plugin.profiles().get(player) != null && plugin.profiles().get(player).buildMode();
        }
        Participant p = match.participant(player.getUniqueId());
        KitRules rules = match.kit().rules();
        if (p == null || !p.alive() || match.state() != Match.State.FIGHTING || !rules.build) {
            return false;
        }
        ArenaCopy copy = match.arena();
        if (!copy.contains(block.getX(), block.getY(), block.getZ(), 0) || block.getY() > copy.buildLimit(rules.buildHeight)) {
            return false;
        }
        if (rules.winCondition == KitRules.WinCondition.BRIDGE) {
            for (int team = 0; team < 2; team++) {
                BlockPos goal = copy.goal(team);
                if (goal != null && Math.abs(block.getX() - goal.x()) <= 2 && Math.abs(block.getZ() - goal.z()) <= 2
                        && block.getY() >= goal.y() - 1) {
                    return false;
                }
            }
        }
        if (breaking) {
            return copy.tracker().isPlaced(BlockPos.pack(block.getX(), block.getY(), block.getZ()));
        }
        return true;
    }

    // ------------------------------------------------------------------ player changes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        boolean arena = inArenaWorld(block.getWorld());
        if (!arena && plugin.matches().matchOf(player) == null) {
            return;
        }
        if (!canBuild(player, block, false)) {
            event.setCancelled(true);
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState state : multi.getReplacedBlockStates()) {
                track(state);
                markPlaced(state.getBlock());
            }
        } else {
            track(event.getBlockReplacedState());
            markPlaced(block);
        }
    }

    private void markPlaced(Block block) {
        ArenaCopy copy = arenas.copyAt(block.getX(), block.getY(), block.getZ(), 0);
        if (copy != null && copy.state() == ArenaCopy.State.IN_USE) {
            copy.tracker().markPlaced(BlockPos.pack(block.getX(), block.getY(), block.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        if (!inArenaWorld(block.getWorld()) && plugin.matches().matchOf(player) == null) {
            return;
        }
        if (!canBuild(player, block, true)) {
            event.setCancelled(true);
            return;
        }
        track(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!inArenaWorld(block.getWorld()) && plugin.matches().matchOf(event.getPlayer()) == null) {
            return;
        }
        if (!canBuild(event.getPlayer(), block, false)) {
            event.setCancelled(true);
            return;
        }
        track(block);
        markPlaced(block);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (!inArenaWorld(block.getWorld()) && plugin.matches().matchOf(event.getPlayer()) == null) {
            return;
        }
        if (!canBuild(event.getPlayer(), block, true)) {
            event.setCancelled(true);
            return;
        }
        track(block);
    }

    // ------------------------------------------------------------------ world changes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        Block to = event.getToBlock();
        if (!inArenaWorld(to.getWorld())) {
            return;
        }
        ArenaCopy source = arenas.copyAt(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ(), 0);
        ArenaCopy target = track(to);
        if (target == null || target != source) {
            event.setCancelled(true);
        } else {
            // flowing liquid can be scooped back up like a placed block
            target.tracker().markPlaced(BlockPos.pack(to.getX(), to.getY(), to.getZ()));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        guard(event.getBlock(), event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        guard(event.getBlock(), event);
    }

    /** Outside an active match nothing in the arena world may change; inside one, the change is recorded. */
    private void guard(Block block, org.bukkit.event.Cancellable event) {
        if (inArenaWorld(block.getWorld()) && track(block) == null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        filterExplosion(event.getLocation().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        filterExplosion(event.getBlock().getWorld(), event.blockList());
    }

    private void filterExplosion(World world, List<Block> blocks) {
        if (!inArenaWorld(world)) {
            return;
        }
        blocks.removeIf(block -> track(block) == null);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!inArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (track(block) == null || track(block.getRelative(event.getDirection())) == null) {
                event.setCancelled(true);
                return;
            }
        }
        track(event.getBlock().getRelative(event.getDirection()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!inArenaWorld(event.getBlock().getWorld())) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (track(block) == null || track(block.getRelative(event.getDirection())) == null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (inArenaWorld(event.getFrom().getWorld()) || plugin.matches().matchOf(event.getPlayer()) != null) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ wand

    @EventHandler(priority = EventPriority.LOW)
    public void onWand(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null) {
            return;
        }
        if (Items.tagOf(event.getItem(), wandKey) == null || !event.getPlayer().hasPermission("riposte.admin")) {
            return;
        }
        int corner;
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            corner = 1;
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            corner = 2;
        } else {
            return;
        }
        event.setCancelled(true);
        Block block = event.getClickedBlock();
        arenas.select(event.getPlayer(), corner, BlockPos.of(block));
        ArenaManager.Selection sel = arenas.selection(event.getPlayer().getUniqueId());
        String size = "";
        if (sel.pos1() != null && sel.pos2() != null) {
            BlockPos min = BlockPos.min(sel.pos1(), sel.pos2());
            BlockPos max = BlockPos.max(sel.pos1(), sel.pos2());
            size = (max.x() - min.x() + 1) + "x" + (max.y() - min.y() + 1) + "x" + (max.z() - min.z() + 1);
        }
        plugin.messages().send(event.getPlayer(), "arena.wand-corner", Text.p("corner", corner),
                Text.p("x", block.getX()), Text.p("y", block.getY()), Text.p("z", block.getZ()), Text.p("size", size));
    }
}
