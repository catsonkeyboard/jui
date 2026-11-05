package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's bubble and message: one chat turn — author, time, and a tinted
 * body aligned left or right — after gpui-kit's Bubble and Message.
 */
public final class Message {
    /** Which side of the transcript the bubble hugs. */
    public enum Side { LEFT, RIGHT }

    public static Element message(Context c, Side side, String author, String time,
                                  String body, Kit.Variant toneOf) {
        Kit.Tone tone = Kit.tone(c, toneOf);
        boolean mine = side == Side.RIGHT;
        return row(c).fillWidth().justify(mine ? Justify.END : Justify.START)
                .label("kit-msg-" + author).children(() -> {
                    column(c).width(460).gap(3).children(() -> {
                        row(c).gap(6).alignItems(Align.CENTER)
                                .justify(mine ? Justify.END : Justify.START).children(() -> {
                            if (!mine) {
                                Avatar.avatar(c, author, 18);
                            }
                            text(c, author).fontSize(11).bold().textColor(c.theme().text());
                            text(c, time).fontSize(10).textColor(c.theme().textMuted());
                            if (mine) {
                                text(c, "(我)").fontSize(10).textColor(tone.fill());
                            }
                        });
                        box(c).fillWidth().padding(8, 10).radius(Kit.RADIUS)
                                .background(mine
                                        ? jui.ui.Color.rgba(c.theme().accent().rgba() >>> 8, 0.14f)
                                        : c.theme().surface())
                                .children(() -> text(c, body).fontSize(12).textColor(c.theme().text()));
                    });
                });
    }

    private Message() {}
}
