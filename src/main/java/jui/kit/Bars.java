package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's status bar and toolbar: three-slot strips with the kit's bar
 * height, after gpui-kit's StatusBar and Toolbar.
 */
public final class Bars {
    /** A bottom status strip: left text, right slot. */
    public static Element statusBar(Context c, String left, Runnable right) {
        return row(c).fillWidth().height(24).padding(0, 12)
                .background(c.theme().surface())
                .alignItems(Align.CENTER)
                .label("kit-statusbar")
                .children(() -> {
                    text(c, left).fontSize(10.5f).textColor(c.theme().textMuted());
                    spacer(c).grow(1);
                    right.run();
                });
    }

    /** A top toolbar: leading slot, trailing slot. */
    public static Element toolbar(Context c, Runnable leading, Runnable trailing) {
        return row(c).fillWidth().height(40).padding(0, 10).gap(8)
                .background(c.theme().surface())
                .alignItems(Align.CENTER)
                .label("kit-toolbar")
                .children(() -> {
                    leading.run();
                    spacer(c).grow(1);
                    trailing.run();
                });
    }

    private Bars() {}
}
