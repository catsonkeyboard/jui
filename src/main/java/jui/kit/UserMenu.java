package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's user menu, after the UserMenu: your account, settings, theme
 * and sign out behind the avatar. The avatar button opens a panel with the
 * account header on top and the rows under it; on a narrow window it reads
 * as a sheet from the bottom edge instead.
 */
public final class UserMenu {

    /** One row under the account header. */
    public record Item(String label, String icon, boolean destructive, Runnable onSelect) {}

    /** The account header's facts. */
    public record Account(String name, String email, String initials) {}

    /**
     * The avatar trigger and its menu. narrow tilts the panel to a bottom
     * sheet: full width, anchored at the window's bottom edge.
     */
    public static Element userMenu(Context c, Account account, Item[] items, boolean narrow,
                                   java.util.function.Consumer<Boolean> onThemeToggle) {
        Element host = row(c).label("kit-user-menu");
        BoolRef open = localBool(host, "open", false);
        Element avatar = buttonBase(c).size(32, 32).radius(16).shrink(0).label("kit-user-avatar")
                .background(Avatar.tint(c, account.name() == null ? "" : account.name()));
        avatar.children(() -> {
            boolean hot = avatar.hovered() || open.get();
            if (hot) {
                avatar.background(Avatar.tintHover(c, account.name() == null ? "" : account.name()));
            }
            text(c, account.initials() == null ? "" : account.initials()).singleLine().fontSize(12)
                    .textColor(Color.rgb(0xFFFFFF));
        });
        if (avatar.clicked()) {
            open.set(!open.get());
        }
        popover(c, avatar, open, () -> {
            Element panel = narrow
                    ? box(c).padding(8).gap(2).fillWidth()
                    : box(c).padding(6).gap(2).minWidth(230).label("kit-user-panel");
            // The account header.
            row(c).gap(10).padding(8, 6).alignItems(Align.CENTER).children(() -> {
                box(c).size(36, 36).radius(18).shrink(0)
                        .background(Avatar.tint(c, account.name() == null ? "" : account.name()))
                        .children(() -> text(c, account.initials() == null ? "" : account.initials())
                                .singleLine().fontSize(13).textColor(Color.rgb(0xFFFFFF)));
                column(c).gap(1).children(() -> {
                    text(c, account.name()).singleLine().fontSize(12.5f).textColor(c.theme().text());
                    text(c, account.email()).singleLine().fontSize(11).textColor(c.theme().textMuted());
                });
            });
            divider(c).margin(4, 2);
            for (Item it : items) {
                Element r = row(c).gap(9).padding(7, 6).radius(Kit.RADIUS_XS).fillWidth().label("kit-user-row");
                if (r.hovered()) {
                    r.background(c.theme().surfaceHover());
                }
                Color tone = it.destructive() ? KitMotion.danger(c) : c.theme().text();
                r.children(() -> {
                    box(c).size(15, 15).shrink(0).draw(Icon.draw(it.icon(), tone));
                    text(c, it.label()).singleLine().fontSize(12.5f).textColor(tone);
                });
                if (r.clicked() && it.onSelect() != null) {
                    open.set(false);
                    it.onSelect().run();
                }
            }
        });
        return host;
    }

    private UserMenu() {}
}
