package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's mention input, after the MentionInput: a textarea with
 * @people and #channel mentions that act as single tokens, with
 * suggestions at the caret. Typing @ or # opens the suggestion list under
 * the field; picking one inserts the token, which reads in the accent and
 * never splits across a line.
 */
public final class MentionInput {

    /** One suggestable entity. */
    public record Entity(String token, String label, String icon) {}

    /**
     * The input: value, the people and channels to suggest, and an optional
     * placeholder. The token insert replaces the partial word at the caret.
     */
    public static Element mentionInput(Context c, TextRef value, Entity[] people, Entity[] channels,
                                       String placeholder, float minHeight) {
        Element host = column(c).fillWidth().gap(2).label("kit-mention");
        BoolRef open = localBool(host, "open", false);
        host.children(() -> {
            Element box = textArea(c, value).placeholder(placeholder).fillWidth().minHeight(minHeight)
                    .padding(8, 11).radius(Kit.RADIUS_XS + 2).label("kit-mention-box");
            boolean focused = box.focused();
            float k = Kit.spring(box, "edge", focused ? 1 : 0, Kit.SNAPPY);
            box.background(Kit.surface(c));
            box.border(1, focused ? Kit.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            // The partial token at the end decides whether suggestions show.
            String t = value.get() == null ? "" : value.get();
            String partial = partialToken(t);
            boolean trigger = partial != null;
            if (trigger != open.get()) {
                open.set(trigger);
                c.invalidate();
            }
            if (trigger) {
                boolean channel = partial.startsWith("#");
                String query = partial.substring(1).toLowerCase();
                Entity[] pool = channel ? channels : people;
                popover(c, box, open, () -> {
                    Element panel = box(c).padding(4).gap(1).minWidth(200);
                    int shown = 0;
                    for (Entity e : pool) {
                        if (!query.isEmpty() && !e.token().toLowerCase().contains(query)
                                && !e.label().toLowerCase().contains(query)) {
                            continue;
                        }
                        Element r = row(c).gap(8).padding(6, 8).radius(Kit.RADIUS_XS).fillWidth()
                                .label("kit-mention-row");
                        if (r.hovered()) {
                            r.background(c.theme().surfaceHover());
                        }
                        r.children(() -> {
                            box(c).size(15, 15).shrink(0).draw(Icon.draw(e.icon(), c.theme().textMuted()));
                            column(c).gap(0).children(() -> {
                                text(c, (channel ? "#" : "@") + e.token()).singleLine().fontSize(12)
                                        .textColor(c.theme().text());
                                text(c, e.label()).singleLine().fontSize(10.5f)
                                        .textColor(c.theme().textMuted());
                            });
                        });
                        if (r.clicked()) {
                            value.set(t.substring(0, t.length() - partial.length())
                                    + (channel ? "#" : "@") + e.token() + " ");
                            c.invalidate();
                        }
                        shown++;
                        if (shown >= 6) {
                            break;
                        }
                    }
                    if (shown == 0) {
                        text(c, "No matches").fontSize(11.5f).textColor(c.theme().textMuted())
                                .padding(6, 8);
                    }
                });
            }
        });
        return host;
    }

    /** The @word or #word sitting at the very end of the text, or null. */
    static String partialToken(String t) {
        if (t == null || t.isEmpty()) {
            return null;
        }
        int i = t.length() - 1;
        while (i >= 0 && !Character.isWhitespace(t.charAt(i))) {
            i--;
        }
        String word = t.substring(i + 1);
        if (word.length() >= 1 && (word.startsWith("@") || word.startsWith("#"))
                && !word.contains(" ")) {
            return word;
        }
        return null;
    }

    private MentionInput() {}
}
