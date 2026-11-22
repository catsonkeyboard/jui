package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's phone input, after the PhoneInput: a phone field with a
 * country picker, formatting as you type, and E.164 output. The dial code
 * sits leading behind a chevron; the picker lists the countries the caller
 * gave; the number groups as it types and the out ref carries +CC and the
 * national digits.
 */
public final class PhoneInput {

    /** One country: its name, dial code and flag emoji. */
    public record Country(String name, String dial, String flag) {}

    public static final Country[] COMMON = {
            new Country("United States", "+1", "🇺🇸"),
            new Country("United Kingdom", "+44", "🇬🇧"),
            new Country("Germany", "+49", "🇩🇪"),
            new Country("France", "+33", "🇫🇷"),
            new Country("Japan", "+81", "🇯🇵"),
            new Country("China", "+86", "🇨🇳"),
            new Country("Australia", "+61", "🇦🇺"),
            new Country("Brazil", "+55", "🇧🇷"),
    };

    /** Groups a national number the readable way: 3-3-4 as far as it goes. */
    static String format(String digits) {
        String d = digits.replaceAll("[^0-9]", "");
        StringBuilder out = new StringBuilder();
        int[] groups = {3, 3, 4};
        int at = 0;
        for (int g : groups) {
            if (at >= d.length()) {
                break;
            }
            if (at > 0) {
                out.append(' ');
            }
            int end = Math.min(d.length(), at + g);
            out.append(d, at, end);
            at = end;
        }
        if (at < d.length()) {
            out.append(' ').append(d, at, d.length());
        }
        return out.toString();
    }

    /**
     * The field: label, the countries to offer, the current country ref,
     * the typed text, and the E.164 out. changed() reports a new number.
     */
    public static Element phoneInput(Context c, String label, Country[] countries,
                                     jui.ui.Ref<Country> current, TextRef raw, TextRef e164) {
        Element host = column(c).fillWidth().gap(4).label("kit-phone-" + label);
        BoolRef open = localBool(host, "open", false);
        host.children(() -> {
            if (label != null && !label.isEmpty()) {
                text(c, label).fontSize(12).fontWeight(500).textColor(c.theme().text());
            }
            Element box = textInput(c, raw).fillWidth().height(34).padding(0, 12).radius(Kit.RADIUS_XS + 2)
                    .label("kit-phone-box");
            boolean focused = box.focused();
            float k = Kit.spring(box, "edge", focused ? 1 : 0, Kit.SNAPPY);
            box.background(Kit.surface(c));
            box.border(1, focused ? Kit.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            box.children(() -> {
                Element dial = buttonBase(c).absolute().left(0).top(0).bottom(0).gap(4).padding(0, 9)
                        .radius(Kit.RADIUS_XS + 2).label("kit-phone-dial");
                if (dial.hovered()) {
                    dial.background(c.theme().surfaceHover());
                }
                Country cur = current.get();
                dial.children(() -> {
                    text(c, cur.flag()).singleLine().fontSize(13);
                    text(c, cur.dial()).singleLine().fontSize(12).textColor(c.theme().textMuted());
                    box(c).size(9, 9).shrink(0).draw(Icon.draw("chevron-down", c.theme().textMuted()));
                });
                if (dial.clicked()) {
                    open.set(!open.get());
                }
            });
            // Formatting as it types.
            String typed = raw.get() == null ? "" : raw.get();
            String digits = typed.replaceAll("[^0-9]", "");
            String shown = format(digits);
            if (!typed.equals(shown)) {
                raw.set(shown);
                c.invalidate();
            }
            e164.set(digits.isEmpty() ? "" : current.get().dial() + digits);
            if (open.get()) {
                popover(c, box, open, () -> {
                    Element panel = box(c).padding(4).gap(1).minWidth(190);
                    for (Country co : countries) {
                        Element r = row(c).gap(8).padding(6, 8).radius(Kit.RADIUS_XS).fillWidth()
                                .label("kit-phone-country");
                        if (r.hovered()) {
                            r.background(c.theme().surfaceHover());
                        }
                        r.children(() -> {
                            text(c, co.flag()).singleLine().fontSize(13);
                            text(c, co.name()).singleLine().fontSize(12).textColor(c.theme().text());
                            spacer(c).grow(1);
                            text(c, co.dial()).singleLine().fontSize(11.5f).textColor(c.theme().textMuted());
                        });
                        if (r.clicked()) {
                            current.set(co);
                            open.set(false);
                            c.invalidate();
                        }
                    }
                });
            }
        });
        return host;
    }

    private PhoneInput() {}
}
