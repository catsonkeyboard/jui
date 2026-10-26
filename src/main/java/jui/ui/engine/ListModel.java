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

    /** The offset the list last wrote; an offset and a row asked for (NaN, -1 for none). */
    float wroteY;
    float requestY = Float.NaN;
    int requestRow = -1;

    /** The rows built this frame, and the view's height as last laid out. */
    int builtFirst, builtLast = -1;
    float viewH;

    /** The rows the last frame showed, or -1 before the list first shows. */
    int first = -1, last = -1;

    public int first() {
        return first;
    }

    public int last() {
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
