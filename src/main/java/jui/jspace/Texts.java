package jui.jspace;

import jui.ui.Context;

/** One-line text that fits a width, cut with an ellipsis. */
final class Texts {
    /** Lays s out at size/weight; if it wraps past one line, returns it cut to fit + "…". */
    static String fit(Context c, String s, float size, int weight, float maxW) {
        if (maxW <= 8) return "…";
        jui.text.TextLayout l = c.layoutText(s, size, weight, maxW);
        if (l.lines <= 1) return s;
        int lo = 0, hi = s.length();
        while (lo < hi) {                                        // binary search the longest prefix that fits
            int mid = (lo + hi + 1) / 2;
            String cut = s.substring(0, mid) + "…";
            if (c.layoutText(cut, size, weight, maxW).lines <= 1) lo = mid; else hi = mid - 1;
        }
        return s.substring(0, lo) + "…";
    }

    private Texts() {}
}
