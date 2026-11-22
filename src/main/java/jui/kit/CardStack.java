package jui.kit;

import java.util.ArrayDeque;
import java.util.Deque;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's card stack, after the CardStack: review a deck one card at a
 * time, with a throw and an undo. The top card follows the drag and
 * rotates as it goes — the engine cannot rotate, so it leans through a
 * horizontal offset instead — past the throw line it flies off, and Undo
 * brings the last throw back.
 */
public final class CardStack {

    private static final class St {
        final Deque<Integer> thrown = new ArrayDeque<>();
    }

    /** The stack: the card count, the top card's builder, and its size. */
    public static Element cardStack(Context c, int count, float width, float height,
                                    java.util.function.IntConsumer render) {
        Element host = box(c).size(width, height).alignItems(Align.CENTER)
                .label("kit-card-stack");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        int[] next = {-1};
        int top = topCard(count, st);
        // The next card peeks behind at a smaller scale, as offset frames.
        if (top + 1 < count) {
            box(c).absolute().left(width * 0.06f).top(8).width(width - width * 0.12f)
                    .height(height - 16).radius(Kit.RADIUS + 4).background(c.theme().surface())
                    .border(1, Kit.hairline(c)).opacity(0.6f).label("kit-stack-under");
        }
        Element card = box(c).absolute().left(0).top(0).size(width, height).radius(Kit.RADIUS + 4)
                .background(c.theme().background()).border(1, Kit.hairline(c)).clip()
                .draggable().label("kit-stack-card");
        float dx = card.dragX();
        boolean held = card.pressed();
        float lean = held ? Math.max(-40, Math.min(40, dx * 0.35f)) : 0;
        float animLean = Kit.spring(card, "lean", lean, Kit.MORPH);
        card.left(animLean);
        // The card tilts as it leans: rotation radians from the offset.
        card.rotate(animLean * 0.006f);
        card.children(() -> {
            box(c).fillWidth().grow(1).children(() -> render.accept(top));
            row(c).fillWidth().padding(10, 14).gap(8).alignItems(Align.CENTER).children(() -> {
                Element pass = buttonBase(c).size(40, 40).radius(20).background(Kit.surface(c))
                        .border(1, Kit.hairline(c)).label("kit-stack-pass");
                pass.children(() -> box(c).size(15, 15).shrink(0)
                        .draw(Icon.draw("xmark", c.theme().textMuted())));
                if (pass.clicked()) {
                    next[0] = 1;
                }
                spacer(c).grow(1);
                Element like = buttonBase(c).size(40, 40).radius(20)
                        .background(Color.rgba(Kit.success(c).rgba() >>> 8, 0.16f))
                        .border(1, Kit.success(c)).label("kit-stack-like");
                like.children(() -> box(c).size(15, 15).shrink(0)
                        .draw(Icon.draw("check", Kit.success(c))));
                if (like.clicked()) {
                    next[0] = 2;
                }
            });
        });
        if (next[0] > 0 && top >= 0) {
            st.thrown.push(top);
            c.invalidate();
        }
        if (held && Math.abs(dx) > width * 0.4f && top >= 0) {
            st.thrown.push(top);
            c.invalidate();
        }
        // Undo brings the last throw back.
        if (!st.thrown.isEmpty()) {
            Element undo = buttonBase(c).size(28, 28).radius(14).background(Kit.surface(c))
                    .border(1, Kit.hairline(c)).label("kit-stack-undo");
            undo.left(-36).top(height / 2 - 14);
            undo.children(() -> box(c).size(13, 13).shrink(0)
                    .draw(Icon.draw("arrow-left", c.theme().textMuted())));
            if (undo.clicked()) {
                st.thrown.pop();
                c.invalidate();
            }
        }
        return host;
    }

    private static int topCard(int count, St st) {
        int seen = st.thrown.size();
        return count - 1 - seen;
    }

    private CardStack() {}
}
