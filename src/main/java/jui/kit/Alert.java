package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's alert: a tinted banner with icon, optional title and an optional
 * close, in info / success / warning / danger, after gpui-kit's Alert.
 * Openness lives in the caller's BoolRef; closed alerts build nothing.
 */
public final class Alert {
    public static Element alert(Context c, Kit.Variant v, String title, String message,
                                boolean closable, jui.ui.BoolRef open) {
        Kit.Tone tone = Kit.tone(c, v);
        String icon = switch (v) {
            case SUCCESS -> "check";
            case WARNING -> "exclamationmark-triangle";
            case DANGER -> "xmark-circle";
            default -> "info-circle";
        };
        return column(c).fillWidth().children(() -> {
            if (!open.get()) return;
            box(c).fillWidth().radius(Kit.RADIUS)
                    .background(jui.ui.Color.rgba(tone.fill().rgba() >>> 8, 0.14f))
                    .border(1, jui.ui.Color.rgba(tone.fill().rgba() >>> 8, 0.35f))
                    .label("kit-alert")
                    .padding(10, 12).gap(6).children(() -> {
                        row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                            box(c).size(16, 16).draw(Icon.draw(icon, tone.fill()));
                            if (title != null && !title.isEmpty()) {
                                text(c, title).fontSize(12.5f).bold().textColor(tone.fill());
                            }
                            spacer(c).grow(1);
                            if (closable) {
                                Element x = buttonBase(c).size(16, 16).radius(4)
                                        .label("kit-alert-close")
                                        .children(() -> box(c).size(10, 10)
                                                .draw(Icon.draw("xmark", tone.fill())));
                                if (x.clicked()) open.set(false);
                            }
                        });
                        if (message != null && !message.isEmpty()) {
                            text(c, message).fontSize(12).textColor(c.theme().textMuted());
                        }
                    });
        });
    }

    public static Element alert(Context c, Kit.Variant v, String message) {
        return alert(c, v, "", message, false, new jui.ui.BoolRef(true));
    }

    private Alert() {}
}
