package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's money input, after the MoneyInput: a currency field with
 * live grouping, a stable width, and minor-unit output. The digits group
 * as you type, the currency sits leading in the muted tone, and the
 * caller's LongRef holds cents so the value never drifts through floats.
 */
public final class MoneyInput {

    /** Groups the whole part with commas; keeps what the user typed after a separator. */
    static String group(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            out.append(digits.charAt(i));
            int left = digits.length() - 1 - i;
            if (left > 0 && left % 3 == 0) {
                out.append(',');
            }
        }
        return out.toString();
    }

    static long cents(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return 0;
        }
        long whole = Long.parseLong(digits);
        return whole * 100;
    }

    /**
     * The field: label, currency mark, the raw text ref the user types
     * into, and a LongRef out for cents. changed() reports a new value.
     */
    public static Element moneyInput(Context c, String label, String currency, TextRef raw, java.util.function.LongConsumer cents) {
        return column(c).fillWidth().gap(4).label("kit-money-" + label).children(() -> {
            if (label != null && !label.isEmpty()) {
                Element l = text(c, label).fontSize(12).fontWeight(500);
                l.textColor(c.theme().text());
            }
            Element box = textInput(c, raw).fillWidth().height(34).padding(0, 12).radius(Kit.RADIUS_XS + 2)
                    .label("kit-money-box");
            boolean focused = box.focused();
            float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
            box.background(KitMotion.surface(c));
            box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            box.children(() -> row(c).absolute().left(12).top(0).bottom(0).gap(3)
                    .alignItems(jui.ui.Align.CENTER).children(() -> {
                        text(c, currency).singleLine().fontSize(12).textColor(c.theme().textMuted());
                        spacer(c).width(2);
                    }));
            // Live grouping: reformat around the caret each time the text changes.
            String typed = raw.get() == null ? "" : raw.get();
            String clean = typed.replace(",", "");
            String grouped = group(clean);
            if (!typed.equals(grouped) && !typed.isEmpty()) {
                raw.set(grouped);
                c.invalidate();
            }
            if (box.changed()) {
                cents.accept(cents(clean));
            }
        });
    }

    private MoneyInput() {}
}
