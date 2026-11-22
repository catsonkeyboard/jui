package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's code block, after the CodeBlock: present code with legible
 * hierarchy and copy access. The header names the language and carries the
 * copy control, keywords and strings tint through the spans engine, and
 * the body scrolls when it overflows.
 */
public final class CodeBlock {

    /** The block: language name, the code text, and a max height. */
    public static Element codeBlock(Context c, String language, String code, float maxHeight) {
        Element host = column(c).fillWidth().radius(Kit.RADIUS + 2).clip()
                .background(c.theme().surface()).border(1, KitMotion.hairline(c))
                .label("kit-code-block");
        host.children(() -> {
            row(c).padding(6, 12).gap(8).alignItems(Align.CENTER).children(() -> {
                text(c, language).singleLine().fontSize(10.5f).textColor(c.theme().textMuted());
                spacer(c).grow(1);
                CopyButton.copyButton(c, code, "Copy", false, false, false);
            });
            divider(c);
            Element scroll = box(c).maxHeight(maxHeight).clip().padding(10, 12)
                    .label("kit-code-body");
            scroll.children(() -> {
                // The simplest safe highlight: line-leading comments and
                // quoted strings tint; the rest reads in the text color.
                for (String line : code.split("\n", -1)) {
                    String l = line;
                    Color col = c.theme().text();
                    String trimmed = l.stripLeading();
                    if (trimmed.startsWith("//") || trimmed.startsWith("#")) {
                        col = c.theme().textMuted();
                    } else if (trimmed.contains("\"")) {
                        col = Color.rgb(0x2EA043);
                    } else if (trimmed.startsWith("import ") || trimmed.startsWith("package ")
                            || trimmed.startsWith("from ") || trimmed.startsWith("def ")
                            || trimmed.startsWith("function ")) {
                        col = Color.rgb(0x8B5CF6);
                    }
                    text(c, l.isEmpty() ? " " : l).singleLine().fontSize(11)
                            .textColor(col);
                }
            });
        });
        return host;
    }

    public static Element codeBlock(Context c, String language, String code) {
        return codeBlock(c, language, code, 260);
    }

    private CodeBlock() {}
}
