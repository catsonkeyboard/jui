package jui.kit;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's input and textarea: labeled fields over the framework's text
 * stack (IME, undo, selection included), after gpui-kit's Input and Textarea.
 */
public final class Input {
    /** A labeled field: the caption above the box. */
    public static Element field(Context c, String label, TextRef value, String placeholder) {
        return column(c).fillWidth().gap(4).children(() -> {
            Label.label(c, label, Kit.Size.SMALL, Label.Variant.SECONDARY);
            textInput(c, value).placeholder(placeholder).fillWidth().label("kit-input-" + label);
        });
    }

    /** The bare input, kit-styled. */
    public static Element input(Context c, TextRef value, String placeholder) {
        return textInput(c, value).placeholder(placeholder).fillWidth().label("kit-input");
    }

    /** A labeled multiline field. */
    public static Element areaField(Context c, String label, TextRef value, String placeholder,
                                    float minHeight) {
        return column(c).fillWidth().gap(4).children(() -> {
            Label.label(c, label, Kit.Size.SMALL, Label.Variant.SECONDARY);
            textArea(c, value).placeholder(placeholder).fillWidth().minHeight(minHeight)
                    .label("kit-textarea-" + label);
        });
    }

    /** The bare multiline input. */
    public static Element textarea(Context c, TextRef value, String placeholder) {
        return textArea(c, value).placeholder(placeholder).fillWidth().label("kit-textarea");
    }

    private Input() {}
}
