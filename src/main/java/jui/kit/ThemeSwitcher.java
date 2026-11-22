package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import jui.ui.Rect;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's theme switcher, after the ThemeSwitcher and its Eclipse, Split
 * and Rise faces: one control that moves between light and dark appearance.
 * The engine has no window-level canvas a nested component can draw on, so
 * the reveal plays across the control itself: Eclipse grows a disc of the
 * next look outward from the button, Split opens it from a seam, Rise lifts
 * it from the bottom edge; the look flips at the cover's midpoint and the
 * icon morphs sun to moon throughout.
 */
public final class ThemeSwitcher {

    /** How the next appearance arrives. */
    public enum Style { PLAIN, ECLIPSE, SPLIT, RISE }

    private static final long REVEAL_NS = 420_000_000L;

    private static final class St {
        boolean animating;
        long start;
        boolean toDark;
    }

    /** The switcher: a 34 DIP round button carrying the sun and moon. */
    public static Element themeSwitcher(Context c, Style style, boolean disabled) {
        boolean dark = c.theme().isDark();
        Element host = box(c).label("kit-theme-switcher");
        BoolRef on = localBool(host, "on", dark);
        Ref<St> ref = local(host, "anim", St::new);
        St st = ref.get();
        long now = c.now();
        float reveal = 0;
        if (st.animating) {
            float t = (now - st.start) / (float) REVEAL_NS;
            if (t >= 1) {
                st.animating = false;
                reveal = 0;
            } else {
                reveal = t < 0.5f ? t * 2 : 1;
                if (t >= 0.5f && c.theme().isDark() != st.toDark) {
                    c.setDark(st.toDark); // the new look arrives under the cover
                    on.set(st.toDark);
                }
                c.after(java.time.Duration.ofNanos(16_000_000));
            }
        }
        Element b = buttonBase(c).size(34, 34).radius(17).shrink(0).label("kit-theme-btn").clip();
        if (disabled) {
            b.disabled(true);
        }
        if (b.hovered() && !disabled) {
            b.background(c.theme().surfaceHover());
        }
        final float cover = reveal;
        b.draw((Painter p, Rect bx) -> {
            if (cover <= 0) {
                return;
            }
            Color next = jui.ui.Theme.of(st.toDark).background();
            Color col = Color.rgba(next.rgba() >>> 8, 0.85f);
            switch (style) {
                case ECLIPSE -> p.circle(bx.x() + bx.width() / 2, bx.y() + bx.height() / 2,
                        40 * cover, col);
                case SPLIT -> {
                    float w = bx.width() / 2 * cover;
                    p.box(bx.x() + bx.width() / 2 - w, bx.y(), w, bx.height(), col);
                    p.box(bx.x() + bx.width() / 2, bx.y(), w, bx.height(), col);
                }
                case RISE -> {
                    float h = bx.height() * cover;
                    p.box(bx.x(), bx.y() + bx.height() - h, bx.width(), h, col);
                }
                default -> { }
            }
        });
        b.children(() -> {
            boolean target = st.animating ? st.toDark : on.get();
            float t = Kit.spring(host, "sun", target ? 1 : 0, Kit.MORPH);
            box(c).size(18, 18).shrink(0).draw(
                    (p, bx) -> IconMorph.paint(p, bx, IconMorph.THEME, t, c.theme().text(),
                            c.theme().text(), c.theme().background(), 18));
        });
        if (!disabled && b.clicked()) {
            st.toDark = !on.get();
            if (style == Style.PLAIN) {
                c.setDark(st.toDark);
                on.set(st.toDark);
            } else {
                st.animating = true;
                st.start = now;
                c.after(java.time.Duration.ofNanos(16_000_000));
            }
            c.invalidate();
        }
        return host;
    }

    public static Element themeSwitcher(Context c) {
        return themeSwitcher(c, Style.PLAIN, false);
    }

    private ThemeSwitcher() {}
}
