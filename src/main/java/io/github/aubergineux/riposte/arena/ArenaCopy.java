package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.util.BlockPos;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/** One playable instance of an arena, pasted into the arena world. */
public final class ArenaCopy {

    public enum State {
        PASTING, AVAILABLE, IN_USE, RESETTING
    }

    private final Arena arena;
    private final int index;
    private final World world;
    private final BlockPos origin;
    private final BlockChangeTracker<BlockData> tracker = new BlockChangeTracker<>();
    private State state = State.PASTING;

    public ArenaCopy(Arena arena, int index, World world, BlockPos origin) {
        this.arena = arena;
        this.index = index;
        this.world = world;
        this.origin = origin;
    }

    public Arena arena() {
        return arena;
    }

    public int index() {
        return index;
    }

    public World world() {
        return world;
    }

    public BlockPos origin() {
        return origin;
    }

    public BlockPos max() {
        return translate(arena.max());
    }

    public BlockChangeTracker<BlockData> tracker() {
        return tracker;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    public String id() {
        return arena.name() + "#" + (index + 1);
    }

    private int dx() {
        return origin.x() - arena.min().x();
    }

    private int dy() {
        return origin.y() - arena.min().y();
    }

    private int dz() {
        return origin.z() - arena.min().z();
    }

    public BlockPos translate(BlockPos templatePos) {
        return templatePos.offset(dx(), dy(), dz());
    }

    public Location translate(Location template) {
        return new Location(world, template.getX() + dx(), template.getY() + dy(), template.getZ() + dz(), template.getYaw(), template.getPitch());
    }

    public Location spawn(int team) {
        return translate(team == 0 ? arena.spawn1() : arena.spawn2());
    }

    public Location center() {
        Location a = spawn(0);
        Location b = spawn(1);
        return new Location(world, (a.getX() + b.getX()) / 2, Math.max(a.getY(), b.getY()) + 2, (a.getZ() + b.getZ()) / 2);
    }

    public BlockPos goal(int team) {
        BlockPos goal = team == 0 ? arena.goal1() : arena.goal2();
        return goal == null ? null : translate(goal);
    }

    public boolean contains(int x, int y, int z, int verticalSlack) {
        BlockPos max = max();
        return x >= origin.x() && x <= max.x() && z >= origin.z() && z <= max.z()
                && y >= origin.y() - verticalSlack && y <= max.y() + verticalSlack;
    }

    public boolean contains(Location location) {
        return location.getWorld() == world && contains(location.getBlockX(), location.getBlockY(), location.getBlockZ(), 0);
    }

    public int floorY() {
        return origin.y();
    }

    /** Highest Y blocks may be placed at for kits with the given build height. */
    public int buildLimit(int buildHeight) {
        int spawnY = (int) Math.max(spawn(0).getY(), spawn(1).getY());
        return Math.min(max().y(), spawnY + buildHeight);
    }
}
