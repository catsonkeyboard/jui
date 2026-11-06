package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's settings page: titled rows of label-left control-right lines in
 * soft cards, after gpui-kit's Settings.
 */
public final class Settings {
    /** One line: the label and the control built beside it. */
    public record Line(String label, Runnable control) {}

    public static Line line(String label, Runnable control) {
        return new Line(label, control);
    }

    public static Element page(Context c, String heading, Line... lines) {
        return box(c).grow(1).fillWidth().clip().children(() ->
                scroll(c).grow(1).children(() ->
                column(c).fillWidth().padding(16, 20).gap(8).children(() -> {
                    if (heading != null && !heading.isEmpty()) {
                        text(c, heading).fontSize(15).bold().textColor(c.theme().text());
                    }
                    for (Line line : lines) {
                        row(c).fillWidth().height(36).radius(Kit.RADIUS_SM)
                                .background(c.theme().surface())
                                .padding(0, 12).gap(10).alignItems(Align.CENTER)
                                .label("kit-set-" + line.label())
                                .children(() -> {
                                    text(c, line.label()).fontSize(12).textColor(c.theme().text());
                                    spacer(c).grow(1);
                                    line.control().run();
                                });
                    }
                })));
    }

    private Settings() {}
}
