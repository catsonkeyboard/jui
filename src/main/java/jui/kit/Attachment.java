package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's attachment: a chip for a staged file — doc icon, name, size and a
 * remove ×, after gpui-kit's Attachment.
 */
public final class Attachment {
    public static Element attachment(Context c, String name, String sizeText, Runnable onRemove) {
        Element chip = row(c).height(30).padding(0, 10).gap(8).radius(Kit.RADIUS_SM)
                .border(1, Kit.line(c))
                .background(c.theme().surface())
                .label("kit-att-" + name)
                .alignItems(Align.CENTER)
                .children(() -> {
                    box(c).size(14, 14).draw(Icon.draw("doc", c.theme().accent()));
                    text(c, name).fontSize(12).textColor(c.theme().text());
                    if (sizeText != null && !sizeText.isEmpty()) {
                        text(c, sizeText).fontSize(10.5f).textColor(c.theme().textMuted());
                    }
                    if (onRemove != null) {
                        Element x = buttonBase(c).size(14, 14).radius(3)
                                .label("kit-att-remove-" + name)
                                .children(() -> box(c).size(9, 9)
                                        .draw(Icon.draw("xmark", c.theme().textMuted())));
                        if (x.clicked()) onRemove.run();
                    }
                });
        return chip;
    }

    private Attachment() {}
}
