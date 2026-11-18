package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.RipostePlugin;
import io.github.aubergineux.riposte.config.YamlFile;
import io.github.aubergineux.riposte.kit.Kit;
import io.github.aubergineux.riposte.util.BlockPos;
import io.github.aubergineux.riposte.util.Items;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns arena templates and their copies. Copies live on a grid in a void world:
 * x = copy index * spacing, z = arena grid slot * spacing, so finding the copy at a
 * location is a constant time lookup.
 */
public final class ArenaManager {

    public record Selection(String world, BlockPos pos1, BlockPos pos2) {
    }

    private final RipostePlugin plugin;
    private final YamlFile file;
    private final YamlFile stateFile;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<Integer, Arena> bySlot = new HashMap<>();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Deque<BlockJob> jobs = new ArrayDeque<>();
    private final Set<String> dirty = new HashSet<>();
    private World arenaWorld;
    private World templateWorld;
    private BukkitTask jobTask;

    public ArenaManager(RipostePlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "arenas.yml", false);
        this.stateFile = new YamlFile(plugin, "data/arena-state.yml", false);
    }

    // ------------------------------------------------------------------ lifecycle

    public void enable() {
        arenaWorld = voidWorld(plugin.settings().arenaWorld);
        templateWorld = voidWorld(plugin.settings().templateWorld);
        arenaWorld.removePluginChunkTickets(plugin);
        load();
        jobTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> processJobs(), 1L, 1L);
    }

    public void disable() {
        if (jobTask != null) {
            jobTask.cancel();
        }
        // finish pending resets synchronously so the next start is clean
        int guard = 0;
        while (!jobs.isEmpty() && guard++ < 10_000) {
            processJobs(Integer.MAX_VALUE);
        }
        saveState();
    }

    private World voidWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            world = new WorldCreator(name).generator(new VoidGenerator()).generateStructures(false).createWorld();
        }
        if (world != null) {
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setGameRule(GameRules.SHOW_DEATH_MESSAGES, false);
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRules.MOB_GRIEFING, false);
            world.setGameRule(GameRules.BLOCK_DROPS, false);
            world.setGameRule(GameRules.ENTITY_DROPS, false);
            world.setGameRule(GameRules.RESPAWN_RADIUS, 0);
            world.setGameRule(GameRules.ALLOW_ENTERING_NETHER_USING_PORTALS, false);
            world.setGameRule(GameRules.LOCATOR_BAR, false);
            world.setDifficulty(Difficulty.NORMAL);
            world.setTime(6000);
            world.setStorm(false);
            world.setAutoSave(true);
        }
        return world;
    }

    public World arenaWorld() {
        return arenaWorld;
    }

    public World templateWorld() {
        return templateWorld;
    }

    // ------------------------------------------------------------------ persistence

    public void load() {
        YamlConfiguration yaml = file.load();
        YamlConfiguration state = stateFile.load();
        arenas.clear();
        bySlot.clear();
        dirty.clear();
        dirty.addAll(state.getStringList("dirty"));
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root != null) {
            for (String name : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(name);
                if (s == null) {
                    continue;
                }
                try {
                    Arena arena = read(name, s);
                    arenas.put(arena.name().toLowerCase(Locale.ROOT), arena);
                    bySlot.put(arena.gridSlot(), arena);
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Skipping arena '" + name + "': " + e.getMessage());
                }
            }
        }
        for (Arena arena : arenas.values()) {
            String signature = signature(arena);
            int pasted = signature.equals(state.getString("pasted." + arena.name() + ".signature")) ? state.getInt("pasted." + arena.name() + ".count") : 0;
            buildCopies(arena, pasted);
        }
        dirty.clear();
        saveState();
    }

    private Arena read(String name, ConfigurationSection s) {
        Arena arena = new Arena(name);
        arena.displayName(s.getString("display-name", name));
        arena.icon(Items.material(s.getString("icon"), org.bukkit.Material.GRASS_BLOCK));
        arena.tags().clear();
        arena.tags().addAll(s.getStringList("tags"));
        if (arena.tags().isEmpty()) {
            arena.tags().add("standard");
        }
        arena.enabled(s.getBoolean("enabled", false));
        arena.gridSlot(s.getInt("grid-slot", nextSlot()));
        arena.copyCount(s.getInt("copies", 2));
        String world = s.getString("world");
        if (world != null && s.contains("min") && s.contains("max")) {
            arena.region(world, BlockPos.parse(s.getString("min")), BlockPos.parse(s.getString("max")));
        }
        arena.spawn1(readLocation(s.getString("spawn1"), world));
        arena.spawn2(readLocation(s.getString("spawn2"), world));
        if (s.contains("goal1")) {
            arena.goal1(BlockPos.parse(s.getString("goal1")));
        }
        if (s.contains("goal2")) {
            arena.goal2(BlockPos.parse(s.getString("goal2")));
        }
        return arena;
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Riposte arenas. Use /arena commands in game rather than editing coordinates by hand.",
                "tags decide which kits can use an arena (see arena-tag in kits.yml)."));
        for (Arena arena : arenas.values()) {
            ConfigurationSection s = yaml.createSection("arenas." + arena.name());
            s.set("display-name", arena.displayName());
            s.set("icon", arena.icon().name());
            s.set("tags", new ArrayList<>(arena.tags()));
            s.set("enabled", arena.enabled());
            s.set("grid-slot", arena.gridSlot());
            s.set("copies", arena.copyCount());
            if (arena.hasRegion()) {
                s.set("world", arena.templateWorld());
                s.set("min", arena.min().serialize());
                s.set("max", arena.max().serialize());
            }
            s.set("spawn1", writeLocation(arena.spawn1()));
            s.set("spawn2", writeLocation(arena.spawn2()));
            if (arena.goal1() != null) {
                s.set("goal1", arena.goal1().serialize());
            }
            if (arena.goal2() != null) {
                s.set("goal2", arena.goal2().serialize());
            }
        }
        try {
            file.file().getParentFile().mkdirs();
            yaml.save(file.file());
        } catch (java.io.IOException e) {
            plugin.getLogger().severe("Could not save arenas.yml: " + e.getMessage());
        }
    }

    private void saveState() {
        YamlConfiguration yaml = stateFile.get() == null ? new YamlConfiguration() : stateFile.get();
        yaml.set("pasted", null);
        for (Arena arena : arenas.values()) {
            int ready = 0;
            for (ArenaCopy copy : arena.copies()) {
                if (copy.state() != ArenaCopy.State.PASTING) {
                    ready++;
                } else {
                    break;
                }
            }
            yaml.set("pasted." + arena.name() + ".signature", signature(arena));
            yaml.set("pasted." + arena.name() + ".count", ready);
        }
        yaml.set("dirty", new ArrayList<>(dirty));
        String data = yaml.saveToString();
        java.io.File target = stateFile.file();
        Runnable write = () -> {
            try {
                target.getParentFile().mkdirs();
                java.nio.file.Files.writeString(target.toPath(), data);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not save arena state: " + e.getMessage());
            }
        };
        if (plugin.isEnabled() && Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        } else {
            write.run();
        }
    }

    private String signature(Arena arena) {
        if (!arena.hasRegion()) {
            return "none";
        }
        return arena.templateWorld() + "|" + arena.min().serialize() + "|" + arena.max().serialize() + "|" + arena.gridSlot() + "|" + plugin.settings().arenaSpacing;
    }

    private static Location readLocation(String s, String world) {
        if (s == null || world == null) {
            return null;
        }
        String[] p = s.split(",");
        return new Location(Bukkit.getWorld(world), Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]),
                p.length > 3 ? Float.parseFloat(p[3]) : 0f, p.length > 4 ? Float.parseFloat(p[4]) : 0f);
    }

    private static String writeLocation(Location l) {
        if (l == null) {
            return null;
        }
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f,%.1f,%.1f", l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    // ------------------------------------------------------------------ arena CRUD

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Arena> all() {
        return Collections.unmodifiableCollection(arenas.values());
    }

    public Arena create(String name) {
        Arena arena = new Arena(name);
        arena.gridSlot(nextSlot());
        arenas.put(name.toLowerCase(Locale.ROOT), arena);
        bySlot.put(arena.gridSlot(), arena);
        save();
        return arena;
    }

    public void delete(Arena arena) {
        arenas.remove(arena.name().toLowerCase(Locale.ROOT));
        bySlot.remove(arena.gridSlot());
        for (ArenaCopy copy : arena.copies()) {
            clearRegion(copy);
        }
        arena.copies().clear();
        save();
        saveState();
    }

    private int nextSlot() {
        int slot = 0;
        Set<Integer> used = new HashSet<>();
        for (Arena a : arenas.values()) {
            used.add(a.gridSlot());
        }
        while (used.contains(slot)) {
            slot++;
        }
        return slot;
    }

    /** Validates the template and (re)pastes every copy. Returns an error message key or null. */
    public String rebuild(Arena arena) {
        if (!arena.isComplete()) {
            return "arena.incomplete";
        }
        if (arena.templateWorld().equals(arenaWorld.getName())) {
            return "arena.template-in-arena-world";
        }
        int limit = plugin.settings().arenaSpacing - 16;
        if (arena.sizeX() > limit || arena.sizeZ() > limit) {
            return "arena.too-large";
        }
        for (ArenaCopy copy : arena.copies()) {
            if (copy.state() == ArenaCopy.State.IN_USE) {
                return "arena.in-use";
            }
        }
        jobs.removeIf(job -> job.copy.arena() == arena);
        buildCopies(arena, 0);
        saveState();
        return null;
    }

    /** Creates copy objects; copies beyond {@code alreadyPasted} (or dirty ones) are queued for pasting. */
    private void buildCopies(Arena arena, int alreadyPasted) {
        for (ArenaCopy old : arena.copies()) {
            if (old.index() >= arena.copyCount()) {
                clearRegion(old);
            }
        }
        arena.copies().clear();
        if (!arena.isComplete()) {
            return;
        }
        int spacing = plugin.settings().arenaSpacing;
        for (int i = 0; i < arena.copyCount(); i++) {
            BlockPos origin = new BlockPos(i * spacing, arena.min().y(), arena.gridSlot() * spacing);
            ArenaCopy copy = new ArenaCopy(arena, i, arenaWorld, origin);
            arena.copies().add(copy);
            keepLoaded(copy, true);
            if (i < alreadyPasted && !dirty.contains(copy.id())) {
                copy.state(ArenaCopy.State.AVAILABLE);
            } else {
                if (dirty.contains(copy.id())) {
                    plugin.getLogger().info("Restoring " + copy.id() + " after an unclean shutdown");
                }
                jobs.add(new PasteJob(copy));
            }
        }
    }

    /** Arena chunks stay loaded so teleporting into a match never loads chunks on the main thread. */
    private void keepLoaded(ArenaCopy copy, boolean add) {
        if (!plugin.settings().keepArenasLoaded) {
            return;
        }
        BlockPos max = copy.max();
        for (int cx = copy.origin().x() >> 4; cx <= max.x() >> 4; cx++) {
            for (int cz = copy.origin().z() >> 4; cz <= max.z() >> 4; cz++) {
                if (add) {
                    arenaWorld.addPluginChunkTicket(cx, cz, plugin);
                } else {
                    arenaWorld.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    private void clearRegion(ArenaCopy copy) {
        keepLoaded(copy, false);
        BlockPos max = copy.max();
        BlockData air = Bukkit.createBlockData(org.bukkit.Material.AIR);
        List<BlockPos> positions = new ArrayList<>();
        for (int x = copy.origin().x(); x <= max.x(); x++) {
            for (int z = copy.origin().z(); z <= max.z(); z++) {
                for (int y = copy.origin().y(); y <= max.y(); y++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        jobs.add(new FillJob(copy, positions, air));
    }

    // ------------------------------------------------------------------ copies at runtime

    /** Finds a free copy that suits the kit, preferring the named arena. Marks it in use. */
    public ArenaCopy allocate(Kit kit, String preferredArena) {
        List<Arena> candidates = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.enabled() && arena.hasTag(kit.rules().arenaTag) && arena.available() > 0) {
                if (preferredArena != null && arena.name().equalsIgnoreCase(preferredArena)) {
                    candidates.clear();
                    candidates.add(arena);
                    break;
                }
                candidates.add(arena);
            }
        }
        // if the requested arena is busy, any other suitable one will do
        if (candidates.isEmpty()) {
            return null;
        }
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        // spread load: prefer the arena with the most free copies
        candidates.sort(Comparator.comparingInt(Arena::available).reversed());
        Arena arena = candidates.getFirst();
        for (ArenaCopy copy : arena.copies()) {
            if (copy.state() == ArenaCopy.State.AVAILABLE) {
                copy.state(ArenaCopy.State.IN_USE);
                dirty.add(copy.id());
                saveState();
                return copy;
            }
        }
        return null;
    }

    public List<Arena> arenasFor(Kit kit) {
        List<Arena> out = new ArrayList<>();
        for (Arena arena : arenas.values()) {
            if (arena.enabled() && arena.hasTag(kit.rules().arenaTag) && !arena.copies().isEmpty()) {
                out.add(arena);
            }
        }
        return out;
    }

    /** Resets the copy over the next ticks and frees it afterwards. */
    public void release(ArenaCopy copy) {
        if (copy.state() != ArenaCopy.State.IN_USE) {
            return;
        }
        copy.state(ArenaCopy.State.RESETTING);
        jobs.addFirst(new ResetJob(copy, false));
    }

    /** Resets blocks immediately (between rounds) without freeing the copy. */
    public void resetNow(ArenaCopy copy) {
        new ResetJob(copy, true).run(Integer.MAX_VALUE);
    }

    public ArenaCopy copyAt(Location location) {
        if (location.getWorld() != arenaWorld) {
            return null;
        }
        return copyAt(location.getBlockX(), location.getBlockY(), location.getBlockZ(), 16);
    }

    public ArenaCopy copyAt(int x, int y, int z, int verticalSlack) {
        int spacing = plugin.settings().arenaSpacing;
        Arena arena = bySlot.get(Math.floorDiv(z, spacing));
        if (arena == null) {
            return null;
        }
        int index = Math.floorDiv(x, spacing);
        if (index < 0 || index >= arena.copies().size()) {
            return null;
        }
        ArenaCopy copy = arena.copies().get(index);
        return copy.contains(x, y, z, verticalSlack) ? copy : null;
    }

    public int pendingJobs() {
        return jobs.size();
    }

    public int totalCopies() {
        int total = 0;
        for (Arena arena : arenas.values()) {
            total += arena.copies().size();
        }
        return total;
    }

    // ------------------------------------------------------------------ wand selections

    public Selection selection(UUID player) {
        return selections.get(player);
    }

    public void select(Player player, int corner, BlockPos pos) {
        Selection old = selections.get(player.getUniqueId());
        String world = player.getWorld().getName();
        if (old == null || !old.world().equals(world)) {
            old = new Selection(world, null, null);
        }
        selections.put(player.getUniqueId(), corner == 1 ? new Selection(world, pos, old.pos2()) : new Selection(world, old.pos1(), pos));
    }

    public void clearSelection(UUID player) {
        selections.remove(player);
    }

    // ------------------------------------------------------------------ block jobs

    private void processJobs() {
        processJobs(plugin.settings().pasteBlocksPerTick);
    }

    /** Deadline for the current tick's block work; jobs check it every few hundred blocks. */
    private long deadline = Long.MAX_VALUE;

    private boolean outOfTime(int done) {
        return (done & 255) == 255 && System.nanoTime() > deadline;
    }

    private void processJobs(int budget) {
        deadline = budget == Integer.MAX_VALUE ? Long.MAX_VALUE : System.nanoTime() + plugin.settings().arenaMillisPerTick * 1_000_000L;
        int waiting = 0;
        while (budget > 0 && !jobs.isEmpty() && waiting < jobs.size()) {
            BlockJob job = jobs.peekFirst();
            int used = job.run(budget);
            if (job.done()) {
                jobs.pollFirst();
                job.finish();
                waiting = 0;
            } else if (used == 0) {
                // still waiting for chunks; try the next job
                jobs.addLast(jobs.pollFirst());
                waiting++;
            }
            budget -= used;
            if (System.nanoTime() > deadline) {
                break;
            }
        }
    }

    private abstract static class BlockJob {
        final ArenaCopy copy;

        BlockJob(ArenaCopy copy) {
            this.copy = copy;
        }

        /** Processes up to budget blocks and returns how many were processed. */
        abstract int run(int budget);

        abstract boolean done();

        abstract void finish();
    }

    private final class PasteJob extends BlockJob {
        private final World source;
        private final BlockPos min;
        private final BlockPos max;
        private int x;
        private int y;
        private int z;
        private boolean finished;
        private java.util.concurrent.CompletableFuture<Void> chunksReady;

        PasteJob(ArenaCopy copy) {
            super(copy);
            this.source = copy.arena().templateWorldInstance();
            this.min = copy.arena().min();
            this.max = copy.arena().max();
            this.x = min.x();
            this.y = min.y();
            this.z = min.z();
            copy.state(ArenaCopy.State.PASTING);
            if (source == null) {
                plugin.getLogger().warning("Template world '" + copy.arena().templateWorld() + "' for arena " + copy.arena().name() + " is not loaded");
                finished = true;
            }
        }

        /** Loads source and target chunks off the main thread before the first block is copied. */
        private boolean chunksLoaded() {
            if (chunksReady == null) {
                List<java.util.concurrent.CompletableFuture<?>> futures = new ArrayList<>();
                BlockPos origin = copy.origin();
                BlockPos targetMax = copy.max();
                for (int cx = min.x() >> 4; cx <= max.x() >> 4; cx++) {
                    for (int cz = min.z() >> 4; cz <= max.z() >> 4; cz++) {
                        futures.add(source.getChunkAtAsync(cx, cz));
                    }
                }
                for (int cx = origin.x() >> 4; cx <= targetMax.x() >> 4; cx++) {
                    for (int cz = origin.z() >> 4; cz <= targetMax.z() >> 4; cz++) {
                        futures.add(copy.world().getChunkAtAsync(cx, cz));
                    }
                }
                chunksReady = java.util.concurrent.CompletableFuture.allOf(futures.toArray(new java.util.concurrent.CompletableFuture[0]));
            }
            return chunksReady.isDone();
        }

        @Override
        int run(int budget) {
            if (!finished && !chunksLoaded()) {
                // let other jobs use this tick while chunks load
                return 0;
            }
            int done = 0;
            BlockPos origin = copy.origin();
            int dx = origin.x() - min.x();
            int dy = origin.y() - min.y();
            int dz = origin.z() - min.z();
            while (!finished && done < budget && !outOfTime(done)) {
                Block from = source.getBlockAt(x, y, z);
                Block to = copy.world().getBlockAt(x + dx, y + dy, z + dz);
                BlockData data = from.getBlockData();
                if (!to.getBlockData().equals(data)) {
                    to.setBlockData(data, false);
                }
                done++;
                if (++y > max.y()) {
                    y = min.y();
                    if (++z > max.z()) {
                        z = min.z();
                        if (++x > max.x()) {
                            finished = true;
                        }
                    }
                }
            }
            return Math.max(1, done);
        }

        @Override
        boolean done() {
            return finished;
        }

        @Override
        void finish() {
            removeEntities(copy);
            copy.tracker().drain();
            copy.state(ArenaCopy.State.AVAILABLE);
            dirty.remove(copy.id());
            boolean last = jobs.stream().noneMatch(j -> j instanceof PasteJob && j.copy.arena() == copy.arena());
            if (last) {
                plugin.getLogger().info("Arena " + copy.arena().name() + " is ready (" + copy.arena().copies().size() + " copies)");
                saveState();
            }
        }
    }

    private final class ResetJob extends BlockJob {
        private final boolean keepInUse;
        private final List<BlockChangeTracker.Change<BlockData>> changes;
        private int index;

        ResetJob(ArenaCopy copy, boolean keepInUse) {
            super(copy);
            this.keepInUse = keepInUse;
            this.changes = copy.tracker().drain();
        }

        @Override
        int run(int budget) {
            int done = 0;
            while (index < changes.size() && done < budget && !outOfTime(done)) {
                BlockChangeTracker.Change<BlockData> change = changes.get(index++);
                BlockPos pos = BlockPos.unpack(change.position());
                copy.world().getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(change.original(), false);
                done++;
            }
            if (keepInUse && index >= changes.size()) {
                removeEntities(copy);
            }
            return Math.max(1, done);
        }

        @Override
        boolean done() {
            return index >= changes.size();
        }

        @Override
        void finish() {
            removeEntities(copy);
            if (!keepInUse) {
                copy.state(ArenaCopy.State.AVAILABLE);
                dirty.remove(copy.id());
                saveState();
            }
        }
    }

    private final class FillJob extends BlockJob {
        private final List<BlockPos> positions;
        private final BlockData data;
        private int index;

        FillJob(ArenaCopy copy, List<BlockPos> positions, BlockData data) {
            super(copy);
            this.positions = positions;
            this.data = data;
        }

        @Override
        int run(int budget) {
            int done = 0;
            while (index < positions.size() && done < budget && !outOfTime(done)) {
                BlockPos pos = positions.get(index++);
                Block block = copy.world().getBlockAt(pos.x(), pos.y(), pos.z());
                if (!block.getType().isAir()) {
                    block.setBlockData(data, false);
                }
                done++;
            }
            return Math.max(1, done);
        }

        @Override
        boolean done() {
            return index >= positions.size();
        }

        @Override
        void finish() {
            removeEntities(copy);
        }
    }

    /** Removes drops, arrows, crystals, TNT and anything else left behind. Players are never touched. */
    public void removeEntities(ArenaCopy copy) {
        BlockPos min = copy.origin();
        BlockPos max = copy.max();
        BoundingBox box = new BoundingBox(min.x() - 2, min.y() - 64, min.z() - 2, max.x() + 3, max.y() + 64, max.z() + 3);
        for (Entity entity : copy.world().getNearbyEntities(box)) {
            if (!(entity instanceof Player)) {
                entity.remove();
            }
        }
    }
}
