package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's sidebar: a navigable list of grouped entries with selection,
 * after gpui-kit's Sidebar. Groups and entries are plain strings; the pick
 * lands in the selected ref as "group/entry".
 */
public final class Sidebar {
    /** One entry inside a group. */
    public record Entry(String group, String label, String icon) {}

    public static Entry entry(String group, String label, String icon) {
        return new Entry(group, label, icon);
    }

    public static Element sidebar(Context c, Entry[] entries, jui.ui.TextRef selected,
                                  float width) {
        String currentGroup = null;
        return column(c).width(width).fillHeight().background(c.theme().background())
                .border(1, Kit.line(c))
                .label("kit-sidebar")
                .children(() -> {
                    scroll(c).grow(1).padding(6, 8).children(() -> {
                        String shown = null;
                        for (Entry e : entries) {
                            if (!e.group().equals(shown)) {
                                shown = e.group();
                                text(c, shown).fontSize(10.5f).bold()
                                        .textColor(c.theme().textMuted()).padding(10, 6, 3, 6);
                            }
                            String id = e.group() + "/" + e.label();
                            boolean on = selected.get().equals(id);
                            Element b = buttonBase(c).fillWidth().height(27).radius(Kit.RADIUS_SM)
                                    .label("kit-side-" + id)
                                    .padding(0, 8).gap(8).justify(Justify.START)
                                    .children(() -> {
                                        if (e.icon() != null) {
                                            box(c).size(15, 15).draw(Icon.draw(e.icon(),
                                                    on ? c.theme().accent() : c.theme().textMuted()));
                                        }
                                        text(c, e.label()).fontSize(12.5f).textColor(c.theme().text());
                                    });
                            if (on) b.background(c.theme().selection());
                            else if (b.hovered()) b.background(c.theme().surfaceHover());
                            if (b.clicked()) selected.set(id);
                        }
                    });
                });
    }

    private Sidebar() {}
}
