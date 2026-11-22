package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Theme;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's drawer, after the Drawer: a temporary side surface for
 * focused work. The panel slides in from the caller's edge on the morph
 * run while the rest of the surface dims; a press on the dim, or the close
 * button, shuts it. The caller should build the drawer inside a full-area
 * box; the panel fills its height.
 */
public final class Drawer {

    /** The side a drawer enters from. */
    public enum Side { LEFT, RIGHT }

    /**
     * The drawer: width, side, title, and the content builder.
     */
    public static Element drawer(Context c, jui.ui.BoolRef open, Side side, float width,
                                 String title, Runnable content) {
        Theme t = c.theme();
        boolean on = open.get();
        Element back = box(c).absolute().left(0).top(0).right(0).bottom(0).label("kit-drawer-back");
        if (!on) {
            back.invisible();
        } else {
            back.background(Color.rgba(0x000000, 0.4f));
            c.after(java.time.Duration.ofMillis(16)); // keep frames running while the panel glides
        }
        // p runs width (open) to 0 (closed); the panel sits at p - width.
        float p = Kit.spring(back, "p", on ? width : 0, Kit.MORPH);
        back.onClick(() -> {
            open.set(false);
            c.invalidate();
        });
        back.children(() -> {
            Element panel = column(c).width(width).fillHeight().absolute().top(0).bottom(0)
                    .background(t.background()).label("kit-drawer-panel");
            panel.onClick(() -> { }); // presses on the panel stay in it
            if (side == Side.LEFT) {
                panel.left(p - width);
            } else {
                panel.right(p - width);
            }
            panel.shadow(side == Side.LEFT ? 4 : -4, 0, 30, 0, Color.rgba(0x000000, 0.25f));
            panel.children(() -> {
                row(c).padding(14, 16).gap(8).alignItems(Align.CENTER).children(() -> {
                    text(c, title).fontSize(13.5f).fontWeight(600).textColor(t.text());
                    spacer(c).grow(1);
                    Element close = buttonBase(c).size(26, 26).radius(6).label("kit-drawer-close");
                    if (close.hovered()) {
                        close.background(t.surfaceHover());
                    }
                    close.children(() -> box(c).size(12, 12).shrink(0)
                            .draw(Icon.draw("xmark", t.textMuted())));
                    if (close.clicked()) {
                        open.set(false);
                        c.invalidate();
                    }
                });
                divider(c);
                box(c).grow(1).padding(16).children(content);
            });
        });
        return back;
    }

    public static Element drawer(Context c, jui.ui.BoolRef open, String title, Runnable content) {
        return drawer(c, open, Side.RIGHT, 320, title, content);
    }

    private Drawer() {}
}
