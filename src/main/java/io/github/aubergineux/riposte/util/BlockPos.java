package io.github.aubergineux.riposte.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Immutable integer block position, also usable as a packed long key. */
public record BlockPos(int x, int y, int z) {

    public static BlockPos of(Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public static BlockPos of(Block block) {
        return new BlockPos(block.getX(), block.getY(), block.getZ());
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }

    public Block block(World world) {
        return world.getBlockAt(x, y, z);
    }

    /** Packs into a long using the same layout as vanilla (26 bits x, 26 bits z, 12 bits y). */
    public long pack() {
        return pack(x, y, z);
    }

    public static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    public static BlockPos unpack(long packed) {
        int x = (int) (packed >> 38);
        int y = (int) (packed << 52 >> 52);
        int z = (int) (packed << 26 >> 38);
        return new BlockPos(x, y, z);
    }

    public String serialize() {
        return x + "," + y + "," + z;
    }

    public static BlockPos parse(String s) {
        String[] parts = s.split(",");
        return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
    }
}
