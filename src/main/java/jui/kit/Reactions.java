package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's reactions, after the Reactions: acknowledge a post in one
 * tap, with counts that roll. Each reaction is a quiet pill with an emoji,
 * a count that slides to its new value on the morph run, and a picked
 * state that fills with the accent's tint. A trailing plus opens the rest.
 */
public final class Reactions {

    /** One reaction: its emoji, its count, whether the user picked it. */
    public record Reaction(String emoji, int count, boolean mine) {}

    private static final class St {
        int picked = -1;
        int[] counts;
    }

    /**
     * The row of pills. The counts change through the ref the caller keeps;
     * picking one moves the count by one and rolls it in place.
     */
    public static Element reactions(Context c, Reaction[] reactions) {
        Element host = row(c).gap(4).label("kit-reactions").alignItems(Align.CENTER);
        Ref<St> ref = local(host, "st", () -> {
            St s = new St();
            s.counts = new int[reactions.length];
            for (int i = 0; i < reactions.length; i++) {
                s.counts[i] = reactions[i].count();
            }
            return s;
        });
        St st = ref.get();
        if (st.counts.length != reactions.length) {
            st.counts = new int[reactions.length];
            for (int i = 0; i < reactions.length; i++) {
                st.counts[i] = reactions[i].count();
            }
        }
        for (int i = 0; i < reactions.length; i++) {
            int idx = i;
            boolean on = st.picked == idx;
            Element pill = buttonBase(c).height(26).padding(0, 9).gap(5).radius(13).shrink(0)
                    .label("kit-reaction-" + idx);
            if (on) {
                pill.background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.16f))
                        .border(1, c.theme().accent());
            } else {
                pill.background(Kit.surface(c)).border(1, Kit.hairline(c));
                if (pill.hovered()) {
                    pill.background(c.theme().surfaceHover());
                }
            }
            int shown = st.counts[idx] + (on && reactions[idx].count() == st.counts[idx] ? 1 : 0)
                    - (!on && reactions[idx].mine() && reactions[idx].count() == st.counts[idx] ? 1 : 0);
            pill.children(() -> {
                text(c, reactions[idx].emoji()).singleLine().fontSize(12.5f);
                // The count rolls: digits rise as the value changes.
                float roll = Kit.spring(host, "r" + idx, shown, Kit.MORPH);
                Element num = text(c, String.valueOf(shown)).singleLine().fontSize(11.5f);
                num.textColor(on ? c.theme().accent() : c.theme().textMuted());
                num.opacity(0.6f + 0.4f * (1 - Math.abs(roll - shown)));
            });
            if (pill.clicked()) {
                if (on) {
                    st.picked = -1;
                    st.counts[idx] = Math.max(0, st.counts[idx] - 1);
                } else {
                    if (st.picked >= 0) {
                        st.counts[st.picked] = Math.max(0, st.counts[st.picked] - 1);
                    }
                    st.picked = idx;
                    st.counts[idx] = st.counts[idx] + 1;
                }
                c.invalidate();
            }
        }
        return host;
    }

    private Reactions() {}
}
