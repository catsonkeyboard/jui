package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's segmented control, after the SegmentedControl: switch between
 * a small set of related views. One indicator pill sits under the segments
 * and glides to the picked one on the morph run, its width springing to
 * each label; the labels cross their color on the way.
 */
public final class SegmentedControl {

    private static float textWidth(Context c, String s, float size) {
        return s == null || s.isEmpty() ? 0 : c.layoutText(s, size, 500, 4096).width;
    }

    /** The control over the caller's IntRef; optional icons ride each segment. */
    public static Element segmented(Context c, IntRef selected, String[] options, String[] icons,
                                    boolean small, boolean disabled) {
        float h = small ? 26 : 32;
        float fs = small ? 11.5f : 12.5f;
        float pad = small ? 9 : 12;
        Element host = row(c).gap(2).padding(2).radius(h / 2 + 1).shrink(0)
                .background(c.theme().surface()).label("kit-segmented").alignItems(Align.CENTER);
        int n = options.length;
        float[] widths = new float[n];
        for (int i = 0; i < n; i++) {
            widths[i] = pad * 2 + textWidth(c, options[i], fs) + (icons != null && icons[i] != null ? 15 : 0);
        }
        int sel = Math.max(0, Math.min(selected.get(), n - 1));
        // The indicator's gliding share, as ButtonGroup's highlight.
        float x = KitMotion.spring(host, "ix", start(sel, widths), KitMotion.MORPH);
        float w = KitMotion.spring(host, "iw", widths[sel], KitMotion.MORPH);
        int outer = -1;
        float[] cover = new float[n];
        {
            float run = 0;
            for (int i = 0; i < n; i++) {
                float overlap = Math.min(x + w, run + widths[i]) - Math.max(x, run);
                cover[i] = widths[i] > 0 ? Math.max(0, Math.min(1, overlap / widths[i])) : 0;
                run += widths[i] + 2;
            }
        }
        for (int i = 0; i < n; i++) {
            int ii = i;
            boolean on = ii == sel;
            Element seg = buttonBase(c).width(widths[ii]).height(h).radius(h / 2).shrink(0)
                    .label("kit-seg-" + options[ii]);
            if (disabled && !on) {
                seg.disabled(true);
            }
            if (cover[ii] > 0.01f) {
                seg.background(KitMotion.mix(Color.rgba(0x808080, 0), c.theme().background(), cover[ii]));
                seg.shadow(0, 1, 2, 0, Color.rgba(0x000000, 0.08f * cover[ii]));
            }
            seg.children(() -> {
                float t = KitMotion.spring(host, "t" + ii, on ? 1 : 0, KitMotion.SNAPPY);
                if (icons != null && icons[ii] != null) {
                    box(c).size(small ? 13 : 15, small ? 13 : 15).shrink(0)
                            .draw(Icon.draw(icons[ii], KitMotion.mix(c.theme().textMuted(), c.theme().text(), t)));
                }
                Element l = text(c, options[ii]).singleLine().fontSize(fs);
                l.textColor(KitMotion.mix(c.theme().textMuted(), c.theme().text(), t));
                l.fontWeight(on ? 500 : 400);
            });
            if (!disabled && seg.clicked()) {
                selected.set(ii);
                c.invalidate();
            }
        }
        return host;
    }

    private static float start(int i, float[] widths) {
        float before = 0;
        for (int k = 0; k < i && k < widths.length; k++) {
            before += widths[k] + 2;
        }
        return before;
    }

    public static Element segmented(Context c, IntRef selected, String[] options) {
        return segmented(c, selected, options, null, false, false);
    }

    private SegmentedControl() {}
}
