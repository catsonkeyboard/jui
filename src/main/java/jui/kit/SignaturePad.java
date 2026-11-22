package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import jui.ui.Rect;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's signature pad, after the SignaturePad: smooth ink that thins
 * with speed, with undo and a clear. The strokes are kept as point lists
 * with a width each; the ink reads from the theme's text color, and a
 * dashed baseline marks the writing line.
 */
public final class SignaturePad {

    /** One stroke: points in element coordinates, one width per segment. */
    public static final class Stroke {
        final List<float[]> points = new ArrayList<>();
        final List<Float> widths = new ArrayList<>();
    }

    private static final class St {
        final List<Stroke> strokes = new ArrayList<>();
        Stroke live;
        long lastAt;
    }

    /** The pad: width, height, and whether the ink takes the danger tone. */
    public static Element signaturePad(Context c, float width, float height, boolean disabled) {
        Element host = column(c).gap(6).label("kit-signature");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        Element pad = box(c).size(width, height).radius(Kit.RADIUS + 2).clip()
                .background(c.theme().background()).border(1, Kit.hairline(c))
                .label("kit-signature-pad").draggable();
        if (disabled) {
            pad.disabled(true);
        }
        Rect b = pad.bounds();
        pad.draw((Painter p, Rect bx) -> {
            // The writing line.
            p.line(bx.x() + 20, bx.y() + bx.height() - 34, bx.x() + bx.width() - 20,
                    bx.y() + bx.height() - 34, 1, Kit.mix(c.theme().border(), c.theme().text(), 0.15f));
            // The ink.
            Color ink = c.theme().text();
            for (Stroke s : st.strokes) {
                stroke(p, s, ink);
            }
            if (st.live != null) {
                stroke(p, st.live, ink);
            }
        });
        pad.children(() -> { });
        if (!disabled && pad.pressed()) {
            float x = pad.dragX(), y = pad.dragY();
            // The pointer's place from the pad's bounds: dragX is an offset,
            // so the live stroke tracks the offset from where the drag began.
            if (st.live == null) {
                st.live = new Stroke();
                st.live.points.add(new float[]{0, 0});
                st.lastAt = c.now();
            }
            long now = c.now();
            float speed = Math.max(0, Math.min(1, (now - st.lastAt) / 80_000_000f));
            st.lastAt = now;
            List<float[]> pts = st.live.points;
            float[] last = pts.get(pts.size() - 1);
            float dx = x - last[0], dy = y - last[1];
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > 2) {
                pts.add(new float[]{x, y});
                // Faster is thinner, as the ink thins with speed.
                st.live.widths.add(Math.max(1.1f, 2.6f - dist * 0.08f - speed * 0.6f));
                c.invalidate();
            }
        } else if (st.live != null) {
            st.strokes.add(st.live);
            st.live = null;
            c.invalidate();
        }
        row(c).gap(6).children(() -> {
            Element undo = buttonBase(c).height(26).padding(0, 9).gap(5).radius(Kit.RADIUS_XS)
                    .label("kit-signature-undo");
            undo.children(() -> {
                box(c).size(13, 13).shrink(0).draw(Icon.draw("arrow-left", c.theme().textMuted()));
                text(c, "Undo").singleLine().fontSize(11.5f).textColor(c.theme().textMuted());
            });
            if (undo.clicked() && !st.strokes.isEmpty()) {
                st.strokes.remove(st.strokes.size() - 1);
                c.invalidate();
            }
            Element clear = buttonBase(c).height(26).padding(0, 9).radius(Kit.RADIUS_XS)
                    .label("kit-signature-clear");
            clear.children(() -> text(c, "Clear").singleLine().fontSize(11.5f)
                    .textColor(Kit.danger(c)));
            if (clear.clicked() && !st.strokes.isEmpty()) {
                st.strokes.clear();
                c.invalidate();
            }
        }).fillWidth().justify(jui.ui.Justify.END);
        return host;
    }

    private static void stroke(Painter p, Stroke s, Color ink) {
        for (int i = 1; i < s.points.size(); i++) {
            float[] a = s.points.get(i - 1), b2 = s.points.get(i);
            float w = i - 1 < s.widths.size() ? s.widths.get(i - 1) : 2f;
            p.line(a[0], a[1], b2[0], b2[1], w, ink);
        }
    }

    public static Element signaturePad(Context c, float width, float height) {
        return signaturePad(c, width, height, false);
    }

    private SignaturePad() {}
}
