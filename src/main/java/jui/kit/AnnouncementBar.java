package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's announcement bar, after the AnnouncementBar: a top banner
 * that rotates messages on a timer, counts down to an event, and collapses
 * smoothly when dismissed. The height glides shut on the morph run, and a
 * reopen chip can bring it back.
 */
public final class AnnouncementBar {

    private static final long ROTATE = 5_000_000_000L;

    private static final class St {
        int index;
        long rotatedAt;
        boolean dismissed;
        boolean reopening;
    }

    /** The banner: the messages to rotate, an optional countdown deadline, and the accent. */
    public static Element announcementBar(Context c, String[] messages, java.time.Instant deadline,
                                          String cta, Runnable onCta) {
        Element host = column(c).fillWidth().label("kit-announcement");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        long now = c.now();
        boolean closed = st.dismissed && !st.reopening;
        host.children(() -> {
        if (closed) {
            return;
        }
        if (st.dismissed && st.reopening) {
            st.reopening = false;
            st.dismissed = false;
            st.rotatedAt = now;
        }
        if (messages.length > 1 && now - st.rotatedAt > ROTATE) {
            st.index = (st.index + 1) % messages.length;
            st.rotatedAt = now;
            c.invalidate();
        }
        float h = Kit.spring(host, "h", st.dismissed ? 0 : 36, Kit.MORPH);
        if (h < 1) {
            row(c).fillWidth().height(20).justify(jui.ui.Justify.CENTER).children(() -> {
                Element reopen = buttonBase(c).padding(2, 8).radius(10).background(Kit.surface(c))
                        .border(1, Kit.hairline(c)).label("kit-announcement-reopen");
                reopen.children(() -> text(c, "Announcements").singleLine().fontSize(10)
                        .textColor(c.theme().textMuted()));
                if (reopen.clicked()) {
                    st.reopening = true;
                    c.invalidate();
                }
            });
            return;
        }
        c.after(java.time.Duration.ofMillis(500));
        Element bar = row(c).fillWidth().height(36).padding(0, 14).gap(8)
                .alignItems(Align.CENTER).clip().label("kit-announcement-bar");
        bar.background(c.theme().accent());
        bar.children(() -> {
            box(c).size(14, 14).shrink(0).draw(Icon.draw("bell", c.theme().accentText()));
            // The rotating message, crossfading as it turns.
            float fade = Math.min(1, (now - st.rotatedAt) / 400_000_000f);
            Element msg = text(c, messages[st.index]).singleLine().fontSize(12)
                    .textColor(c.theme().accentText());
            msg.opacity(fade);
            if (deadline != null) {
                long left = Math.max(0, deadline.toEpochMilli() * 1_000_000L - now);
                long days = left / 86_400_000_000L;
                text(c, days > 0 ? days + "d left" : "Ends today").singleLine().fontSize(11)
                        .textColor(Color.rgba(c.theme().accentText().rgba() >>> 8, 0.8f));
            }
            spacer(c).grow(1);
            if (cta != null && onCta != null) {
                Element go = buttonBase(c).height(24).padding(0, 10).radius(12)
                        .background(Color.rgba(0xFFFFFF, 0.18f)).label("kit-announcement-cta");
                go.children(() -> text(c, cta).singleLine().fontSize(11)
                        .textColor(c.theme().accentText()));
                if (go.clicked()) {
                    onCta.run();
                }
            }
            Element x = buttonBase(c).size(22, 22).radius(6).label("kit-announcement-close");
            x.children(() -> box(c).size(10, 10).shrink(0)
                    .draw(Icon.draw("xmark", c.theme().accentText())));
            if (x.clicked()) {
                st.dismissed = true;
                c.invalidate();
            }
        });
        });
        return host;
    }

    private AnnouncementBar() {}
}
