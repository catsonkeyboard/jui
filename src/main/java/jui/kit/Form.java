package jui.kit;

import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's form: labeled fields in a column with a submit row, reporting
 * one submit — the layout half of gpui-kit's Form (validation rules ride the
 * caller's own state here).
 */
public final class Form {
    /** One field row: label on the left, control on the right. */
    public record Field(String label, Runnable control) {}

    public static Field field(String label, Runnable control) {
        return new Field(label, control);
    }

    public static Element form(Context c, String heading, Field[] fields, String submitLabel,
                               Runnable onSubmit) {
        return column(c).fillWidth().gap(12).children(() -> {
            if (heading != null && !heading.isEmpty()) {
                text(c, heading).fontSize(14).bold().textColor(c.theme().text());
            }
            for (Field f : fields) {
                row(c).fillWidth().gap(10).alignItems(jui.ui.Align.CENTER).children(() -> {
                    text(c, f.label()).fontSize(12).textColor(c.theme().text()).width(90);
                    f.control().run();
                });
            }
            row(c).fillWidth().justify(jui.ui.Justify.END).children(() -> {
                Element submit = Button.button(c, submitLabel, Kit.Variant.PRIMARY, Kit.Size.SMALL);
                if (submit.clicked() && onSubmit != null) onSubmit.run();
            });
        });
    }

    private Form() {}
}
