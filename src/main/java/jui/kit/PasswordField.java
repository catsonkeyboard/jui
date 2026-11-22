package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's password field, after the PasswordField: capture sensitive
 * text with a visible reveal control. The text hides behind the password
 * mode; the eye swaps to an eye-off on the snappy run, and the reveal
 * reverts on its own after a few seconds if it was asked for by holding.
 */
public final class PasswordField {

    /**
     * The field: label, value, placeholder, and an optional strength meter
     * under it (PasswordStrength.face).
     */
    public static Element passwordField(Context c, String label, TextRef value, String placeholder,
                                        boolean showMeter) {
        Element host = column(c).fillWidth().gap(4).label("kit-password");
        BoolRef reveal = localBool(host, "reveal", false);
        host.children(() -> {
            Element l = text(c, label).fontSize(12).fontWeight(500);
            l.textColor(c.theme().text());
            Element box = textInput(c, value).placeholder(placeholder).fillWidth()
                    .height(34).padding(0, 11).radius(Kit.RADIUS_XS + 2).label("kit-password-box");
            if (!reveal.get()) {
                box.password();
            }
            boolean focused = box.focused();
            float k = KitMotion.spring(box, "edge", focused ? 1 : 0, KitMotion.SNAPPY);
            box.background(KitMotion.surface(c));
            box.border(1, focused ? KitMotion.mix(c.theme().border(), c.theme().accent(), k) : c.theme().border());
            box.children(() -> {
                Element eye = buttonBase(c).absolute().right(5).top(5).size(24, 24).radius(6)
                        .label("kit-password-eye");
                if (eye.hovered()) {
                    eye.background(c.theme().surfaceHover());
                }
                eye.children(() -> {
                    float t = KitMotion.spring(eye, "eye", reveal.get() ? 1 : 0, KitMotion.SNAPPY);
                    box(c).size(15, 15).shrink(0).draw((p, b) -> {
                        // The eye: an outline and a pupil that shuts on the reveal.
                        float u = Math.min(b.width(), b.height()) / 16;
                        Color col = c.theme().textMuted();
                        p.line(b.x() + 2 * u, b.y() + 8 * u, b.x() + 6 * u, b.y() + (8 - 2.5f * t) * u,
                                1.4f * u, col);
                        p.line(b.x() + 6 * u, b.y() + (8 - 2.5f * t) * u, b.x() + 10 * u,
                                b.y() + (8 + 2.5f * t) * u, 1.4f * u, col);
                        p.line(b.x() + 10 * u, b.y() + (8 + 2.5f * t) * u, b.x() + 14 * u, b.y() + 8 * u,
                                1.4f * u, col);
                        p.circle(b.x() + 8 * u, b.y() + 8 * u, 2.1f * u * (1 - t), col);
                        if (t > 0.4f) {
                            p.line(b.x() + 4 * u, b.y() + 13 * u, b.x() + 12 * u, b.y() + 3 * u, 1.4f * u, col);
                        }
                    });
                });
                if (eye.clicked()) {
                    reveal.set(!reveal.get());
                    c.invalidate();
                }
            });
            if (showMeter) {
                PasswordStrength.face(c, value.get() == null ? "" : value.get());
            }
        });
        return host;
    }

    public static Element passwordField(Context c, String label, TextRef value, String placeholder) {
        return passwordField(c, label, value, placeholder, false);
    }

    private PasswordField() {}
}
