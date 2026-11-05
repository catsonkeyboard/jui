package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's description list: label–value pairs in a tidy column, after
 * gpui-kit's DescriptionList.
 */
public final class DescriptionList {
    /** One label–value pair. */
    public record Entry(String label, String value) {}

    public static Entry entry(String label, String value) {
        return new Entry(label, value);
    }

    public static Element descriptionList(Context c, Entry... entries) {
        return column(c).fillWidth().gap(6).children(() -> {
            for (Entry e : entries) {
                row(c).fillWidth().gap(10).alignItems(Align.CENTER).children(() -> {
                    text(c, e.label()).fontSize(12).textColor(c.theme().textMuted());
                    spacer(c).grow(1);
                    text(c, e.value()).fontSize(12).textColor(c.theme().text());
                });
            }
        });
    }

    private DescriptionList() {}
}
