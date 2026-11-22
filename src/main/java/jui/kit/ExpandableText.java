package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's expandable text, after the ExpandableText: long text clamped
 * to a few lines with a soft fade, gliding open with Read more and closed
 * with Show less — shown only when the text overflows its clamp.
 */
public final class ExpandableText {

    private static final class St {
        boolean open;
    }

    /**
     * The block: the text, the line clamp while closed, the max width, and
     * the fade at the clamp's edge. The height runs on the morph spring.
     */
    public static Element expandableText(Context c, String text, int clampLines, float maxWidth,
                                         float lineHeight) {
        Element host = column(c).gap(4).maxWidth(maxWidth).label("kit-expandable-text");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        // The overflow read: the engine wraps the text, so the line count
        // comes from the laid-out height against the clamp.
        float clamped = clampLines * lineHeight + 2;
        float lines = text == null ? 0 : c.layoutText(text, 12.5f, 400, maxWidth).lines;
        boolean overflows = lines > clampLines;
        float h = Kit.spring(host, "h", st.open || !overflows ? lineHeight * Math.max(1, lines)
                : clamped, Kit.MORPH);
        Element body = box(c).height(Math.min(h, lineHeight * Math.max(1, lines))).clip()
                .label("kit-expandable-body");
        body.children(() -> text(c, text == null ? "" : text).fontSize(12.5f)
                .textColor(c.theme().text()));
        if (overflows) {
            host.children(() -> {
                if (!st.open) {
                    // The soft fade over the clamp's last line.
                    box(c).absolute().left(0).right(0).height(lineHeight)
                            .background(jui.ui.Color.rgba(c.theme().background().rgba() >>> 8, 0.75f))
                            .label("kit-expandable-fade");
                }
            });
            Element toggle = buttonBase(c).label("kit-expandable-toggle").padding(2, 0);
            toggle.children(() -> {
                text(c, st.open ? "Show less" : "Read more").singleLine().fontSize(11.5f)
                        .textColor(c.theme().accent());
                IconMorph.icon(c, IconMorph.CHEVRON, st.open, c.theme().accent(), 11);
            });
            if (toggle.clicked()) {
                st.open = !st.open;
                c.invalidate();
            }
        }
        return host;
    }

    public static Element expandableText(Context c, String text) {
        return expandableText(c, text, 3, 420, 18);
    }

    private ExpandableText() {}
}
