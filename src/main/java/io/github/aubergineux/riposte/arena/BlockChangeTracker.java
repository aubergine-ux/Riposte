package io.github.aubergineux.riposte.arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Remembers the original state of every block touched during a match, keyed by packed position.
 * Only the first change per position is stored, so a block placed, broken and placed again still
 * resets to what the map had. Generic so the logic can be unit tested without a server.
 */
public final class BlockChangeTracker<T> {

    public record Change<T>(long position, T original) {
    }

    private final Map<Long, T> originals = new LinkedHashMap<>();
    private final Set<Long> placed = new HashSet<>();

    /** Records the original state if this position has not been touched yet. Returns true when newly recorded. */
    public boolean record(long position, Supplier<T> original) {
        if (originals.containsKey(position)) {
            return false;
        }
        originals.put(position, original.get());
        return true;
    }

    public boolean isTracked(long position) {
        return originals.containsKey(position);
    }

    /** Marks a block as placed by a player, which makes it breakable even when map blocks are protected. */
    public void markPlaced(long position) {
        placed.add(position);
    }

    public void unmarkPlaced(long position) {
        placed.remove(position);
    }

    public boolean isPlaced(long position) {
        return placed.contains(position);
    }

    public int size() {
        return originals.size();
    }

    public boolean isEmpty() {
        return originals.isEmpty();
    }

    /** Returns every change in the order it happened and clears the tracker for the next match. */
    public List<Change<T>> drain() {
        List<Change<T>> out = new ArrayList<>(originals.size());
        for (Map.Entry<Long, T> e : originals.entrySet()) {
            out.add(new Change<>(e.getKey(), e.getValue()));
        }
        originals.clear();
        placed.clear();
        return out;
    }
}
