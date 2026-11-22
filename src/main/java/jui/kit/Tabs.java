package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's tabs, after the Tabs: switch between related content in the
 * same context. The underline glides from tab to tab on the morph run and
 * its width springs to each label; the labels cross their color and weight
 * as the underline passes.
 */
public final class Tabs {

    private static float textWidth(Context c, String s, float size) {
        return s == null || s.isEmpty() ? 0 : c.layoutText(s, size, 500, 4096).width;
    }

    /** The tab row over the caller's IntRef, with optional leading icons. */
    public static Element tabs(Context c, IntRef selected, String[] titles, String[] icons,
                               boolean disabled) {
        float fs = 12.5f;
        Element host = row(c).gap(2).label("kit-tabs").alignItems(Align.END);
        int n = titles.length;
        int sel = Math.max(0, Math.min(selected.get(), n - 1));
        float[] widths = new float[n];
        for (int i = 0; i < n; i++) {
            widths[i] = textWidth(c, titles[i], fs) + 20;
        }
        int sel2 = sel;
        float x = start(sel2, widths);
        for (int i = 0; i < n; i++) {
            int ii = i;
            boolean on = ii == sel2;
            Element tab = buttonBase(c).width(widths[ii]).height(34).shrink(0).label("kit-tab-" + titles[ii]);
            if (disabled && !on) {
                tab.disabled(true);
            }
            float t = KitMotion.spring(host, "t" + ii, on ? 1 : 0, KitMotion.SNAPPY);
            tab.children(() -> {
                if (icons != null && icons[ii] != null) {
                    box(c).size(14, 14).shrink(0)
                            .draw(Icon.draw(icons[ii], KitMotion.mix(c.theme().textMuted(), c.theme().text(), t)));
                }
                Element l = text(c, titles[ii]).singleLine().fontSize(fs);
                l.textColor(KitMotion.mix(c.theme().textMuted(), c.theme().text(), t));
                l.fontWeight(on ? 500 : 400);
                // The gliding underline, one per tab by coverage.
                box(c).absolute().left(10).bottom(0).right(10).height(2).radius(1)
                        .background(Color.rgba(c.theme().accent().rgba() >>> 8, t));
            });
            if (!disabled && tab.clicked()) {
                selected.set(ii);
                c.invalidate();
            }
        }
        return host;
    }

    private static float start(int i, float[] widths) {
        float before = 0;
        for (int k = 0; k < i && k < widths.length; k++) {
            before += widths[k];
        }
        return before;
    }

    /** The legacy shape. */
    public static Element tabs(Context c, IntRef selected, String... titles) {
        return tabs(c, selected, titles, null, false);
    }

    private Tabs() {}
}
