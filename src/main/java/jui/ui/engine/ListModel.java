package jui.ui.engine;

import java.util.Arrays;

/**
 * What a list keeps from frame to frame: its rows' heights by index (NaN
 * for rows not yet measured), the row its view is anchored to, and what
 * the app asked of it. Rows not measured are estimated at the average of
 * those measured, 32 before any is (as MyGO's listHeights).
 */
public final class ListModel {
    static final float FIRST_ESTIMATE = 32;
    /** How far from where it was a key is looked for, each way (as MyGO's listSearch). */
    static final int SEARCH = 1000;

    int count;
    private float[] heights = filled(new float[64]);
    private int known;
    private double knownSum;
    float gap;

    /** The first row in view, and its top's distance from the view's top (0 or below). */
    int anchor;
    float anchorY;

    /** Whether the view stays at the list's end: its last row meets the bottom, frame after frame. */
    boolean atEnd;
    /**
     * Whether the list follows its end (ListState.followEnd), whether it is
     * following now, and whether it has planned a pass yet (as MyGO's
     * FollowEnd and following).
     */
    boolean followEnd, following, started;
    /** Whether the last layout showed the end: no rows, or the last row's bottom within the view (as MyGO's AtEnd). */
    boolean endShown;
    /** A reveal moved the anchor in this frame: the next plan keeps it, rather than reading the offset again. */
    boolean revealed;

    /** The offset the list last wrote; an offset and a row asked for (NaN, -1 for none). */
    float wroteY;
    float requestY = Float.NaN;
    int requestRow = -1;

    /** The rows built this frame, and the view's height as last laid out. */
    int builtFirst, builtLast = -1;
    float viewH;

    /** The rows the last frame showed, or -1 before the list first shows. */
    int first = -1, last = -1;

    /** Each row's key (ListState.keys), or null: a row is known by its index. */
    private java.util.function.IntToLongFunction keyOf;
    /** The anchor row's key as last laid out, to find it again where rows came or went above it. */
    long anchorKey;
    boolean hasAnchorKey;

    /** The rows the last frame built: their identities, their indices and, when keyed, their keys. */
    long[] lastIds = new long[16];
    int[] lastRows = new int[16];
    long[] lastKeys = new long[16];
    int lastCount;
    /** A row built out of view because the focus is in it (as MyGO's focusedRow), or -1. */
    int extraRow = -1;
    /** Whether the view read first or last since the list last laid out: if they change, it needs another frame. */
    boolean rangeRead;

    public int first() {
        rangeRead = true;
        return first;
    }

    public int last() {
        rangeRead = true;
        return last;
    }

    public int builtFirst() {
        return builtFirst;
    }

    public int builtLast() {
        return builtLast;
    }

    /** Asks for row at the top of the view in the next frame. */
    public void requestRow(int row) {
        requestRow = row;
    }

    /** Follows the end: it starts there, and stays there as rows come, until the view scrolls up. */
    public void followEnd(boolean on) {
        followEnd = on;
        following = on && (!started || endShown);
    }

    /**
     * Whether the last frame showed the list's end: it has no rows, or its
     * last row's bottom is in view, as when all its rows fit. A view that
     * reads it gets another frame if it changes.
     */
    public boolean atEnd() {
        rangeRead = true;
        return endShown;
    }

    /** Asks for the end in the next frame: a list that follows its end follows it again. */
    public void requestEnd() {
        requestY = Float.MAX_VALUE;
        requestRow = -1;
    }

    /** Keys the rows: keyOf(i) is row i's key, which its identity and the anchor follow. */
    public void keys(java.util.function.IntToLongFunction keyOf) {
        this.keyOf = keyOf; // set again on every build, it keeps the anchor's key: a key not found falls back to the index
    }

    public java.util.function.IntToLongFunction keyOf() {
        return keyOf;
    }

    /** The row built out of view because the focus is in it, or -1. */
    public int extraRow() {
        return extraRow;
    }

    /** The row of n whose key is key, looking out from near, each way in turn, as far as SEARCH; -1 if none. */
    int find(long key, int near, int n) {
        for (int d = 0; d <= SEARCH; d++) {
            int lo = near - d, hi = near + d;
            if (lo < 0 && hi >= n) {
                break;
            }
            if (lo >= 0 && lo < n && keyOf.applyAsLong(lo) == key) {
                return lo;
            }
            if (d > 0 && hi >= 0 && hi < n && keyOf.applyAsLong(hi) == key) {
                return hi;
            }
        }
        return -1;
    }

