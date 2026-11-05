package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.TextRef;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's editor: a syntax-less plain code field over the framework's
 * editor primitives — monospace text area with line height suited to code,
 * after gpui-kit's Editor (which carries full language support).
 */
public final class Editor {
    public static Element editor(Context c, TextRef code, float minHeight) {
        return column(c).fillWidth().gap(4).children(() -> {
            row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                box(c).size(12, 12).draw(Icon.draw("doc", c.theme().textMuted()));
                Label.label(c, "code", Kit.Size.XSMALL, Label.Variant.MUTED);
            });
            box(c).fillWidth().radius(Kit.RADIUS_SM).border(1, Kit.line(c))
                    .background(c.theme().background())
                    .children(() -> textArea(c, code).fillWidth().minHeight(minHeight)
                            .label("kit-editor"));
        });
    }

    private Editor() {}
}
