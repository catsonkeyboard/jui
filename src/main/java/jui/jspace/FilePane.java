package jui.jspace;

import jui.ui.Context;

import static jui.ui.Ui.*;

/** One pane: its tab bar, its breadcrumb line and its content area. */
final class FilePane {
    private final Workspace ws;
    private final int pane;

    FilePane(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        JspaceTheme.Palette p = JspaceTheme.palette(c);
        boolean active = m.active == pane;
        box(c).fill().border(1.5f, active ? c.theme().accent() : p.panel())
                .radius(6).clip().background(p.panel()).label("pane-" + pane + "-frame")
                .padding(4).children(() -> {
                    column(c).fill().children(() -> {
                        new PaneTabs(ws, pane).build(c);
                        new Breadcrumbs(ws, pane).build(c);
                        content(c);
                    });
                });
    }

    private void content(Context c) {
        Model.TabState t = ws.model().panes[pane].tab();
        jui.jspace.store.FileStore.DirSnap snap = t.dir == null ? null : ws.store().snap(t.dir);
        jui.ui.Element contentBox = box(c).grow(1).fillWidth().background(c.theme().background())
                .label("pane-" + pane + "-content")
                .children(() -> {
                    if (snap == null || snap.state() == jui.jspace.store.FileStore.State.LOADING) {
                        centerNote(c, "正在载入…");
                    } else if (snap.state() == jui.jspace.store.FileStore.State.ERROR) {
                        column(c).grow(1).fillWidth().alignItems(jui.ui.Align.CENTER).children(() -> {
                            spacer(c);
                            text(c, snap.error() == null ? "无法打开" : snap.error())
                                    .textColor(c.theme().textMuted());
                            jui.ui.Element back = button(c, "返回主目录");
                            if (back.clicked()) ws.navigate(pane,
                                    java.nio.file.Path.of(System.getProperty("user.home")));
                            spacer(c);
                        });
                    } else if (t.view == Model.ViewMode.LIST) {
                        new FileList(ws, pane).build(c);
                    } else {
                        centerNote(c, snap.entries().length + " 项");   // the grid view lands in Task 10
                    }
                });
        t.paneWidth = contentBox.bounds().width();              // last frame's width, 0 on the first
    }

    private void centerNote(Context c, String s) {
        box(c).grow(1).fillWidth().children(() ->
                text(c, s).textColor(c.theme().textMuted()));
    }
}
