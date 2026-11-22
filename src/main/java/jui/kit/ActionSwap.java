package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's action swap, after the ActionSwap: a compact pill that steps
 * through related states on each press (Follow to Following, Send to Sent),
 * morphing its width, tone and label together on the morph run. A working
 * phase holds the pill; a failure shows the failure word for a moment.
 */
public final class ActionSwap {

    /** One state of the cycle. */
    public record State(String id, String label, String icon, String tone, String pendingLabel, int holdForMs) {
        public State(String id, String label) {
            this(id, label, null, "neutral", null, 0);
        }
    }

    /** Tones a face takes: the fill and the text that reads on it. */
    private static Kit.Tone tone(Context c, String tone) {
        return switch (tone == null ? "neutral" : tone) {
            case "accent" -> Kit.tone(c, Kit.Variant.PRIMARY);
            case "success" -> Kit.tone(c, Kit.Variant.SUCCESS);
            case "warning" -> Kit.tone(c, Kit.Variant.WARNING);
            case "quiet" -> Kit.tone(c, Kit.Variant.GHOST);
            default -> Kit.tone(c, Kit.Variant.DEFAULT);
        };
    }

    private static final class St {
        int index;
        boolean busy;
        boolean failed;
        long failedAt;
        long holdAt;
    }

    private static float textWidth(Context c, String s, float size) {
        return s == null || s.isEmpty() ? 0 : c.layoutText(s, size, 500, 4096).width;
    }

    /**
     * The pill: sm, md or lg, the states in order, and the work callback.
     * The callback receives the next state; a rejection (throw) shows the
     * failure word and stays put. holdFor keeps a state before advancing.
     */
    public static Element swap(Context c, State[] states, String failureLabel, Button.Size size,
                               java.util.function.Consumer<State> onAction) {
        float h = Button.height(size);
        float fs = Button.font(size);
        float pad = Button.padX(size);
        Element host = row(c).label("kit-action-swap").alignItems(Align.CENTER);
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        long now = c.now();
        if (st.failed && now - st.failedAt > 1_800_000_000L) {
            st.failed = false;
            c.invalidate();
        }
        if (st.holdAt > 0 && states[st.index].holdForMs() > 0 && now - st.holdAt > states[st.index].holdForMs() * 1_000_000L) {
            st.holdAt = 0;
            st.index = (st.index + 1) % states.length;
            c.invalidate();
        }
        State cur = states[st.index];
        Kit.Tone tone = tone(c, st.failed ? "warning" : cur.tone());
        String word = st.failed ? failureLabel : cur.label();
        float w = pad * 2 + textWidth(c, word, fs) + (cur.icon() != null ? 16 + 7 : 0);
        // The width, padding and tone move on one morph run.
        float animW = KitMotion.spring(host, "w", w, KitMotion.MORPH);
        Element b = buttonBase(c).height(h).width(animW).radius(h / 2).shrink(0)
                .label("kit-swap-face").background(tone.fill());
        if (b.hovered() && !st.busy) {
            b.background(tone.fillHover());
        }
        if (st.busy) {
            b.disabled(true);
        }
        float dip = KitMotion.press(b);
        b.children(() -> {
            if (cur.icon() != null) {
                box(c).size(16, 16).shrink(0).opacity(1 - 0.1f * dip).draw(Icon.draw(cur.icon(), tone.text()));
            }
            Element l = text(c, word).singleLine().fontSize(fs).opacity(1 - 0.1f * dip);
            l.textColor(tone.text());
        });
        if (!st.busy && b.clicked() && states.length >= 2) {
            st.index = (st.index + 1) % states.length;
            State next = states[st.index];
            if (onAction != null) {
                try {
                    onAction.accept(next);
                    st.holdAt = next.holdForMs() > 0 ? c.now() : 0;
                    if (next.holdForMs() > 0) {
                        c.after(java.time.Duration.ofMillis(next.holdForMs() + 40));
                    }
                } catch (RuntimeException e) {
                    st.failed = true;
                    st.failedAt = c.now();
                    st.index = (st.index - 1 + states.length) % states.length;
                    c.after(java.time.Duration.ofNanos(1_840_000_000));
                }
            }
            c.invalidate();
        }
        return b;
    }

    public static Element swap(Context c, State[] states, java.util.function.Consumer<State> onAction) {
        return swap(c, states, "Try again", Button.Size.MD, onAction);
    }

    private ActionSwap() {}
}
