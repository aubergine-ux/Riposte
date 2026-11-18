package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.util.BlockPos;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;
import java.util.Locale;

/**
 * Builds ready-to-play arena templates so a fresh server has somewhere to fight without any building.
 * Templates are placed in the template world at x = 0, z = grid slot * spacing.
 */
public final class ArenaGenerator {

    public enum Style {
        CLASSIC("standard", Material.STONE_BRICKS),
        SUMO("sumo", Material.SLIME_BLOCK),
        BRIDGE("bridge", Material.BLUE_TERRACOTTA);

        public final String tag;
        public final Material icon;

        Style(String tag, Material icon) {
            this.tag = tag;
            this.icon = icon;
        }

        public static Style parse(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final int BASE_Y = 100;

    private ArenaGenerator() {
    }

    public static void generate(Arena arena, Style style, World world, int spacing) {
        int ox = 0;
        int oz = arena.gridSlot() * spacing;
        switch (style) {
            case CLASSIC -> classic(arena, world, ox, oz);
            case SUMO -> sumo(arena, world, ox, oz);
            case BRIDGE -> bridge(arena, world, ox, oz);
        }
        arena.tags().clear();
        arena.tags().add(style.tag);
        arena.icon(style.icon);
    }

    // 51x51 floor, low walls, barrier sides so pearls cannot escape
    private static void classic(Arena arena, World w, int ox, int oz) {
        int size = 51;
        int top = BASE_Y + 24;
        clear(w, ox, BASE_Y - 4, oz, ox + size - 1, top, oz + size - 1);
        List<Material> floor = List.of(Material.SMOOTH_STONE, Material.POLISHED_ANDESITE, Material.STONE_BRICKS, Material.ANDESITE);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int ring = Math.max(Math.abs(x - size / 2), Math.abs(z - size / 2));
                Material m = floor.get(Math.floorMod((x * 7 + z * 13) ^ (x * z), floor.size()));
                if (ring <= 3) {
                    m = ring == 3 ? Material.POLISHED_DEEPSLATE : Material.CHISELED_STONE_BRICKS;
                }
                set(w, ox + x, BASE_Y, oz + z, m);
                set(w, ox + x, BASE_Y - 1, oz + z, Material.STONE);
                set(w, ox + x, BASE_Y - 2, oz + z, Material.STONE);
                set(w, ox + x, BASE_Y - 3, oz + z, Material.BEDROCK);
                boolean edge = x == 0 || z == 0 || x == size - 1 || z == size - 1;
                if (edge) {
                    for (int y = BASE_Y + 1; y <= BASE_Y + 3; y++) {
                        set(w, ox + x, y, oz + z, y == BASE_Y + 3 ? Material.STONE_BRICK_SLAB : Material.STONE_BRICKS);
                    }
                    for (int y = BASE_Y + 4; y <= top; y++) {
                        set(w, ox + x, y, oz + z, Material.BARRIER);
                    }
                }
            }
        }
        // corner pillars with lanterns
        for (int[] c : new int[][]{{0, 0}, {size - 1, 0}, {0, size - 1}, {size - 1, size - 1}}) {
            for (int y = BASE_Y + 1; y <= BASE_Y + 5; y++) {
                set(w, ox + c[0], y, oz + c[1], Material.POLISHED_DEEPSLATE);
            }
            set(w, ox + c[0], BASE_Y + 6, oz + c[1], Material.SEA_LANTERN);
        }
        arena.region(w.getName(), new BlockPos(ox, BASE_Y - 3, oz), new BlockPos(ox + size - 1, top, oz + size - 1));
        arena.spawn1(new Location(w, ox + 10.5, BASE_Y + 1, oz + size / 2 + 0.5, -90, 0));
        arena.spawn2(new Location(w, ox + size - 10.5, BASE_Y + 1, oz + size / 2 + 0.5, 90, 0));
    }

