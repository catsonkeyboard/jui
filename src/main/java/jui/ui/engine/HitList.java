package jui.ui.engine;

import java.util.Arrays;

/**
 * What the last frame committed for pointer input: every node's identity,
 * box, parent and flags, in paint order (the pool's order, as M2 has no
 * absolute positioning). The arrays only grow.
 */
final class HitList {
    /** A flag the hit list adds to the node's own: the element is disabled. */
    static final int DISABLED = 1 << 30;

    long[] ids = new long[64];
    float[] x = new float[64], y = new float[64], w = new float[64], h = new float[64];
    int[] parent = new int[64];
    int[] flags = new int[64];
    int size;

    void clear() {
        size = 0;
    }

    void add(long id, float bx, float by, float bw, float bh, int parentIndex, int nodeFlags) {
        if (size == ids.length) {
            int n = size * 2;
            ids = Arrays.copyOf(ids, n);
            x = Arrays.copyOf(x, n);
            y = Arrays.copyOf(y, n);
            w = Arrays.copyOf(w, n);
            h = Arrays.copyOf(h, n);
            parent = Arrays.copyOf(parent, n);
            flags = Arrays.copyOf(flags, n);
        }
        ids[size] = id;
        x[size] = bx;
        y[size] = by;
        w[size] = bw;
        h[size] = bh;
        parent[size] = parentIndex;
        flags[size] = nodeFlags;
        size++;
    }

    boolean contains(int i, float px, float py) {
        return px >= x[i] && px < x[i] + w[i] && py >= y[i] && py < y[i] + h[i];
    }

    /** The index of identity id, or -1. */
    int indexOf(long id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The hit chain at (px, py), innermost first, as indices into out: the
     * last element in paint order whose box holds the point, then its
     * ancestors. Returns its length; out grows as needed and is returned
     * through holder[0].
     */
    int chain(float px, float py, int[][] holder) {
        int hit = -1;
        for (int i = size - 1; i >= 0; i--) {
            if (contains(i, px, py)) {
                hit = i;
                break;
            }
        }
        int n = 0;
        for (int i = hit; i >= 0; i = parent[i]) {
            if (n == holder[0].length) {
                holder[0] = Arrays.copyOf(holder[0], n * 2);
            }
            holder[0][n++] = i;
        }
        return n;
    }
}
