package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's rich text editor, after the RichTextEditor: a lightweight
 * editor with markdown shortcuts and a toolbar. The toolbar's bold,
 * italic, code and heading buttons wrap the selection's line in markdown
 * markers over the framework's text stack; the note under the field
 * reminds that output is markdown.
 */
public final class RichTextEditor {

    private static final class St {
        String last = "";
    }

    /** The editor: value, placeholder, min height, and a disabled state. */
    public static Element richTextEditor(Context c, TextRef value, String placeholder,
                                         float minHeight, boolean disabled) {
        Element host = column(c).fillWidth().gap(4).label("kit-rte");
        host.children(() -> {
            // The toolbar.
            row(c).gap(2).padding(2).radius(Kit.RADIUS_XS + 2).background(c.theme().surface())
                    .children(() -> {
                        tool(c, "B", () -> wrap(c, value, "**"));
                        tool(c, "I", () -> wrap(c, value, "*"));
                        tool(c, "</>", () -> wrap(c, value, "`"));
                        tool(c, "H", () -> heading(c, value));
                    });
            Element box = textArea(c, value).placeholder(placeholder).fillWidth()
                    .minHeight(minHeight).padding(8, 11).radius(Kit.RADIUS_XS + 2)
                    .label("kit-rte-field");
            if (disabled) {
                box.disabled(true);
            }
            boolean focused = box.focused();
            float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
            box.background(KitMotion.surface(c));
            box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : KitMotion.hairline(c));
            text(c, "Markdown in, markdown out").fontSize(10.5f).textColor(c.theme().textMuted());
        });
        return host;
    }

    /** One toolbar button. */
    private static void tool(Context c, String glyph, Runnable action) {
        Element b = buttonBase(c).size(26, 24).radius(Kit.RADIUS_XS).label("kit-rte-tool");
        if (b.hovered()) {
            b.background(c.theme().surfaceHover());
        }
        b.children(() -> text(c, glyph).singleLine().fontSize(glyph.length() > 1 ? 9.5f : 12)
                .fontWeight(600).textColor(c.theme().textMuted()));
        if (b.clicked()) {
            action.run();
        }
    }

    /** Wraps the whole value's trailing paragraph in markers; the caret's
     * paragraph needs the selection API, so the wrap lands on the tail. */
    private static void wrap(Context c, TextRef value, String marker) {
        String t = value.get() == null ? "" : value.get();
        if (t.endsWith(marker) && t.length() >= marker.length() * 2
                && t.substring(0, t.length() - marker.length()).endsWith(marker)) {
            value.set(t.substring(0, t.length() - marker.length() * 2));
        } else {
            value.set(t + marker + marker);
        }
        c.invalidate();
    }

    private static void heading(Context c, TextRef value) {
        String t = value.get() == null ? "" : value.get();
        value.set(t + (t.isEmpty() || t.endsWith("\n") ? "## " : "\n## "));
        c.invalidate();
    }

    public static Element richTextEditor(Context c, TextRef value) {
        return richTextEditor(c, value, "Write something…", 140, false);
    }

    private RichTextEditor() {}
}
