package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Key;
import jui.ui.Shortcut;

import static jui.ui.Ui.*;

/**
 * The kit's sheet: a full-height panel that sits against the window's right
 * edge over a dimmed backdrop, closed by a backdrop click or Escape — after
 * gpui-kit's Sheet. Built from public pieces (a full-screen catcher button
 * over the dim layer) because the engine's modal plumbing is package-private.
 */
public final class Sheet {
    public static Element sheet(Context c, jui.ui.BoolRef open, float width, Runnable content) {
        Element root = box(c).absolute().left(0).top(0).right(0).bottom(0)
                .label("kit-sheet-backdrop");
        if (!open.get()) {
            root.invisible();
            return root;
        }
        root.background(Color.rgba(0x000000, 0.4f));
        Element catcher = buttonBase(c).fill().label("kit-sheet-catcher");
        if (catcher.clicked()) open.set(false);
        if (c.shortcut(Shortcut.of(Key.ESCAPE))) open.set(false);
        box(c).absolute().right(0).top(0).bottom(0).width(width)
                .background(c.theme().surface())
                .shadow(-6, 0, 24, 0, Color.rgba(0x000000, 0.3f))
                .label("kit-sheet")
                .children(content);
        return root;
    }

    private Sheet() {}
}
