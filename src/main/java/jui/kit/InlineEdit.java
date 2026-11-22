package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's inline edit, after the InlineEdit: rename in place. At rest
 * the value reads as text and a pencil shows on hover; clicking swaps the
 * text for a field of the same height without moving anything, Enter or a
 * blur commits, Escape puts the old value back.
 */
public final class InlineEdit {

    /** The control over the caller's TextRef, one line tall. */
    public static Element inlineEdit(Context c, TextRef value, float fontSize, float maxWidth) {
        Element host = row(c).height(fontSize + 12).radius(Kit.RADIUS_XS).label("kit-inline-edit")
                .alignItems(jui.ui.Align.CENTER);
        BoolRef editing = localBool(host, "editing", false);
        String before = value.get() == null ? "" : value.get();
        if (editing.get()) {
            Element box = textInput(c, value).height(fontSize + 12).padding(0, 7)
                    .radius(Kit.RADIUS_XS).minWidth(80).maxWidth(maxWidth).singleLine()
                    .label("kit-inline-field");
            box.background(KitMotion.surface(c));
            box.border(1, box.focused() ? c.theme().accent() : KitMotion.hairline(c));
            box.autoFocus();
            if (box.submitted()) {
                editing.set(false);
                c.invalidate();
            }
            if (box.dismissed()) {
                value.set(before);
                editing.set(false);
                c.invalidate();
            }
            c.after(java.time.Duration.ofMillis(16));
        } else {
            boolean hot = host.hovered();
            host.children(() -> {
                text(c, before).singleLine().fontSize(fontSize).textColor(c.theme().text());
                if (hot) {
                    box(c).size(13, 13).shrink(0).margin(0, 4)
                            .draw(Icon.draw("pencil", c.theme().textMuted()));
                }
            });
            if (hot) {
                host.background(c.theme().surfaceHover());
            }
            if (host.clicked()) {
                editing.set(true);
                c.invalidate();
            }
        }
        return host;
    }

    public static Element inlineEdit(Context c, TextRef value) {
        return inlineEdit(c, value, 12.5f, 320);
    }

    private InlineEdit() {}
}
