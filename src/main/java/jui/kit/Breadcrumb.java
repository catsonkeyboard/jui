package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's breadcrumb, after the Breadcrumb: show where a page sits in
 * a hierarchy. The trail reads in the muted tone with chevron separators;
 * the current page is the only one in the text color, and the rest hover
 * to it and open on a click.
 */
public final class Breadcrumb {

    /** One crumb; the last one is the current page. */
    public record Crumb(String label, Runnable onOpen) {}

    /** The trail. */
    public static Element breadcrumb(Context c, Crumb[] crumbs) {
        Element host = row(c).gap(4).label("kit-breadcrumb").alignItems(Align.CENTER);
        host.children(() -> {
        for (int i = 0; i < crumbs.length; i++) {
            int ii = i;
            Crumb crumb = crumbs[ii];
            boolean last = ii == crumbs.length - 1;
            if (!last) {
                Element link = buttonBase(c).padding(2, 4).radius(4).label("kit-crumb");
                link.children(() -> text(c, crumb.label()).singleLine().fontSize(12)
                        .textColor(c.theme().textMuted()));
                if (link.hovered()) {
                    link.background(c.theme().surfaceHover());
                }
                if (link.clicked() && crumb.onOpen() != null) {
                    crumb.onOpen().run();
                }
                box(c).size(11, 11).shrink(0)
                        .draw(Icon.draw("chevron-right", c.theme().textMuted()));
            } else {
                Element cur = box(c).padding(2, 4).label("kit-crumb-cur");
                cur.children(() -> text(c, crumb.label()).singleLine().fontSize(12)
                        .textColor(c.theme().text()));
            }
        }
        });
        return host;
    }

    public static Element breadcrumb(Context c, String... labels) {
        Crumb[] crumbs = new Crumb[labels.length];
        for (int i = 0; i < labels.length; i++) {
            crumbs[i] = new Crumb(labels[i], null);
        }
        return breadcrumb(c, crumbs);
    }

    private Breadcrumb() {}
}
