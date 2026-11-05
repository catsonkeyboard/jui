package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Context;

import static jui.ui.Ui.richText;
import jui.ui.Element;
import jui.ui.Span;
import jui.ui.Spans;

/**
 * The kit's text view: markdown-lite over the framework's rich text stack —
 * **bold**, *italic*, `code`, and hard line breaks — after gpui-kit's
 * TextView (which renders full markdown through its text system).
 */
public final class TextView {
    /** Renders the markdown-lite string into a rich text element. */
    public static Element textView(Context c, String markdown) {
        Spans spans = Spans.of(parse(markdown).toArray(new Span[0]));
        return richText(c, spans).label("kit-textview");
    }

    static List<Span> parse(String md) {
        List<Span> out = new ArrayList<>();
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < md.length()) {
            if (md.startsWith("**", i)) {
                flush(out, plain, false, false, false);
                int end = md.indexOf("**", i + 2);
                if (end < 0) { plain.append("**"); i += 2; continue; }
                out.add(Span.text(md.substring(i + 2, end)).bold());
                i = end + 2;
            } else if (md.startsWith("`", i)) {
                flush(out, plain, false, false, false);
                int end = md.indexOf('`', i + 1);
                if (end < 0) { plain.append('`'); i++; continue; }
                out.add(code(md.substring(i + 1, end)));
                i = end + 1;
            } else if (md.charAt(i) == '\n') {
                flush(out, plain, false, false, false);
                out.add(Span.text("\n"));
                i++;
            } else {
                plain.append(md.charAt(i));
                i++;
            }
        }
        flush(out, plain, false, false, false);
        return out;
    }

    private static void flush(List<Span> out, StringBuilder plain, boolean bold, boolean italic, boolean code) {
        if (plain.length() > 0) {
            Span s = Span.text(plain.toString());
            out.add(s);
            plain.setLength(0);
        }
    }

    /** A code run: monospace on a soft chip. */
    public static Span code(String text) {
        return Span.text(text).monospace().background(jui.ui.Color.rgba(0x808080, 0.18f));
    }

    private TextView() {}
}
