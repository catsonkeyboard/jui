package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's password strength, after the PasswordStrength: show how
 * strong a new password is while it is typed. Four segments fill on the
 * snappy run as the score rises, and the label names the level in its
 * color; empty reads "Enter a password" in the muted tone.
 */
public final class PasswordStrength {

    /** The score 0..4 of a password: length, classes, and repeats. */
    public static int score(String password) {
        if (password == null || password.isEmpty()) {
            return 0;
        }
        int s = 0;
        if (password.length() >= 8) {
            s++;
        }
        boolean up = false, lo = false, di = false, pu = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            up |= Character.isUpperCase(ch);
            lo |= Character.isLowerCase(ch);
            di |= Character.isDigit(ch);
            pu |= !Character.isLetterOrDigit(ch);
        }
        int classes = (up ? 1 : 0) + (lo ? 1 : 0) + (di ? 1 : 0) + (pu ? 1 : 0);
        if (classes >= 2) {
            s++;
        }
        if (classes >= 3) {
            s++;
        }
        if (password.length() >= 12 && classes >= 3) {
            s++;
        }
        return Math.max(1, Math.min(4, s));
    }

    private static Color level(Context c, int score) {
        return switch (score) {
            case 1 -> KitMotion.danger(c);
            case 2 -> KitMotion.warning(c);
            case 3 -> Color.rgb(0x30A46C);
            default -> KitMotion.success(c);
        };
    }

    private static String word(int score) {
        return switch (score) {
            case 1 -> "Weak";
            case 2 -> "Fair";
            case 3 -> "Good";
            default -> "Strong";
        };
    }

    /** The meter: four segments and the level word, animated to the score. */
    public static Element face(Context c, String password) {
        int s = score(password);
        Color col = level(c, s);
        Element host = column(c).fillWidth().gap(5).label("kit-strength").alignItems(Align.STRETCH);
        float[] fill = new float[4];
        for (int i = 0; i < 4; i++) {
            fill[i] = KitMotion.spring(host, "seg" + i, i < s ? 1 : 0, KitMotion.SNAPPY);
        }
        host.children(() -> {
            row(c).gap(5).children(() -> {
                for (int i = 0; i < 4; i++) {
                    int ii = i;
                    box(c).height(4).grow(1).radius(2).background(c.theme().border()).children(() -> {
                        // The colored fill rides over the track, sized by the run.
                        box(c).absolute().left(0).top(0).bottom(0)
                                .width(host.bounds().width() * Math.max(0, Math.min(1, fill[ii])))
                                .background(col);
                    });
                }
            });
            if (s > 0 && password != null && !password.isEmpty()) {
                text(c, word(s)).singleLine().fontSize(11).textColor(col);
            } else {
                text(c, "Enter a password").singleLine().fontSize(11).textColor(c.theme().textMuted());
            }
        });
        return host;
    }

    private PasswordStrength() {}
}
