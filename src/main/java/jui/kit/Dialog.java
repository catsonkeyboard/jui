package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's dialog: a centered modal with title, body and an action row,
 * over the framework's modal, after gpui-kit's Dialog.
 */
public final class Dialog {
    /** dialogKind picks which footer buttons render. */
    public enum Buttons { NONE, OK, OK_CANCEL, CONFIRM_CANCEL }

    public static Element dialog(Context c, jui.ui.BoolRef open, String title, Runnable body,
                                 Buttons buttons, java.util.function.Consumer<Boolean> onChoice) {
        return modal(c, open, () -> {
            column(c).width(360).padding(18, 20).gap(12)
                    .background(c.theme().surface()).radius(Kit.RADIUS)
                    .children(() -> {
                        row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                            text(c, title).fontSize(14).bold().textColor(c.theme().text());
                            spacer(c).grow(1);
                        });
                        body.run();
                        if (buttons != Buttons.NONE) {
                            row(c).gap(8).justify(Justify.END).children(() -> {
                                if (buttons == Buttons.OK_CANCEL || buttons == Buttons.CONFIRM_CANCEL) {
                                    Element cancel = Button.button(c, "取消", Kit.Variant.DEFAULT, Kit.Size.SMALL);
                                    if (cancel.clicked()) {
                                        open.set(false);
                                        onChoice.accept(false);
                                    }
                                }
                                String okLabel = buttons == Buttons.CONFIRM_CANCEL ? "确认" : "确定";
                                Element ok = Button.button(c, okLabel, Kit.Variant.PRIMARY, Kit.Size.SMALL);
                                if (ok.clicked()) {
                                    open.set(false);
                                    onChoice.accept(true);
                                }
                            });
                        }
                    });
        });
    }

    private Dialog() {}
}
