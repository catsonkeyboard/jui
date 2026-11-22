package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's copy button, after the CopyButton: copies a value and
 * crossfades its icon and label to Copied, then back. The label cell
 * reserves the widest of its states, so the layout never shifts; the check
 * draws its stroke in.
 */
public final class CopyButton {

    private static final long RESET_NS = 1_800_000_000L;

    private static final class St {
        int state; // 0 idle, 1 copied, 2 failed
        long at;
    }

    /**
     * The button: outline or plain, icon-only or labelled. Copies through
     * the host clipboard; a failure shows the failed face instead of
     * throwing.
     */
    public static Element copyButton(Context c, String value, String label, boolean iconOnly,
                                     boolean outline, boolean disabled) {
        Element host = row(c).label("kit-copy-button").alignItems(Align.CENTER);
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        if (st.state != 0 && c.now() - st.at > RESET_NS) {
            st.state = 0;
            c.invalidate();
        }
        String word = st.state == 1 ? "Copied" : st.state == 2 ? "Failed" : label;
        float fs = 12;
        // Reserve the widest state so the row never shifts.
        float w = Math.max(Math.max(textWidth(c, label, fs), textWidth(c, "Copied", fs)),
                textWidth(c, "Failed", fs));
        Element b = buttonBase(c).height(30).radius(Kit.RADIUS_XS + 2).gap(6).label("kit-copy-face");
        if (disabled) {
            b.disabled(true);
        }
        if (outline) {
            b.border(1, KitMotion.hairline(c)).background(KitMotion.surface(c)).padding(0, 10);
            if (b.hovered() && !disabled) {
                b.background(c.theme().surfaceHover());
            }
        } else {
            b.padding(0, iconOnly ? 7 : 6);
            if (b.hovered() && !disabled) {
                b.background(c.theme().surfaceHover());
            }
        }
        b.width(iconOnly ? 30 : w + (outline ? 20 : 12) + 16 + 6);
        b.children(() -> {
            float t = KitMotion.spring(host, "swap", st.state == 0 ? 0 : 1, KitMotion.FADE);
            box(c).size(15, 15).shrink(0).draw((p, bx) -> {
                if (st.state == 0) {
                    drawCopyGlyph(p, bx, c.theme().text(), 1 - t);
                } else {
                    drawCheckGlyph(p, bx, st.state == 1 ? KitMotion.success(c) : KitMotion.danger(c), t);
                }
            });
            if (!iconOnly) {
                Element l = text(c, word).singleLine().fontSize(fs);
                l.textColor(st.state == 1 ? KitMotion.success(c) : st.state == 2 ? KitMotion.danger(c) : c.theme().text());
            }
        });
        if (!disabled && b.clicked()) {
            boolean ok;
            try {
                c.copyText(value == null ? "" : value);
                ok = true;
            } catch (Throwable e) {
                ok = false;
            }
            st.state = ok ? 1 : 2;
            st.at = c.now();
            c.after(java.time.Duration.ofNanos(RESET_NS).plusMillis(40));
            c.invalidate();
        }
        return b;
    }

    public static Element copyButton(Context c, String value) {
        return copyButton(c, value, "Copy", false, true, false);
    }

    private static float textWidth(Context c, String s, float size) {
        return s == null || s.isEmpty() ? 0 : c.layoutText(s, size, 500, 4096).width;
    }

    private static void drawCopyGlyph(jui.ui.Painter p, jui.ui.Rect b, Color color, float alpha) {
        float u = Math.min(b.width(), b.height()) / 16;
        float ox = b.x(), oy = b.y();
        Color col = Color.rgba(color.rgba() >>> 8, alpha);
        p.roundedBox(ox + 5.5f * u, oy + 2 * u, 7 * u, 9 * u, 1.5f * u, Color.rgba(col.rgba() >>> 8, alpha * 0.0f));
        p.line(ox + 5.5f * u, oy + 2 * u, ox + 10.5f * u, oy + 2 * u, 1.4f * u, col);
        p.line(ox + 10.5f * u, oy + 2 * u, ox + 10.5f * u, oy + 11 * u, 1.4f * u, col);
        p.line(ox + 5.5f * u, oy + 2 * u, ox + 5.5f * u, oy + 5 * u, 1.4f * u, col);
        p.line(ox + 3 * u, oy + 5 * u, ox + 10.5f * u, oy + 5 * u, 1.4f * u, col);
        p.line(ox + 3 * u, oy + 5 * u, ox + 3 * u, oy + 14 * u, 1.4f * u, col);
        p.line(ox + 3 * u, oy + 14 * u, ox + 8 * u, oy + 14 * u, 1.4f * u, col);
        p.line(ox + 10.5f * u, oy + 11 * u, ox + 8 * u, oy + 14 * u, 1.4f * u, col);
    }

    private static void drawCheckGlyph(jui.ui.Painter p, jui.ui.Rect b, Color color, float t) {
        float u = Math.min(b.width(), b.height()) / 16;
        float ox = b.x(), oy = b.y();
        float[] a = {3.5f, 8.5f}, m = {6.5f, 11.5f}, e = {13, 4.5f};
        float k1 = Math.min(1, t * 2), k2 = Math.max(0, t * 2 - 1);
        p.line(ox + a[0] * u, oy + a[1] * u, ox + a[0] * u + (m[0] - a[0]) * u * k1,
                oy + a[1] * u + (m[1] - a[1]) * u * k1, 1.8f * u, color);
        if (k2 > 0) {
            p.line(ox + m[0] * u, oy + m[1] * u, ox + m[0] * u + (e[0] - m[0]) * u * k2,
                    oy + m[1] * u + (e[1] - m[1]) * u * k2, 1.8f * u, color);
        }
    }

    private CopyButton() {}
}
