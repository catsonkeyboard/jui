package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's dropdown menu, after the DropdownMenu: a focused list of
 * actions anchored to a trigger. Items arrive with a short stagger, the
 * highlight glides, disabled rows read dimmed and stay focusable, and
 * destructive rows take the danger color. The trigger is the caller's
 * element; the menu closes on Escape, an outside press, or a pick.
 */
public final class DropdownMenu {

    /** One menu row. */
    public record Item(String label, String icon, String shortcut, boolean destructive, boolean disabled,
                       Runnable onSelect) {
        public Item(String label, String icon, Runnable onSelect) {
            this(label, icon, null, false, false, onSelect);
        }
    }

    /**
     * Anchors {@code content} under {@code trigger} while {@code open} is
     * true, as one menu panel: rows with icons, shortcuts and a
     * separator between groups marked by a null item.
     */
    public static Element menu(Context c, Element trigger, BoolRef open, Item[] items) {
        popover(c, trigger, open, () -> {
            Element panel = box(c).padding(4).gap(1).minWidth(180).label("kit-dropdown");
            int stagger = 0;
            for (Item it : items) {
                if (it == null) {
                    divider(c).margin(4, 6);
                    continue;
                }
                Item row = it;
                Element r = row(c).gap(9).padding(7, 9).radius(Kit.RADIUS_XS).fillWidth().label("kit-menu-row");
                if (row.disabled()) {
                    r.disabled(true);
                }
                boolean hot = r.hovered() && !row.disabled();
                if (hot) {
                    r.background(c.theme().surfaceHover());
                }
                float rise = Kit.spring(panel, "st" + row.label().hashCode(), 1, Kit.SNAPPY);
                r.opacity(java.lang.Math.min(1, 0.4f + 0.6f * rise));
                Color tone = row.destructive() ? Kit.danger(c) : c.theme().text();
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
                stagger++;
            }
        });
        return trigger;
    }

    /** A trigger + menu in one: a quiet button that opens the rows. */
    public static Element dropdown(Context c, String label, String icon, Item[] items) {
        Element host = row(c).label("kit-dropdown-host");
        BoolRef open = localBool(host, "open", false);
        Element b = buttonBase(c).height(30).padding(0, 10).gap(6).radius(Kit.RADIUS_XS + 2)
                .label("kit-dropdown-trigger");
        if (b.hovered() || open.get()) {
            b.background(c.theme().surfaceHover());
        }
        b.children(() -> {
            if (icon != null) {
                box(c).size(15, 15).shrink(0).draw(Icon.draw(icon, c.theme().text()));
            }
            text(c, label).singleLine().fontSize(12.5f).textColor(c.theme().text());
            IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 12);
        });
        if (b.clicked()) {
            open.set(!open.get());
        }
        menu(c, b, open, items);
        return host;
    }

    private DropdownMenu() {}
}
