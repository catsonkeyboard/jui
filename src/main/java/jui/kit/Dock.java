package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;

import static jui.ui.Ui.*;

/**
 * The kit's dock: a floating bottom bar of icon buttons with an active dot,
 * after gpui-kit's Dock.
 */
public final class Dock {
    public static Element dock(Context c, IntRef active, String... icons) {
        Element bar = row(c).gap(6).padding(6, 10).radius(Kit.RADIUS)
                .background(c.theme().surface())
                .border(1, Kit.line(c))
                .alignItems(Align.CENTER)
                .label("kit-dock")
                .children(() -> {
                    for (int i = 0; i < icons.length; i++) {
                        int idx = i;
                        boolean on = active.get() == idx;
                        Element b = buttonBase(c).size(34, 34).radius(Kit.RADIUS_SM)
                                .label("kit-dock-" + icons[idx])
                                .alignItems(Align.CENTER)
                                .children(() -> {
                                    box(c).size(16, 16).draw(Icon.draw(icons[idx],
                                            on ? c.theme().accent() : c.theme().textMuted()));
                                    if (on) {
                                        box(c).size(3, 3).radius(1.5f).background(c.theme().accent());
                                    }
                                });
                        if (b.hovered()) b.background(c.theme().surfaceHover());
                        if (b.clicked()) active.set(idx);
                    }
                });
        return bar;
    }

    private Dock() {}
}
