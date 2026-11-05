package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's sheet: a panel that slides in from the right edge over a dimmed
 * backdrop, after gpui-kit's Sheet.
 */
public final class Sheet {
    public static Element sheet(Context c, jui.ui.BoolRef open, float width, Runnable content) {
        return modal(c, open, () -> {
            box(c).absolute().right(0).top(0).bottom(0).width(width)
                    .background(c.theme().surface())
                    .border(1, Kit.line(c))
                    .label("kit-sheet")
                    .children(content);
        });
    }

    private Sheet() {}
}
