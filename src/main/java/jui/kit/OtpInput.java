package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's OTP input: N display cells filled from one capture field beside
 * them — typing digits fills the cells left to right, after gpui-kit's
 * OtpInput (whose per-cell focus hop needs focus plumbing this framework's
 * single-focus model handles via the capture field).
 */
public final class OtpInput {
    public static Element otp(Context c, TextRef value, int digits, float boxSize) {
        return column(c).gap(6).children(() -> {
            row(c).gap(6).children(() -> {
                String current = value.get();
                for (int i = 0; i < digits; i++) {
                    String digit = i < current.length() ? current.substring(i, i + 1) : "";
                    boolean next = i == Math.min(current.length(), digits - 1);
                    box(c).size(boxSize, boxSize).shrink(0).radius(Kit.RADIUS_XS)
                            .border(next ? 1.5f : 1, next ? c.theme().accent() : Kit.line(c))
                            .background(c.theme().surface())
                            .alignItems(Align.CENTER)
                            .label("kit-otp-" + i)
                            .children(() -> {
                                if (!digit.isEmpty()) {
                                    text(c, digit).fontSize(boxSize * 0.5f)
                                            .textColor(c.theme().text());
                                }
                            });
                }
            });
            Element capture = textInput(c, value).width(boxSize * 1.6f)
                    .placeholder("输入验证码").label("kit-otp-capture");
            if (capture.changed()) {
                String digits1 = value.get().replaceAll("[^0-9]", "");
                if (!digits1.equals(value.get())) value.set(digits1);
                if (digits1.length() > digits) value.set(digits1.substring(0, digits));
            }
        });
    }

    private OtpInput() {}
}
