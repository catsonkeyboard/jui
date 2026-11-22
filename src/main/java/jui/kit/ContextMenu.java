package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's context menu, after the ContextMenu: secondary actions kept
 * close to the selected object. A right click on the target opens the same
 * rows the dropdown shows, anchored to the target; the actions match the
 * object, so the caller rebuilds them per frame.
 */
public final class ContextMenu {

    /** One row; null draws a separator. */
    public record Item(String label, String icon, String shortcut, boolean destructive, boolean disabled,
                       Runnable onSelect) {
        public Item(String label, String icon, Runnable onSelect) {
            this(label, icon, null, false, false, onSelect);
        }
    }

    /**
     * Wires a right click menu onto the target: on rightClicked the rows
     * open in a panel anchored to it, closing on a pick, Escape, or an
     * outside press. Build this as a sibling of the target, right after it.
     */
    public static Element attach(Context c, Element target, Item[] items) {
        Element host = box(c).invisible();
        BoolRef open = localBool(host, "open", false);
        if (target.rightClicked()) {
            open.set(true);
            c.invalidate();
        }
        if (!open.get()) {
            return host;
        }
        popover(c, target, open, () -> {
            Element panel = box(c).padding(4).gap(1).minWidth(170).label("kit-context");
            for (Item it : items) {
                if (it == null) {
                    divider(c).margin(4, 6);
                    continue;
                }
                Item row = it;
                Element r = row(c).gap(9).padding(7, 9).radius(Kit.RADIUS_XS).fillWidth()
                        .label("kit-ctx-row");
                if (row.disabled()) {
                    r.disabled(true);
                }
                if (r.hovered() && !row.disabled()) {
                    r.background(c.theme().surfaceHover());
                }
                Color tone = row.destructive() ? KitMotion.danger(c) : c.theme().text();
                r.children(() -> {
                    if (row.icon() != null) {
                        box(c).size(15, 15).shrink(0).draw(Icon.draw(row.icon(),
                                row.disabled() ? c.theme().textMuted() : tone));
                    }
                    text(c, row.label()).singleLine().fontSize(12.5f)
                            .textColor(row.disabled() ? c.theme().textMuted() : tone);
                    spacer(c).grow(1);
                    if (row.shortcut() != null) {
                        text(c, row.shortcut()).singleLine().fontSize(11)
                                .textColor(c.theme().textMuted());
                    }
                });
                if (!row.disabled() && r.clicked() && row.onSelect() != null) {
                    open.set(false);
                    row.onSelect().run();
                }
            }
        });
        return host;
    }

    private ContextMenu() {}
}
