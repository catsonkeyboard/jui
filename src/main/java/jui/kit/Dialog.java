package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's dialog, after the Dialog: a focused surface for decisions
 * that need attention. The panel sits over the dimmed backdrop with the
 * floating shadow, the title row leads with an optional icon, and the
 * footer's actions align to the end; Escape and an outside press close it.
 */
public final class Dialog {

    /** dialogKind picks which footer buttons render. */
    public enum Buttons { NONE, OK, OK_CANCEL, CONFIRM_CANCEL }

    /** The words the footer's buttons read; hosts with another language set these once. */
    public static String OK_TEXT = "OK", CONFIRM_TEXT = "Confirm", CANCEL_TEXT = "Cancel";

    public static Element dialog(Context c, jui.ui.BoolRef open, String title, String icon,
                                 boolean danger, Runnable body,
                                 Buttons buttons, java.util.function.Consumer<Boolean> onChoice) {
        return modal(c, open, () -> {
            Element panel = column(c).width(400).padding(20).gap(12)
                    .background(c.theme().background()).radius(Kit.RADIUS + 3)
                    .label("kit-dialog");
            panel.shadow(0, 16, 40, -8, Color.rgba(0x000000, 0.25f));
            panel.children(() -> {
                row(c).gap(9).alignItems(Align.CENTER).children(() -> {
                    if (icon != null) {
                        box(c).size(18, 18).shrink(0)
                                .draw(Icon.draw(icon, danger ? Kit.danger(c) : c.theme().accent()));
                    }
                    text(c, title).fontSize(14).fontWeight(600).textColor(c.theme().text());
                });
                body.run();
                if (buttons != Buttons.NONE) {
                    row(c).gap(8).justify(Justify.END).children(() -> {
                        if (buttons == Buttons.OK_CANCEL || buttons == Buttons.CONFIRM_CANCEL) {
                            Element cancel = ButtonBase.soft(c, false);
                            cancel.height(32).padding(0, 12).radius(Kit.RADIUS_XS + 2)
                                    .border(1, Kit.hairline(c));
                            cancel.children(() -> text(c, CANCEL_TEXT).singleLine().fontSize(12.5f)
                                    .textColor(c.theme().text()));
                            if (cancel.clicked()) {
                                open.set(false);
                                onChoice.accept(false);
                            }
                        }
                        Element ok = Button.builder(c,
                                buttons == Buttons.CONFIRM_CANCEL ? CONFIRM_TEXT : OK_TEXT,
                                danger ? Button.Variant.DANGER : Button.Variant.PRIMARY, Button.Size.SM);
                        if (ok.clicked()) {
                            open.set(false);
                            onChoice.accept(true);
                        }
                    });
                }
            });
        });
    }

    /** The legacy shape. */
    public static Element dialog(Context c, jui.ui.BoolRef open, String title, Runnable body,
                                 Buttons buttons, java.util.function.Consumer<Boolean> onChoice) {
        return dialog(c, open, title, null, false, body, buttons, onChoice);
    }

    private Dialog() {}
}
