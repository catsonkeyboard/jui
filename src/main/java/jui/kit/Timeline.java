package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's timeline, after the Timeline: follow what happened, newest
 * first, grouped by day. A hairline spine runs down the left, each event
 * carries a dot that fills when it is the newest, and the day groups read
 * in the muted tone above their events.
 */
public final class Timeline {

    /** One event: when, who, what. */
    public record Event(String day, String time, String who, String what) {}

    /** The timeline over the events in order (newest first). */
    public static Element timeline(Context c, Event[] events) {
        Element host = column(c).fillWidth().gap(0).label("kit-timeline");
        String lastDay = null;
        int n = events.length;
        for (int i = 0; i < n; i++) {
            Event e = events[i];
            int ii = i;
            if (!e.day().equals(lastDay)) {
                text(c, e.day()).singleLine().fontSize(10.5f).textColor(c.theme().textMuted())
                        .padding(0, 0, 6, 0);
                lastDay = e.day();
            }
            host.children(() -> {
                row(c).gap(10).alignItems(Align.START).children(() -> {
                    // The spine and dot.
                    box(c).size(22, 22).alignItems(Align.CENTER).children(() -> {
                        if (ii < n - 1) {
                            box(c).absolute().left(10).top(10).bottom(-10).width(1)
                                    .background(KitMotion.hairline(c));
                        }
                        box(c).size(8, 8).radius(4)
                                .background(ii == 0 ? c.theme().accent() : KitMotion.hairline(c));
                    });
                    column(c).gap(1).children(() -> {
                        row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                            text(c, e.who()).singleLine().fontSize(11.5f).fontWeight(500)
                                    .textColor(c.theme().text());
                            text(c, e.time()).singleLine().fontSize(10)
                                    .textColor(c.theme().textMuted());
                        });
                        text(c, e.what()).fontSize(11.5f).textColor(c.theme().textMuted());
                    });
                });
            });
        }
        return host;
    }

    private Timeline() {}
}