    /**
     * Moves the heights known by delta rows, as rows came (delta above 0)
     * or went above them: those that move past the end go, and the rows
     * that open up are not known.
     */
    void shift(int delta) {
        if (delta > 0) {
            for (int i = count - 1; i >= 0; i--) {
                heights[i] = i - delta >= 0 ? heights[i - delta] : Float.NaN;
            }
        } else if (delta < 0) {
            for (int i = 0; i < count; i++) {
                heights[i] = i - delta < count ? heights[i - delta] : Float.NaN;
            }
        }
        known = 0;
        knownSum = 0;
        for (int i = 0; i < count; i++) {
            if (!Float.isNaN(heights[i])) {
                known++;
                knownSum += heights[i];
            }
        }
    }

    /**
     * Before a pass plans the rows: with keys, finds the anchor's key again
     * and, where it has moved, moves the anchor and the heights known with
     * it, so the view stays on the same item; else the anchor stays at its
     * index (as MyGO's sync).
     */
    void follow(int n) {
        if (keyOf == null || !hasAnchorKey || n == 0) {
            return;
        }
        int j = find(anchorKey, anchor, n);
        if (j >= 0 && j != anchor) {
            resize(Math.max(n, count));
            shift(j - anchor);
            anchor = j;
        }
    }

    /** Where a row's top goes from the content box's top, as the heights known or estimated put it. */
    float rowTop(int row) {
        return (float) (top(row) - top(anchor)) + anchorY;
    }

    float estimate() {
        return known > 0 ? (float) (knownSum / known) : FIRST_ESTIMATE;
    }

    float height(int i) {
        float h = heights[i];
        return Float.isNaN(h) ? estimate() : h;
    }

    /** Row i's top, from the list's top. */
    double top(int i) {
        double y = 0;
        float e = estimate();
        for (int j = 0; j < i; j++) {
            float h = heights[j];
            y += (Float.isNaN(h) ? e : h) + gap;
        }
        return y;
    }

    /** All the rows' height, measured or estimated, with the gaps. */
    double content() {
        return count == 0 ? 0 : knownSum + (count - known) * (double) estimate() + gap * (double) (count - 1);
    }

    void resize(int n) {
        if (n > heights.length) {
            int old = heights.length;
            heights = Arrays.copyOf(heights, Math.max(n, old * 2));
            Arrays.fill(heights, old, heights.length, Float.NaN);
        }
        for (int i = n; i < count; i++) {
            if (!Float.isNaN(heights[i])) {
                known--;
                knownSum -= heights[i];
                heights[i] = Float.NaN;
            }
        }
        count = n;
        if (anchor > count - 1) {
            anchor = Math.max(0, count - 1);
        }
    }

    void setHeight(int i, float h) {
        float old = heights[i];
        if (old == h) {
            return;
        }
        if (Float.isNaN(old)) {
            known++;
        } else {
            knownSum -= old;
        }
        knownSum += h;
        heights[i] = h;
    }

    /** Anchors the view at offset y from the list's top. */
    void anchorAt(double y) {
        double t = 0;
        for (int i = 0; i < count; i++) {
            float h = height(i);
            if (t + h > y || i == count - 1) {
                anchor = i;
                anchorY = (float) (t - y);
                return;
            }
            t += h + gap;
        }
        anchor = 0;
        anchorY = 0;
    }

    /** Anchors the view so that the last row's bottom meets the bottom of a view v high (as MyGO's end request). */
    void anchorEnd(float v) {
        int i = count - 1;
        double sum = height(i);
        while (sum < v && i > 0) {
            i--;
            sum += height(i) + gap;
        }
        anchor = i;
        anchorY = (float) (v - sum);
    }

    /** Moves the anchor to the first row whose bottom is below the view's top. */
    void normalize() {
        while (anchor < count - 1 && anchorY + height(anchor) + gap <= 0) {
            anchorY += height(anchor) + gap;
            anchor++;
        }
        while (anchor > 0 && anchorY > 0) {
            anchor--;
            anchorY -= height(anchor) + gap;
        }
    }

    /** The rows to build for a view v high: those in it, and two more each way. */
    void plan(float v) {
        if (count == 0) {
            builtFirst = 0;
            builtLast = -1;
            return;
        }
        builtFirst = Math.max(0, anchor - 2);
        float y = anchorY;
        int i = anchor;
        while (i < count - 1 && y + height(i) < v) {
            y += height(i) + gap;
            i++;
        }
        builtLast = Math.min(count - 1, i + 2);
    }

    private static float[] filled(float[] a) {
        Arrays.fill(a, Float.NaN);
        return a;
    }
}
