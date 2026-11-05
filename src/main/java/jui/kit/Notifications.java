package jui.kit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's notifications: toasts in a corner that fade on their own, after
 * gpui-kit's Notification. The queue is kit-global; build the host once per
 * window and call notify() from anywhere.
 */
public final class Notifications {
    /** One toast: its text, tone and the wall clock it dies at. */
    record Toast(String text, Kit.Variant variant, long dieAt) {}

    private static final List<Toast> QUEUE = new ArrayList<>();
    private static final long LIFETIME_MS = 3500;

    /** Adds a toast; call from any action. */
    public static void notify(String text, Kit.Variant variant) {
        QUEUE.add(new Toast(text, variant, System.currentTimeMillis() + LIFETIME_MS));
    }

    /** The overlay column; render it once, top-right of the window root. */
    public static Element host(Context c) {
        long now = System.currentTimeMillis();
        Iterator<Toast> it = QUEUE.iterator();
        while (it.hasNext()) {
            if (it.next().dieAt() <= now) it.remove();
        }
        return column(c).absolute().right(12).top(12).width(240).gap(6).children(() -> {
            for (int i = 0; i < QUEUE.size(); i++) {
                Toast t = QUEUE.get(i);
                Kit.Tone tone = Kit.tone(c, t.variant());
                box(c).fillWidth().radius(Kit.RADIUS_SM)
                        .background(c.theme().surface())
                        .border(1, Kit.line(c))
                        .padding(8, 10).gap(8).children(() -> {
                            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                                box(c).size(8, 8).radius(4).background(tone.fill());
                                text(c, t.text()).fontSize(12).textColor(c.theme().text());
                                spacer(c).grow(1);
                            });
                        });
            }
        });
    }

    private Notifications() {}
}
