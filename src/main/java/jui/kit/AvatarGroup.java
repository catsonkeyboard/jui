package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's avatar group, after the AvatarGroup: show a team or set of
 * contributors in a small space. The faces overlap by a third, the ring
 * each carries separates it from its neighbours, and the trailing counter
 * rolls when the list changes.
 */
public final class AvatarGroup {

    /** The overlapping faces, a +N counter past the max, optional presence. */
    public static Element avatarGroup(Context c, String[] names, int max, String presenceFor) {
        Element host = row(c).gap(-8).label("kit-avatar-group").alignItems(Align.CENTER);
        int shown = Math.min(names.length, max <= 0 ? names.length : max);
        for (int i = 0; i < shown; i++) {
            int ii = i;
            host.children(() -> {
                Element a = Avatar.avatar(c, names[ii], 28, presenceFor != null ? presenceFor : null, false);
                a.border(2, c.theme().background());
            });
        }
        if (names.length > shown) {
            Element more = box(c).size(28, 28).radius(14).shrink(0).background(c.theme().surface())
                    .border(2, c.theme().background()).alignItems(Align.CENTER)
                    .children(() -> text(c, "+" + (names.length - shown)).singleLine().fontSize(9.5f)
                            .textColor(c.theme().textMuted()));
        }
        return host;
    }

    public static Element avatarGroup(Context c, String[] names) {
        return avatarGroup(c, names, 5, null);
    }

    private AvatarGroup() {}
}
