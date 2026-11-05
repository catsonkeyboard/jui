package jui.jspace;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;

import java.nio.file.Path;

import static jui.ui.Ui.*;

/** One pane's tab bar: pill tabs and the + button. */
final class PaneTabs {
    private final Workspace ws;
    private final int pane;

    PaneTabs(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model.PaneState p = ws.model().panes[pane];
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).gap(4).padding(2, 2, 0, 2).children(() -> {
            for (int i = 0; i < p.tabs.size(); i++) {
                pill(c, p, i, pal);
            }
            Element plus = buttonBase(c).width(24).height(24).radius(6).label("new-tab")
                    .alignItems(Align.CENTER)
                    .children(() -> text(c, "+").fontSize(13).textColor(c.theme().textMuted()));
            if (plus.clicked()) ws.newTab(pane);
        });
    }

    private void pill(Context c, Model.PaneState p, int i, JspaceTheme.Palette pal) {
        boolean sel = i == p.current;
        Model.TabState t = p.tabs.get(i);
        Element pill = buttonBase(c).height(26).padding(0, 10).radius(6)
                .background(sel ? pal.pillSel() : pal.pill()).label("tab-" + i)
                .alignItems(Align.CENTER)
                .children(() -> row(c).gap(6).alignItems(Align.CENTER).children(() -> {
                    text(c, title(t)).fontSize(12)
                            .textColor(sel ? c.theme().text() : c.theme().textMuted());
                    if (sel && p.tabs.size() > 1) {
                        Element x = buttonBase(c).width(16).height(16).label("tab-" + i + "-close")
                                .alignItems(Align.CENTER)
                                .children(() -> text(c, "✕").fontSize(10).textColor(c.theme().textMuted()));
                        if (x.clicked()) ws.closeTab(pane);
                    }
                }));
        if (pill.clicked()) p.current = i;
    }

    static String title(Model.TabState t) {
        if (t.dir == null) return "主目录";
        Path h = Path.of(System.getProperty("user.home"));
        if (t.dir.equals(h)) return "主目录";
        return t.dir.getFileName() == null ? "/" : t.dir.getFileName().toString();
    }
}
