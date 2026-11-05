package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileStore;
import jui.jspace.store.RelativeDate;
import jui.ui.Context;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;

import static jui.ui.Ui.*;

/** The pane's list view: sortable header, virtualized rows, tree disclosure. */
final class FileList {
    private final Workspace ws;
    private final int pane;

    FileList(Workspace ws, int pane) { this.ws = ws; this.pane = pane; }

    void build(Context c) {
        Model m = ws.model();
        Model.TabState t = m.panes[pane].tab();
        FileStore.DirSnap snap = ws.store().snap(t.dir);
        List<Entries.Row> rows = Entries.view(snap.entries(), ws.store()::snap, t.sort,
                ws.settings().foldersFirst, t.search, ws.settings().showHidden, t.expanded);
        float rowH = ws.settings().density == jui.jspace.store.Settings.Density.COMPACT ? 22 : 26;
        t.rowCount = rows.size();
        t.listRows.keys(i -> rows.get(i).entry().path().hashCode());

        column(c).fill().children(() -> {
            header(c, t);
            box(c).grow(1).fillWidth().clip().children(() ->
                    list(c, t.listRows, rows.size(), i -> listRow(c, t, rows.get(i), i, rowH)));
        });
    }

    private void header(Context c, Model.TabState t) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        row(c).height(22).fillWidth().background(pal.bar()).children(() -> {
            head(c, "名称", Entries.SortKey.NAME, "sort-name", t).grow(1);
            head(c, "修改日期", Entries.SortKey.DATE, "sort-date", t).width(112);
            head(c, "大小", Entries.SortKey.SIZE, "sort-size", t).width(72);
            head(c, "种类", Entries.SortKey.KIND, "sort-kind", t).width(104);
        });
    }

    private jui.ui.Element head(Context c, String label, Entries.SortKey key, String id, Model.TabState t) {
        boolean on = t.sort.key == key;
        jui.ui.Element b = buttonBase(c).height(22).fillWidth().padding(0, 6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> row(c).gap(4).alignItems(jui.ui.Align.CENTER).children(() -> {
                    text(c, label).fontSize(10.5f)
                            .textColor(on ? c.theme().accent() : c.theme().textMuted());
                    if (on) text(c, t.sort.asc ? "∧" : "∨").fontSize(9).textColor(c.theme().accent());
                }));
        if (b.clicked()) {
            if (on) t.sort.asc = !t.sort.asc; else { t.sort.key = key; t.sort.asc = true; }
        }
        return b;
    }

    private void listRow(Context c, Model.TabState t, Entries.Row r, int i, float rowH) {
        FileEntry e = r.entry();
        boolean sel = e.path().equals(t.selected);
        jui.ui.Element rowBtn = buttonBase(c).fillWidth().height(rowH).padding(0, 8)
                .label("row-" + e.name())
                .justify(jui.ui.Justify.CENTER)
                .children(() -> row(c).gap(4).alignItems(jui.ui.Align.CENTER).children(() -> {
                    box(c).width(r.depth() * 16);
                    if (e.dir()) disclosure(c, t, e);
                    else box(c).width(12);
                    box(c).width(16).height(16).draw(FileIcons.draw(e.icon(), false));
                    text(c, Texts.fit(c, e.name(), 12, 400,
                                    Math.max(60, t.paneWidth - r.depth() * 16 - 310)))
                            .fontSize(12).textColor(sel ? c.theme().accentText() : c.theme().text());
                    spacer(c).grow(1);
                    text(c, RelativeDate.format(e.mtimeMillis(), ZonedDateTime.now()))
                            .fontSize(11).textColor(c.theme().textMuted()).width(106);
                    box(c).width(66).alignItems(jui.ui.Align.END).children(() ->
                            text(c, size(e)).fontSize(11).textColor(c.theme().textMuted()));
                    box(c).width(98).children(() ->
                            text(c, e.kind()).fontSize(11).textColor(c.theme().textMuted()));
                }));
        if (sel) rowBtn.background(c.theme().selection());
        else if (rowBtn.hovered()) rowBtn.background(JspaceTheme.palette(c).hover());
        else if (i % 2 == 1) rowBtn.background(JspaceTheme.palette(c).zebra());
        if (rowBtn.clicked()) t.selected = e.path();
        if (rowBtn.doubleClicked()) ws.openEntry(pane, e);
        entryMenu(c, rowBtn, e);
    }

    /** The right-click menu on one entry. */
    private void entryMenu(Context c, jui.ui.Element target, FileEntry e) {
        contextMenu(c, target, menu -> {
            if (menu.item("打开").chosen()) ws.openEntry(pane, e);
            if (e.dir() && menu.item("在新标签页中打开").chosen()) {
                ws.newTab(pane);
                ws.navigate(pane, e.path());
            }
            if (menu.item("重命名").chosen()) ws.startRename(pane);
            menu.separator();
            if (menu.item("拷贝").chosen()) ws.copy(pane, false);
            if (menu.item("剪切").chosen()) ws.copy(pane, true);
            menu.separator();
            if (menu.item("删除").chosen()) ws.askDelete(pane);
        });
    }

    private void disclosure(Context c, Model.TabState t, FileEntry e) {
        boolean open = t.expanded.contains(e.path());
        jui.ui.Element d = buttonBase(c).width(12).height(rowH(t)).label("disc-" + e.name())
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, open ? "▾" : "▸").fontSize(9).textColor(c.theme().textMuted()));
        if (d.clicked()) {
            if (open) t.expanded.remove(e.path());
            else { t.expanded.add(e.path()); ws.store().scan(e.path()); }
        }
    }

    private float rowH(Model.TabState t) {
        return ws.settings().density == jui.jspace.store.Settings.Density.COMPACT ? 22 : 26;
    }

    static String size(FileEntry e) {
        if (e.dir()) return "--";
        if (e.size() < 1024) return e.size() + " 字节";
        if (e.size() < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", e.size() / 1024.0);
        if (e.size() < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", e.size() / 1048576.0);
        return String.format(Locale.ROOT, "%.1f GB", e.size() / 1073741824.0);
    }
}
