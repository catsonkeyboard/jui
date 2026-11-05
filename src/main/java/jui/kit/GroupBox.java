package jui.kit;

import jui.ui.Align;
import jui.ui.Justify;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's group box: a titled, bordered card grouping related controls,
 * after gpui-kit's GroupBox.
 */
public final class GroupBox {
    public static Element groupBox(Context c, String title, Runnable content) {
        return column(c).fillWidth().radius(Kit.RADIUS).border(1, Kit.line(c))
                .background(c.theme().surface())
                .children(() -> {
                    box(c).fillWidth().height(30).padding(0, 12)
                            .alignItems(Align.CENTER)
                            .children(() -> text(c, title).fontSize(11).bold()
                                    .textColor(c.theme().textMuted()));
                    box(c).fillWidth().padding(12, 14).children(content);
                });
    }

    private GroupBox() {}
}
