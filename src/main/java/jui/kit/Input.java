package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;
import jui.ui.Theme;

import static jui.ui.Ui.*;

/**
 * The kit's input and textarea, after the Input and Textarea: labeled
 * fields with clear states — a helper line under the box, an invalid state
 * whose border and note take the danger color on the snappy run, leading
 * and trailing slots, a disabled look, and the framework's text stack
 * (IME, undo, selection) underneath.
 */
public final class Input {

    /** Border color for the field's state. */
    private static Color edge(Context c, boolean focused, boolean hovered, boolean invalid) {
        if (invalid) {
            return Kit.danger(c);
        }
        Theme t = c.theme();
        return focused ? t.accent() : hovered ? Kit.mix(t.border(), t.text(), 0.25f) : t.border();
    }

    /**
     * One field face: the box with its animated border, an optional
     * leading icon, a trailing slot (a builder, so it lands inside the
     * box), and the helper or error note under it.
     */
    public static Element field(Context c, String label, TextRef value, String placeholder,
                                String helper, String error, boolean disabled,
                                String leadingIcon, Runnable trailing) {
        boolean invalid = error != null && !error.isEmpty();
        return column(c).fillWidth().gap(4).label("kit-field-" + label).children(() -> {
            if (label != null && !label.isEmpty()) {
                Element l = text(c, label).fontSize(12);
                l.textColor(invalid ? Kit.danger(c) : c.theme().text());
                l.fontWeight(500);
            }
            Element box = textInput(c, value).placeholder(placeholder).fillWidth()
                    .height(34).padding(0, 11).radius(Kit.RADIUS_XS + 2)
                    .label("kit-input-" + label);
            if (disabled) {
                box.disabled(true);
            }
            boolean focused = box.focused();
            boolean hovered = box.hovered();
            float k = Kit.spring(box, "edge", focused || invalid ? 1 : 0, Kit.SNAPPY);
            Color rest = edge(c, focused, hovered, invalid);
            Color fill = disabled ? Color.rgba(Kit.surface(c).rgba() >>> 8, 0.5f) : Kit.surface(c);
            box.background(fill);
            box.border(1, focused || invalid ? Kit.mix(c.theme().border(), rest, k) : rest);
            if (leadingIcon != null) {
                box.children(() -> box(c).absolute().left(9).top(0).bottom(0).width(16)
                        .alignItems(jui.ui.Align.CENTER)
                        .children(() -> box(c).size(15, 15)
                                .draw(Icon.draw(leadingIcon, c.theme().textMuted()))));
            }
            if (trailing != null) {
                box.children(() -> box(c).absolute().right(7).top(0).bottom(0).width(24)
                        .alignItems(jui.ui.Align.CENTER).children(trailing));
            }
            if (invalid || (helper != null && !helper.isEmpty())) {
                Element note = text(c, invalid ? error : helper).fontSize(11);
                note.textColor(invalid ? Kit.danger(c) : c.theme().textMuted());
            }
        });
    }

    /** The labeled field. */
    public static Element field(Context c, String label, TextRef value, String placeholder) {
        return field(c, label, value, placeholder, null, null, false, null, null);
    }

    /** The bare input. */
    public static Element input(Context c, TextRef value, String placeholder) {
        return textInput(c, value).placeholder(placeholder).fillWidth().height(34)
                .padding(0, 11).radius(Kit.RADIUS_XS + 2).label("kit-input");
    }

    /** A labeled multiline field with a helper note and a min height. */
    public static Element areaField(Context c, String label, TextRef value, String placeholder,
                                    String helper, boolean invalid, boolean disabled, float minHeight) {
        return column(c).fillWidth().gap(4).label("kit-area-" + label).children(() -> {
            if (label != null && !label.isEmpty()) {
                Element l = text(c, label).fontSize(12).fontWeight(500);
                l.textColor(invalid ? Kit.danger(c) : c.theme().text());
            }
            Element box = textArea(c, value).placeholder(placeholder).fillWidth()
                    .minHeight(minHeight).padding(8, 11).radius(Kit.RADIUS_XS + 2)
                    .label("kit-textarea-" + label);
            if (disabled) {
                box.disabled(true);
            }
            boolean focused = box.focused();
            float k = Kit.spring(box, "edge", focused || invalid ? 1 : 0, Kit.SNAPPY);
            box.background(disabled ? Color.rgba(Kit.surface(c).rgba() >>> 8, 0.5f) : Kit.surface(c));
            box.border(1, focused || invalid
                    ? Kit.mix(c.theme().border(), edge(c, focused, box.hovered(), invalid), k)
                    : edge(c, focused, box.hovered(), invalid));
            if (helper != null && !helper.isEmpty()) {
                Element note = text(c, helper).fontSize(11);
                note.textColor(invalid ? Kit.danger(c) : c.theme().textMuted());
            }
        });
    }

    /** The labeled multiline field (legacy shape kept). */
    public static Element areaField(Context c, String label, TextRef value, String placeholder,
                                    float minHeight) {
        return areaField(c, label, value, placeholder, null, false, false, minHeight);
    }

    /** The bare multiline input. */
    public static Element textarea(Context c, TextRef value, String placeholder) {
        return textArea(c, value).placeholder(placeholder).fillWidth().padding(8, 11)
                .radius(Kit.RADIUS_XS + 2).label("kit-textarea");
    }

    private Input() {}
}
