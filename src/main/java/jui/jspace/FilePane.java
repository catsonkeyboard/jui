package jui.jspace;

import jui.ui.Context;
import jui.ui.Menu;

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
        // A button base as the root: hovering or clicking its empty parts makes the pane active.
        jui.ui.Element root = buttonBase(c).fill().border(1.5f, active ? c.theme().accent() : p.panel())
                .radius(6).clip().background(p.panel()).label("pane-" + pane + "-frame")
                .padding(4).children(() -> {
                    column(c).fill().children(() -> {
                        new PaneTabs(ws, pane).build(c);
                        new Breadcrumbs(ws, pane).build(c);
                        renameRow(c);
                        content(c);
                    });
                });
        if (root.hovered() || root.clicked()) m.active = pane;
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
                        new IconGrid(ws, pane).build(c);
                    }
                });
        t.paneWidth = contentBox.bounds().width();              // last frame's width, 0 on the first
        if (snap != null && snap.state() == jui.jspace.store.FileStore.State.READY) {
            emptyMenu(c, contentBox, t);
        }
    }

    /** The right-click menu on the pane's empty space. */
    private void emptyMenu(Context c, jui.ui.Element target, Model.TabState t) {
        contextMenu(c, target, menu -> {
            if (menu.item("新建文件夹").chosen()) ws.newFolder(pane);
            if (menu.item("粘贴").disabled(ws.model().clip == null).chosen()) ws.paste(pane);
            menu.separator();
            menu.submenu("排序", sort -> {
                sortKey(sort, "按名称", Entries.SortKey.NAME, t);
                sortKey(sort, "按修改日期", Entries.SortKey.DATE, t);
                sortKey(sort, "按大小", Entries.SortKey.SIZE, t);
                sortKey(sort, "按种类", Entries.SortKey.KIND, t);
                sort.separator();
                if (sort.item("升序").checked(t.sort.asc).chosen()) t.sort.asc = true;
                if (sort.item("降序").checked(!t.sort.asc).chosen()) t.sort.asc = false;
            });
            menu.submenu("视图", view -> {
                if (view.item("列表").checked(t.view == Model.ViewMode.LIST).chosen()) {
                    t.view = Model.ViewMode.LIST;
                }
                if (view.item("图标").checked(t.view == Model.ViewMode.GRID).chosen()) {
                    t.view = Model.ViewMode.GRID;
                }
            });
            if (menu.item("显示隐藏文件").checked(ws.settings().showHidden).chosen()) {
                ws.settings().showHidden = !ws.settings().showHidden;
                ws.settings().save();
            }
            if (menu.item("刷新").chosen()) {
                ws.store().scan(t.dir);
                for (java.nio.file.Path e : t.expanded) ws.store().scan(e);
            }
        });
    }

    private void sortKey(Menu sort, String label, Entries.SortKey key, Model.TabState t) {
        if (sort.item(label).checked(t.sort.key == key).chosen()) {
            t.sort.key = key;
            t.sort.asc = true;
        }
    }

    private void centerNote(Context c, String s) {
        box(c).grow(1).fillWidth().children(() ->
                text(c, s).textColor(c.theme().textMuted()));
    }

    /** The one-shot inline rename field, right under the breadcrumbs. */
    private void renameRow(Context c) {
        Model.TabState t = ws.model().panes[pane].tab();
        if (t.renameNew == null) return;
        box(c).height(30).fillWidth().padding(0, 4).children(() -> {
            jui.ui.Element in = textInput(c, ws.renameRef).label("rename-input").autoFocus();
            in.setTextSelection(0, ws.renameRef.get().length());
            if (in.submitted()) ws.actions().submitRename(pane, ws.renameRef.get());
        });
    }
}
