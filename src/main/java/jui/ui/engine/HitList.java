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
    /** The pointer's shape over each entry: 0 unset, else a Cursor's ordinal plus 1. */
    byte[] cursor = new byte[64];
    /** The clip of each entry's clipping ancestors, intersected; infinite for none. */
    float[] clipX0 = new float[64], clipY0 = new float[64], clipX1 = new float[64], clipY1 = new float[64];
    /**
     * Each entry's innermost clip, when it rounds its corners: an index into
     * the rounded clips, else -1. Only the innermost clip rounds, as paint.
     */
    int[] round = new int[64];
    int size;

    /** The rounded clips: rect then corners (top-left, top-right, bottom-right, bottom-left), 8 floats each. */
    float[] rounds = new float[8 * 8];
    int roundCount;

    void clear() {
        size = 0;
        roundCount = 0;
    }

    /** Adds a rounded clip, and returns its index for {@link #add}. */
    int addRound(float rx, float ry, float rw, float rh, float tl, float tr, float br, float bl) {
        if ((roundCount + 1) * 8 > rounds.length) {
            rounds = Arrays.copyOf(rounds, rounds.length * 2);
        }
        int o = roundCount * 8;
        rounds[o] = rx;
        rounds[o + 1] = ry;
        rounds[o + 2] = rw;
        rounds[o + 3] = rh;
        rounds[o + 4] = tl;
        rounds[o + 5] = tr;
        rounds[o + 6] = br;
        rounds[o + 7] = bl;
        return roundCount++;
    }

    void add(long id, float bx, float by, float bw, float bh, int parentIndex, int nodeFlags, byte nodeCursor,
             float cx0, float cy0, float cx1, float cy1, int roundClip) {
        if (size == ids.length) {
            int n = size * 2;
            ids = Arrays.copyOf(ids, n);
            x = Arrays.copyOf(x, n);
            y = Arrays.copyOf(y, n);
            w = Arrays.copyOf(w, n);
            h = Arrays.copyOf(h, n);
            parent = Arrays.copyOf(parent, n);
            flags = Arrays.copyOf(flags, n);
            cursor = Arrays.copyOf(cursor, n);
            clipX0 = Arrays.copyOf(clipX0, n);
            clipY0 = Arrays.copyOf(clipY0, n);
            clipX1 = Arrays.copyOf(clipX1, n);
            clipY1 = Arrays.copyOf(clipY1, n);
            round = Arrays.copyOf(round, n);
        }
        ids[size] = id;
        x[size] = bx;
        y[size] = by;
        w[size] = bw;
        h[size] = bh;
        parent[size] = parentIndex;
        flags[size] = nodeFlags;
        cursor[size] = nodeCursor;
        clipX0[size] = cx0;
        clipY0[size] = cy0;
        clipX1[size] = cx1;
        clipY1[size] = cy1;
        round[size] = roundClip;
        size++;
    }

    /** Whether the point is in entry i's box and inside its ancestors' clips, the innermost's corners too. */
    boolean contains(int i, float px, float py) {
        return px >= x[i] && px < x[i] + w[i] && py >= y[i] && py < y[i] + h[i]
                && px >= clipX0[i] && px < clipX1[i] && py >= clipY0[i] && py < clipY1[i]
                && (round[i] < 0 || inRound(round[i] * 8, px, py));
    }

    /** Whether the point is inside the rounded clip at o, by the shader's distance (sdRoundRect). */
    private boolean inRound(int o, float px, float py) {
        float hw = rounds[o + 2] / 2, hh = rounds[o + 3] / 2;
        float qx = px - rounds[o] - hw, qy = py - rounds[o + 1] - hh;
        float r = qx < 0 ? (qy < 0 ? rounds[o + 4] : rounds[o + 7]) : (qy < 0 ? rounds[o + 5] : rounds[o + 6]);
        float ax = Math.abs(qx) - hw + r, ay = Math.abs(qy) - hh + r;
        float mx = Math.max(ax, 0), my = Math.max(ay, 0);
        float d = (float) Math.sqrt(mx * mx + my * my) + Math.min(Math.max(ax, ay), 0) - r;
        return d <= 0;
    }

    /** Whether entry i's box shows: it meets its ancestors' clips. */
    boolean shown(int i) {
        return x[i] < clipX1[i] && x[i] + w[i] > clipX0[i] && y[i] < clipY1[i] && y[i] + h[i] > clipY0[i];
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
