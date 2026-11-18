package io.github.aubergineux.riposte.kit;

import org.bukkit.inventory.ItemStack;

/**
 * A layout is a permutation of the 41 inventory slots: layout[kitSlot] = playerSlot.
 * Storing a permutation (instead of items) means admins can tweak enchantments or potion
 * counts later and every player's arrangement still applies.
 */
public final class KitLayouts {

    private KitLayouts() {
    }

    public static ItemStack[] arrange(ItemStack[] contents, int[] layout) {
        if (!isValid(layout)) {
            return contents.clone();
        }
        ItemStack[] out = new ItemStack[Kit.SIZE];
        for (int i = 0; i < Kit.SIZE; i++) {
            out[layout[i]] = contents[i];
        }
        return out;
    }

    public static boolean isValid(int[] layout) {
        if (layout == null || layout.length != Kit.SIZE) {
            return false;
        }
        boolean[] seen = new boolean[Kit.SIZE];
        for (int i = 0; i < Kit.SIZE; i++) {
            int target = layout[i];
            if (target < 0 || target >= Kit.SIZE || seen[target]) {
                return false;
            }
            // armor never moves
            if ((i >= Kit.ARMOR_START && i < Kit.OFFHAND) != (target >= Kit.ARMOR_START && target < Kit.OFFHAND)) {
                return false;
            }
            if (i >= Kit.ARMOR_START && i < Kit.OFFHAND && target != i) {
                return false;
            }
            seen[target] = true;
        }
        return true;
    }

    /**
     * Works out the permutation that turns the kit's default contents into what the player arranged.
     * Returns null when the arrangement contains items that do not belong to the kit.
     */
    public static int[] compute(ItemStack[] kitContents, ItemStack[] arranged) {
        int[] layout = new int[Kit.SIZE];
        boolean[] used = new boolean[Kit.SIZE];
        boolean[] assigned = new boolean[Kit.SIZE];

        // armor is fixed
        for (int i = Kit.ARMOR_START; i < Kit.OFFHAND; i++) {
            layout[i] = i;
            used[i] = true;
            assigned[i] = true;
        }
        // first pass: items that did not move keep their slot, which keeps duplicates stable
        for (int i = 0; i < Kit.SIZE; i++) {
            if (assigned[i] || isEmpty(kitContents[i])) {
                continue;
            }
            if (same(kitContents[i], arranged[i])) {
                layout[i] = i;
                used[i] = true;
                assigned[i] = true;
            }
        }
        // second pass: find where each remaining item went
        for (int i = 0; i < Kit.SIZE; i++) {
            if (assigned[i] || isEmpty(kitContents[i])) {
                continue;
            }
            int found = -1;
            for (int j = 0; j < Kit.SIZE; j++) {
                if (!used[j] && !(j >= Kit.ARMOR_START && j < Kit.OFFHAND) && same(kitContents[i], arranged[j])) {
                    found = j;
                    break;
                }
            }
            if (found < 0) {
                return null;
            }
            layout[i] = found;
            used[found] = true;
            assigned[i] = true;
        }
        // every non-empty slot in the arrangement must now be accounted for
        for (int j = 0; j < Kit.SIZE; j++) {
            if (!used[j] && !isEmpty(arranged[j])) {
                return null;
            }
        }
        // empty kit slots fill the remaining empty player slots in order
        int next = 0;
        for (int i = 0; i < Kit.SIZE; i++) {
            if (assigned[i]) {
                continue;
            }
            while (used[next] || (next >= Kit.ARMOR_START && next < Kit.OFFHAND)) {
                next++;
            }
            layout[i] = next;
            used[next] = true;
        }
        return isValid(layout) ? layout : null;
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.isEmpty();
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        return a.getAmount() == b.getAmount() && a.isSimilar(b);
    }
}
