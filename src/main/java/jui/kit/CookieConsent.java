package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's cookie consent, after the CookieConsent: a consent bar that
 * morphs into preferences and folds into a reopen button. Accept and
 * Decline answer at once; Customize opens the toggle rows, and once
 * answered a small chip can reopen the bar.
 */
public final class CookieConsent {

    /** One preference row. */
    public record Pref(String label, String description, boolean locked) {}

    private static final class St {
        int state; // 0 open bar, 1 preferences, 2 answered
        boolean analytics;
        boolean marketing;
    }

    /** The bar at the caller's chosen corner. */
    public static Element cookieConsent(Context c, String text, Pref[] prefs,
                                        java.util.function.Consumer<String> onAnswer) {
        Element host = box(c).label("kit-cookie-consent");

        host.children(() -> {
            Ref<St> ref = local(host, "st", St::new);
            St st = ref.get();
            if (st.state == 2) {
                Element chip = buttonBase(c).size(30, 30).radius(15).background(Kit.surface(c))
                        .border(1, Kit.hairline(c)).label("kit-cookie-reopen");
                chip.children(() -> box(c).size(14, 14).shrink(0)
                        .draw(Icon.draw("lock", c.theme().textMuted())));
                if (chip.clicked()) {
                    st.state = 0;
                    c.invalidate();
                }
            } else {
                Element card = column(c).width(360).padding(14).gap(8).radius(Kit.RADIUS + 3)
                        .background(c.theme().background()).border(1, Kit.hairline(c))
                        .label("kit-cookie-card");
                Kit.floatShadow(card);
                float w = Kit.spring(card, "w", st.state == 1 ? 320 : 360, Kit.MORPH);
                card.width(w);
                card.children(() -> {
                    row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                        box(c).size(16, 16).shrink(0).draw(Icon.draw("lock", c.theme().accent()));
                        text(c, st.state == 1 ? "Cookie preferences" : "We use cookies")
                                .singleLine().fontSize(12.5f).fontWeight(500).textColor(c.theme().text());
                    });
                    if (st.state == 0) {
                        text(c, text).fontSize(11.5f).textColor(c.theme().textMuted());
                    } else {
                        for (Pref p : prefs) {
                            row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                                column(c).gap(0).grow(1).children(() -> {
                                    text(c, p.label()).singleLine().fontSize(11.5f).textColor(c.theme().text());
                                    text(c, p.description()).singleLine().fontSize(10.5f)
                                            .textColor(c.theme().textMuted());
                                });
                                if (p.locked()) {
                                    text(c, "Always on").singleLine().fontSize(10.5f)
                                            .textColor(c.theme().textMuted());
                                } else {
                                    boolean on = p.label().startsWith("A") ? st.analytics : st.marketing;
                                    Element track = buttonBase(c).size(34, 19).radius(10).padding(2)
                                            .label("kit-cookie-switch");
                                    float pos = Kit.spring(track, "knob", on ? 1 : 0, Kit.SNAPPY);
                                    track.background(Kit.mix(c.theme().border(), c.theme().accent(), pos));
                                    track.children(() -> box(c).size(15, 15).radius(8)
                                            .background(Color.rgb(0xFFFFFF)).margin(0, 0, 0, 15 * pos));
                                    if (track.clicked()) {
                                        if (p.label().startsWith("A")) {
                                            st.analytics = !st.analytics;
                                        } else {
                                            st.marketing = !st.marketing;
                                        }
                                        c.invalidate();
                                    }
                                }
                            });
                        }
                    }
                    row(c).gap(6).justify(jui.ui.Justify.END).children(() -> {
                        if (st.state == 0) {
                            Element custom = buttonBase(c).height(28).padding(0, 10).radius(Kit.RADIUS_XS + 2)
                                    .label("kit-cookie-custom");
                            custom.children(() -> text(c, "Customize").singleLine().fontSize(11.5f)
                                    .textColor(c.theme().textMuted()));
                            if (custom.clicked()) {
                                st.state = 1;
                                c.invalidate();
                            }
                        }
                        Element decline = buttonBase(c).height(28).padding(0, 10).radius(Kit.RADIUS_XS + 2)
                                .border(1, Kit.hairline(c)).label("kit-cookie-decline");
                        decline.children(() -> text(c, "Decline").singleLine().fontSize(11.5f)
                                .textColor(c.theme().text()));
                        if (decline.clicked()) {
                            st.state = 2;
                            if (onAnswer != null) {
                                onAnswer.accept("declined");
                            }
                            c.invalidate();
                        }
                        Element accept = Button.builder(c, "Accept", Button.Variant.PRIMARY, Button.Size.SM);
                        if (accept.clicked()) {
                            st.state = 2;
                            if (onAnswer != null) {
                                onAnswer.accept("accepted");
                            }
                            c.invalidate();
                        }
                    });
                });
            }
        });
        return host;
    }

    private CookieConsent() {}
}
