package io.github.aubergineux.riposte.arena;

import io.github.aubergineux.riposte.util.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChangeTrackerTest {

    @Test
    void keepsOnlyTheFirstOriginalPerPosition() {
        BlockChangeTracker<String> tracker = new BlockChangeTracker<>();
        long pos = BlockPos.pack(10, 64, -5);
        assertTrue(tracker.record(pos, () -> "stone"));
        assertFalse(tracker.record(pos, () -> "cobblestone"));
        assertFalse(tracker.record(pos, () -> "air"));
        List<BlockChangeTracker.Change<String>> changes = tracker.drain();
        assertEquals(1, changes.size());
        assertEquals("stone", changes.getFirst().original());
    }

    @Test
    void doesNotEvaluateOriginalForKnownPositions() {
        BlockChangeTracker<String> tracker = new BlockChangeTracker<>();
        AtomicInteger reads = new AtomicInteger();
        long pos = BlockPos.pack(0, 0, 0);
        tracker.record(pos, () -> {
            reads.incrementAndGet();
            return "grass";
        });
        for (int i = 0; i < 100; i++) {
            tracker.record(pos, () -> {
                reads.incrementAndGet();
                return "dirt";
            });
        }
        assertEquals(1, reads.get());
    }

    @Test
    void drainRestoresEverythingAndClears() {
        // simulate a world as a map: place and break blocks, then restore from the tracker
        Map<Long, String> world = new HashMap<>();
        BlockChangeTracker<String> tracker = new BlockChangeTracker<>();
        for (int x = 0; x < 20; x++) {
            world.put(BlockPos.pack(x, 100, 0), "floor");
        }
        Map<Long, String> before = new HashMap<>(world);

        for (int x = 0; x < 20; x += 2) {
            long key = BlockPos.pack(x, 100, 0);
            tracker.record(key, () -> world.get(key));
            world.put(key, "air");
        }
        for (int x = 0; x < 10; x++) {
            long key = BlockPos.pack(x, 101, 0);
            tracker.record(key, () -> world.getOrDefault(key, "air"));
            world.put(key, "cobblestone");
            tracker.markPlaced(key);
        }
        assertEquals(20, tracker.size());
        for (BlockChangeTracker.Change<String> change : tracker.drain()) {
            if (change.original().equals("air")) {
                world.remove(change.position());
            } else {
                world.put(change.position(), change.original());
            }
        }
        assertEquals(before, world);
        assertTrue(tracker.isEmpty());
        assertFalse(tracker.isPlaced(BlockPos.pack(0, 101, 0)));
    }

    @Test
    void placedBlocksAreTrackedSeparately() {
        BlockChangeTracker<String> tracker = new BlockChangeTracker<>();
        long placed = BlockPos.pack(1, 2, 3);
        long map = BlockPos.pack(4, 5, 6);
        tracker.record(placed, () -> "air");
        tracker.markPlaced(placed);
        tracker.record(map, () -> "stone");
        assertTrue(tracker.isPlaced(placed));
        assertFalse(tracker.isPlaced(map));
        tracker.unmarkPlaced(placed);
        assertFalse(tracker.isPlaced(placed));
        assertTrue(tracker.isTracked(placed));
    }

    @Test
    void preservesChangeOrder() {
        BlockChangeTracker<Integer> tracker = new BlockChangeTracker<>();
        for (int i = 0; i < 1000; i++) {
            final int value = i;
            tracker.record(BlockPos.pack(i, 70, i), () -> value);
        }
        List<BlockChangeTracker.Change<Integer>> changes = tracker.drain();
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, changes.get(i).original());
        }
    }

    @Test
    void packedPositionsRoundTrip() {
        int[][] samples = {{0, 0, 0}, {-1, -64, -1}, {512, 319, -1024}, {30_000_000, 100, -30_000_000}, {-123_456, -2048, 7}};
        for (int[] s : samples) {
            BlockPos pos = BlockPos.unpack(BlockPos.pack(s[0], s[1], s[2]));
            assertEquals(new BlockPos(s[0], s[1], s[2]), pos);
        }
    }

    @Test
    void packedPositionsAreUnique() {
        java.util.Set<Long> keys = new java.util.HashSet<>();
        for (int x = -8; x < 8; x++) {
            for (int y = -64; y < 320; y += 7) {
                for (int z = -8; z < 8; z++) {
                    assertTrue(keys.add(BlockPos.pack(x, y, z)));
                }
            }
        }
    }
}