    // round platform over a water pool; falling in loses the round
    private static void sumo(Arena arena, World w, int ox, int oz) {
        int size = 31;
        int c = size / 2;
        int top = BASE_Y + 12;
        clear(w, ox, BASE_Y - 4, oz, ox + size - 1, top, oz + size - 1);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double d = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                set(w, ox + x, BASE_Y - 4, oz + z, Material.PRISMARINE_BRICKS);
                boolean wall = x == 0 || z == 0 || x == size - 1 || z == size - 1;
                if (wall) {
                    for (int y = BASE_Y - 3; y <= BASE_Y - 1; y++) {
                        set(w, ox + x, y, oz + z, Material.DARK_PRISMARINE);
                    }
                    for (int y = BASE_Y; y <= top; y++) {
                        set(w, ox + x, y, oz + z, Material.BARRIER);
                    }
                } else {
                    set(w, ox + x, BASE_Y - 3, oz + z, Material.WATER);
                    set(w, ox + x, BASE_Y - 2, oz + z, Material.WATER);
                }
                if (d <= 7.5) {
                    Material m = d <= 1.5 ? Material.RED_GLAZED_TERRACOTTA : (d > 6.5 ? Material.QUARTZ_BRICKS : Material.SMOOTH_QUARTZ);
                    set(w, ox + x, BASE_Y, oz + z, m);
                    set(w, ox + x, BASE_Y - 1, oz + z, Material.QUARTZ_PILLAR);
                }
            }
        }
        for (int[] p : new int[][]{{2, 2}, {size - 3, 2}, {2, size - 3}, {size - 3, size - 3}}) {
            set(w, ox + p[0], BASE_Y - 1, oz + p[1], Material.SEA_LANTERN);
        }
        arena.region(w.getName(), new BlockPos(ox, BASE_Y - 4, oz), new BlockPos(ox + size - 1, top, oz + size - 1));
        arena.spawn1(new Location(w, ox + c - 3 + 0.5, BASE_Y + 1, oz + c + 0.5, -90, 0));
        arena.spawn2(new Location(w, ox + c + 3 + 0.5, BASE_Y + 1, oz + c + 0.5, 90, 0));
    }

    // two bases with goals, joined by a one block wide line players build around
    private static void bridge(Arena arena, World w, int ox, int oz) {
        int length = 71;
        int width = 21;
        int c = width / 2;
        int top = BASE_Y + 14;
        int bottom = BASE_Y - 8;
        clear(w, ox, bottom, oz, ox + length - 1, top, oz + width - 1);
        base(w, ox + 2, oz + c, 3, Material.RED_TERRACOTTA, Material.RED_STAINED_GLASS);
        base(w, ox + length - 13, oz + c, 7, Material.BLUE_TERRACOTTA, Material.BLUE_STAINED_GLASS);
        for (int x = 13; x <= length - 14; x++) {
            set(w, ox + x, BASE_Y, oz + c, Material.WHITE_TERRACOTTA);
        }
        // small middle island
        for (int x = c - 2; x <= c + 2; x++) {
            for (int z = c - 2; z <= c + 2; z++) {
                set(w, ox + length / 2 + (x - c), BASE_Y, oz + z, Material.WHITE_TERRACOTTA);
                set(w, ox + length / 2 + (x - c), BASE_Y - 1, oz + z, Material.LIGHT_GRAY_TERRACOTTA);
            }
        }
        arena.region(w.getName(), new BlockPos(ox, bottom, oz), new BlockPos(ox + length - 1, top, oz + width - 1));
        // spawns stand in front of their own goal, facing the enemy
        arena.spawn1(new Location(w, ox + 10.5, BASE_Y + 1, oz + c + 0.5, -90, 0));
        arena.spawn2(new Location(w, ox + length - 10.5, BASE_Y + 1, oz + c + 0.5, 90, 0));
        // goal centre, one block below the surface
        arena.goal1(new BlockPos(ox + 5, BASE_Y - 1, oz + c));
        arena.goal2(new BlockPos(ox + length - 6, BASE_Y - 1, oz + c));
    }

    private static void base(World w, int x0, int zc, int goalOffset, Material floor, Material glass) {
        for (int x = 0; x < 11; x++) {
            for (int dz = -5; dz <= 5; dz++) {
                set(w, x0 + x, BASE_Y, zc + dz, floor);
                set(w, x0 + x, BASE_Y - 1, zc + dz, Material.STONE);
                set(w, x0 + x, BASE_Y - 2, zc + dz, floor);
            }
        }
        // 3x3 goal pit, lined with glass
        int gx = x0 + goalOffset;
        for (int x = -1; x <= 1; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(w, gx + x, BASE_Y, zc + dz, Material.AIR);
                set(w, gx + x, BASE_Y - 1, zc + dz, Material.AIR);
                set(w, gx + x, BASE_Y - 2, zc + dz, glass);
            }
        }
    }

    private static void clear(World w, int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = y1; y <= y2; y++) {
                    if (!w.getBlockAt(x, y, z).getType().isAir()) {
                        w.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private static void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }
}
