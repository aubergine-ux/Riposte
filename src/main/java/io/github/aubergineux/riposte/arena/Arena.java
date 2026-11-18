package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.util.BlockPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * An arena template: a region an admin built (or Riposte generated) plus spawns.
 * Matches never run in the template itself, only in its copies.
 */
public final class Arena {

    private final String name;
    private String displayName;
    private Material icon = Material.GRASS_BLOCK;
    private final Set<String> tags = new LinkedHashSet<>();
    private boolean enabled;
    private String templateWorld;
    private BlockPos min;
    private BlockPos max;
    private Location spawn1;
    private Location spawn2;
    private BlockPos goal1;
    private BlockPos goal2;
    private int gridSlot;
    private int copyCount = 2;
    private final List<ArenaCopy> copies = new ArrayList<>();

    public Arena(String name) {
        this.name = name;
        this.tags.add("standard");
    }

    public String name() {
        return name;
    }

    public String displayName() {
        return displayName == null ? name : displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    public Material icon() {
        return icon;
    }

    public void icon(Material icon) {
        this.icon = icon;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean enabled() {
        return enabled;
    }

    public void enabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String templateWorld() {
        return templateWorld;
    }

    public void region(String world, BlockPos a, BlockPos b) {
        this.templateWorld = world;
        this.min = BlockPos.min(a, b);
        this.max = BlockPos.max(a, b);
    }

    public BlockPos min() {
        return min;
    }

    public BlockPos max() {
        return max;
    }

    public Location spawn1() {
        return spawn1;
    }

    public void spawn1(Location spawn1) {
        this.spawn1 = spawn1;
    }

    public Location spawn2() {
        return spawn2;
    }

    public void spawn2(Location spawn2) {
        this.spawn2 = spawn2;
    }

    public BlockPos goal1() {
        return goal1;
    }

    public void goal1(BlockPos goal1) {
        this.goal1 = goal1;
    }

    public BlockPos goal2() {
        return goal2;
    }

    public void goal2(BlockPos goal2) {
        this.goal2 = goal2;
    }

    public int gridSlot() {
        return gridSlot;
    }

    public void gridSlot(int gridSlot) {
        this.gridSlot = gridSlot;
    }

    public int copyCount() {
        return copyCount;
    }

    public void copyCount(int copyCount) {
        this.copyCount = Math.max(1, copyCount);
    }

    public List<ArenaCopy> copies() {
        return copies;
    }

    public boolean hasRegion() {
        return templateWorld != null && min != null && max != null;
    }

    public boolean isComplete() {
        return hasRegion() && spawn1 != null && spawn2 != null && contains(spawn1) && contains(spawn2);
    }

    public boolean contains(Location location) {
        if (location == null || min == null) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= min.x() && x <= max.x() && y >= min.y() - 1 && y <= max.y() + 1 && z >= min.z() && z <= max.z();
    }

    public int sizeX() {
        return max.x() - min.x() + 1;
    }

    public int sizeY() {
        return max.y() - min.y() + 1;
    }

    public int sizeZ() {
        return max.z() - min.z() + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public int available() {
        int count = 0;
        for (ArenaCopy copy : copies) {
            if (copy.state() == ArenaCopy.State.AVAILABLE) {
                count++;
            }
        }
        return count;
    }

    public World templateWorldInstance() {
        return templateWorld == null ? null : org.bukkit.Bukkit.getWorld(templateWorld);
    }
}
