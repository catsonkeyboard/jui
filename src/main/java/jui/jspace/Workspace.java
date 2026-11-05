package jui.jspace;

import jui.ui.Context;
import jui.ui.View;

import java.nio.file.Path;
import java.util.List;

import static jui.ui.Ui.*;

/** The main window: toolbar, sidebar, the pane grid and the status line. */
final class Workspace implements View {
    private final Model m;
    private final jui.jspace.store.Settings settings;
    private final jui.jspace.store.FileStore store;
    private final jui.jspace.store.FileStore.Publish publish;
    private final Sidebar sidebar;
    private final Actions actions;

    final jui.ui.TextRef renameRef = new jui.ui.TextRef("");

    Workspace(Model m) { this(m, new jui.jspace.store.Settings(), Runnable::run); }

    Workspace(Model m, jui.jspace.store.Settings settings, jui.jspace.store.FileStore.Publish publish) {
        this.m = m;
        this.settings = settings;
        this.publish = publish;
        this.store = new jui.jspace.store.FileStore(publish);
        this.sidebar = new Sidebar(this);
        this.actions = new Actions(this);
        for (Model.PaneState p : m.panes) {                     // startup (and a restored workspace) load at once
            for (Model.TabState t : p.tabs) {
                if (t.dir != null) store.scan(t.dir);
            }
        }
    }

    Model model() { return m; }

    jui.jspace.store.Settings settings() { return settings; }

    jui.jspace.store.FileStore store() { return store; }

    jui.jspace.store.FileStore.Publish opsPublish() { return publish; }

    Actions actions() { return actions; }

    /** The view this workspace builds itself as. */
    View view() { return this; }

    /** Run when a setting changed the look; the app resets its windows' theme pairs. */
    Runnable onAppearanceChange = () -> {};

    /** Run when the settings window is asked for; the app owns that window. */
    Runnable onSettings = () -> {};

    final jui.ui.TextRef searchRef = new jui.ui.TextRef("");
    final jui.ui.Handle searchHandle = new jui.ui.Handle();
    final jui.ui.BoolRef sortOpen = new jui.ui.BoolRef(false);

    /** Re-applies the appearance choice to the open windows (through onAppearanceChange). */
    void applyAppearance() {
        onAppearanceChange.run();
    }

    // Navigation and tabs -----------------------------------------------------

    /** Takes the pane's current tab to dir, remembering where it came from. */
    void navigate(int pane, Path dir) {
        if (dir == null) return;
        Model.TabState t = m.panes[pane].tab();
        if (dir.equals(t.dir)) return;
        if (t.dir != null) t.back.push(t.dir);
        t.forward.clear();
        t.dir = dir;
        t.selected = null;
        store.scan(dir);
        snapshot();
    }

    /** Keeps the restore file near the current shape, off the main thread. */
    private void snapshot() {
        if (settings.restoreWorkspace) WorkspaceState.saveAsync(m, WorkspaceState.defaultFile());
    }

    void goBack(int pane) {
        Model.TabState t = m.panes[pane].tab();
        if (t.back.isEmpty() || t.dir == null) return;
        t.forward.push(t.dir);
        t.dir = t.back.pop();
        t.selected = null;
        store.scan(t.dir);
        snapshot();
    }

    void goForward(int pane) {
        Model.TabState t = m.panes[pane].tab();
        if (t.forward.isEmpty() || t.dir == null) return;
        t.back.push(t.dir);
        t.dir = t.forward.pop();
        t.selected = null;
        store.scan(t.dir);
        snapshot();
    }

    /** A new tab in the pane, opening where the setting says. */
    void newTab(int pane) {
        Model.TabState cur = m.panes[pane].tab();
        Path home = Path.of(System.getProperty("user.home"));
        Path at = settings.newTabAt == jui.jspace.store.Settings.NewTabAt.CURRENT && cur.dir != null ? cur.dir : home;
        Model.TabState t = new Model.TabState(at);
        t.sort.key = cur.sort.key;
        t.sort.asc = cur.sort.asc;
        m.panes[pane].tabs.add(t);
        m.panes[pane].current = m.panes[pane].tabs.size() - 1;
        store.scan(at);
        snapshot();
    }

    /** Closes the pane's tab; the last one reopens at home instead of leaving none. */
    void closeTab(int pane) {
        Model.PaneState p = m.panes[pane];
        if (p.tabs.size() <= 1) {
            p.tab().dir = Path.of(System.getProperty("user.home"));
            p.tab().back.clear();
            p.tab().forward.clear();
            store.scan(p.tab().dir);
            return;
        }
        p.tabs.remove(p.current);
        if (p.current >= p.tabs.size()) p.current = p.tabs.size() - 1;
        snapshot();
    }

    /** Menu: a new tab in the active pane. */
    void newTab() { newTab(m.active); }

    /** Menu: close the active pane's tab. */
    void closeTab() { closeTab(m.active); }

    /** Opens one entry: into it when a folder, with /usr/bin/open when not. */
    void openEntry(int pane, jui.jspace.store.FileEntry e) {
        if (e.dir()) {
            navigate(pane, e.path());
        } else {
            try {
                new ProcessBuilder("/usr/bin/open", e.path().toString()).start();
            } catch (java.io.IOException ex) {
                m.status.note = "无法打开 " + e.name();
                m.status.noteAt = System.nanoTime();
            }
        }
    }

    /** Menu: show or hide the sidebar. */
    void toggleSidebar() { m.sidebarOpen = !m.sidebarOpen; }

    // The operations the menus and keys drive ------------------------------------

    void newFolder(int pane) { actions.newFolder(pane); }

    void askDelete(int pane) { actions.askDelete(pane); }

    void copy(int pane, boolean cut) { actions.copy(pane, cut); }

    void paste(int pane) { actions.paste(pane); }

    void startRename(int pane) { actions.startRename(pane); }

    /** Menu: open the settings window (the app owns it, through onSettings). */
    void openSettings() { onSettings.run(); }

    // Building -----------------------------------------------------------------

    @Override
    public void build(Context c) {
        syncSearch();
        keys(c);
        column(c).fill().children(() -> {
            toolbar(c);
            row(c).grow(1).children(() -> {
                if (m.sidebarOpen) {
                    box(c).width(230).fillHeight().children(() -> sidebar.build(c));
                }
                paneGrid(c);
            });
            statusbar(c);
            deleteModal(c);
            clashModal(c);
        });
    }

    private void statusbar(Context c) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        Model.TabState t = m.panes[m.active].tab();
        if (!m.status.note.isEmpty() && c.now() - m.status.noteAt > 5_000_000_000L) {
            m.status.note = "";
        }
        row(c).height(22).fillWidth().padding(0, 12).background(pal.bar())
                .alignItems(jui.ui.Align.CENTER).label("statusbar").children(() -> {
            String left = t.rowCount + " 项";
            if (t.selected != null && t.selected.getFileName() != null) {
                left += " · 已选 " + t.selected.getFileName();
            }
            text(c, left).fontSize(10.5f).textColor(c.theme().textMuted());
            spacer(c).grow(1);
            if (!m.status.note.isEmpty()) {
                text(c, m.status.note).fontSize(10.5f).textColor(pal.warn());
            }
        });
    }

    private void deleteModal(Context c) {
        modal(c, actions.deleteOpen, () -> {
            Path p = actions.pendingDelete();
            String name = p == null ? "" : p.getFileName().toString();
            column(c).width(320).padding(18, 20).gap(12)
                    .background(c.theme().surface()).radius(10)
                    .children(() -> {
                        text(c, "确认删除「" + name + "」？").fontSize(14).bold().textColor(c.theme().text());
                        if (actions.pendingCount() > 1) {
                            text(c, "其中包含 " + actions.pendingCount() + " 项")
                                    .fontSize(12).textColor(c.theme().textMuted());
                        }
                        row(c).gap(8).children(() -> {
                            jui.ui.Element ok = primaryButton(c, "删除").label("confirm-delete-ok");
                            if (ok.clicked()) actions.doDelete();
                            jui.ui.Element no = button(c, "取消").label("confirm-delete-cancel");
                            if (no.clicked()) actions.cancelDelete();
                        });
                    });
        });
    }

    private void clashModal(Context c) {
        modal(c, actions.clashOpen, () -> {
            column(c).width(320).padding(18, 20).gap(12)
                    .background(c.theme().surface()).radius(10)
                    .children(() -> {
                        text(c, "「" + actions.clashName() + "」已存在").fontSize(14).bold()
                                .textColor(c.theme().text());
                        row(c).gap(8).children(() -> {
                            jui.ui.Element over = primaryButton(c, "覆盖").label("confirm-overwrite-overwrite");
                            if (over.clicked()) actions.decideClash(jui.jspace.store.FileOps.Decision.OVERWRITE);
                            jui.ui.Element skip = button(c, "跳过").label("confirm-overwrite-skip");
                            if (skip.clicked()) actions.decideClash(jui.jspace.store.FileOps.Decision.SKIP);
                            jui.ui.Element cancel = button(c, "取消").label("confirm-overwrite-cancel");
                            if (cancel.clicked()) actions.decideClash(jui.jspace.store.FileOps.Decision.CANCEL);
                        });
                    });
        });
    }

    /** The search box shows the active tab's word unless it is being typed in. */
    private void syncSearch() {
        Model.TabState t = m.panes[m.active].tab();
        if (!searchHandle.focused() && !searchRef.get().equals(t.search)) {
            searchRef.set(t.search);
        }
    }

    private void toolbar(Context c) {
        JspaceTheme.Palette pal = JspaceTheme.palette(c);
        Model.TabState t = m.panes[m.active].tab();
        row(c).height(40).fillWidth().padding(0, 10).gap(6)
                .background(pal.bar()).alignItems(jui.ui.Align.CENTER).label("toolbar").children(() -> {
            nav(c, "tb-back", !t.back.isEmpty(), "‹", () -> goBack(m.active));
            nav(c, "tb-forward", !t.forward.isEmpty(), "›", () -> goForward(m.active));
            text(c, PaneTabs.title(t)).fontSize(13).bold().textColor(c.theme().text());
            spacer(c).grow(1);

            segButton(c, "view-list", t.view == Model.ViewMode.LIST, "列表",
                    () -> t.view = Model.ViewMode.LIST);
            segButton(c, "view-grid", t.view == Model.ViewMode.GRID, "图标",
                    () -> t.view = Model.ViewMode.GRID);
            box(c).width(10);
            segButton(c, "layout-single", m.layout == Model.Layout.SINGLE, "1×1",
                    () -> m.layout = Model.Layout.SINGLE);
            segButton(c, "layout-double", m.layout == Model.Layout.DOUBLE, "1×2",
                    () -> m.layout = Model.Layout.DOUBLE);
            segButton(c, "layout-grid", m.layout == Model.Layout.GRID, "2×2",
                    () -> m.layout = Model.Layout.GRID);
            box(c).width(10);

            sortPopover(c, t);
            plainButton(c, "toggle-sidebar", "边栏", this::toggleSidebar);
            plainButton(c, "open-settings", "设置", this::openSettings);

            box(c).width(150).height(26).radius(6).border(1, c.theme().border()).label("search-box")
                    .padding(0, 6).children(() -> {
                        jui.ui.Element in = textInput(c, searchRef).placeholder("搜索");
                        in.bind(searchHandle);
                        if (in.changed()) t.search = searchRef.get();
                    });
        });
    }

    /** A small square arrow button, as a pane's ‹ › at the toolbar's left. */
    private void nav(Context c, String id, boolean enabled, String glyph, Runnable go) {
        jui.ui.Element b = buttonBase(c).width(26).height(26).radius(6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, glyph).fontSize(15)
                        .textColor(enabled ? c.theme().text() : c.theme().border()));
        if (enabled && b.hovered()) b.background(JspaceTheme.palette(c).hover());
        if (enabled && b.clicked()) go.run();
    }

    private void segButton(Context c, String id, boolean on, String label, Runnable go) {
        jui.ui.Element b = buttonBase(c).height(26).padding(0, 9).radius(6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, label).fontSize(12)
                        .textColor(on ? c.theme().accent() : c.theme().textMuted()));
        if (on) b.background(c.theme().selection());
        else if (b.hovered()) b.background(JspaceTheme.palette(c).hover());
        if (b.clicked()) {
            go.run();
            snapshot();
        }
    }

    private void plainButton(Context c, String id, String label, Runnable go) {
        jui.ui.Element b = buttonBase(c).height(26).padding(0, 9).radius(6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, label).fontSize(12).textColor(c.theme().textMuted()));
        if (b.hovered()) b.background(JspaceTheme.palette(c).hover());
        if (b.clicked()) go.run();
    }

    private void sortPopover(Context c, Model.TabState t) {
        jui.ui.Element anchor = buttonBase(c).height(26).padding(0, 9).radius(6).label("sort-menu")
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, "排序").fontSize(12).textColor(c.theme().textMuted()));
        if (anchor.hovered()) anchor.background(JspaceTheme.palette(c).hover());
        popover(c, anchor, sortOpen, () -> {
            column(c).padding(6, 4).gap(2).children(() -> {
                sortItem(c, "按名称", Entries.SortKey.NAME, t);
                sortItem(c, "按修改日期", Entries.SortKey.DATE, t);
                sortItem(c, "按大小", Entries.SortKey.SIZE, t);
                sortItem(c, "按种类", Entries.SortKey.KIND, t);
                jui.ui.Element asc = buttonBase(c).height(24).padding(0, 8).radius(4)
                        .children(() -> text(c, t.sort.asc ? "升序 ✓" : "升序").fontSize(12)
                                .textColor(c.theme().text()));
                if (asc.clicked()) { t.sort.asc = true; sortOpen.set(false); }
                jui.ui.Element desc = buttonBase(c).height(24).padding(0, 8).radius(4)
                        .children(() -> text(c, !t.sort.asc ? "降序 ✓" : "降序").fontSize(12)
                                .textColor(c.theme().text()));
                if (desc.clicked()) { t.sort.asc = false; sortOpen.set(false); }
            });
        });
    }

    private void sortItem(Context c, String label, Entries.SortKey key, Model.TabState t) {
        boolean on = t.sort.key == key;
        jui.ui.Element b = buttonBase(c).height(24).padding(0, 8).radius(4)
                .children(() -> text(c, on ? label + " ✓" : label).fontSize(12).textColor(c.theme().text()));
        if (b.clicked()) { t.sort.key = key; t.sort.asc = true; sortOpen.set(false); }
    }

    /** Window-level keys that act on the active pane's list: arrows walk, Enter opens. */
    private void keys(Context c) {
        for (jui.ui.Key k : new jui.ui.Key[] { jui.ui.Key.UP, jui.ui.Key.DOWN, jui.ui.Key.LEFT, jui.ui.Key.RIGHT }) {
            if (c.shortcut(jui.ui.Shortcut.of(k))) arrowKey(k);
        }
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.ENTER))) {
            Model.TabState t = m.panes[m.active].tab();
            if (t.selected != null) {
                for (Entries.Row r : wsEntries(t)) {
                    if (r.entry().path().equals(t.selected)) { openEntry(m.active, r.entry()); break; }
                }
            }
        }
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.F))) searchHandle.focus();
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.C))) copy(m.active, false);
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.X))) copy(m.active, true);
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.V))) paste(m.active);
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.BACKSPACE))) askDelete(m.active);
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.ESCAPE))) {
            Model.TabState t = m.panes[m.active].tab();
            if (t.renameNew != null) t.renameNew = null;         // a rename in progress closes first
            else { t.search = ""; searchRef.set(""); }
        }
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.R))) {
            Model.TabState t = m.panes[m.active].tab();
            if (t.dir != null) {
                store.scan(t.dir);
                for (java.nio.file.Path e : t.expanded) store.scan(e);
            }
        }
    }

    /** Moves the selection in the active pane: a line at a time in the list, a cell in the grid;
     *  → expands or enters a folder, ← collapses one or jumps to its parent. */
    private void arrowKey(jui.ui.Key k) {
        Model.TabState t = m.panes[m.active].tab();
        if (t.dir == null) return;
        List<Entries.Row> rows = wsEntries(t);
        if (rows.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).entry().path().equals(t.selected)) { at = i; break; }
        }
        if (t.view == Model.ViewMode.LIST) {
            switch (k) {
                case UP, DOWN -> {
                    int step = k == jui.ui.Key.DOWN ? 1 : -1;
                    int to = Math.max(0, Math.min(rows.size() - 1, at < 0 ? 0 : at + step));
                    t.selected = rows.get(to).entry().path();
                    t.listRows.scrollTo(to);
                }
                case RIGHT -> {
                    if (at < 0) { t.selected = rows.get(0).entry().path(); return; }
                    jui.jspace.store.FileEntry e = rows.get(at).entry();
                    if (!e.dir()) return;
                    if (!t.expanded.contains(e.path())) {
                        t.expanded.add(e.path());
                        store.scan(e.path());
                    } else {
                        navigate(m.active, e.path());
                    }
                }
                case LEFT -> {
                    if (at < 0) { t.selected = rows.get(0).entry().path(); return; }
                    Entries.Row r = rows.get(at);
                    jui.jspace.store.FileEntry e = r.entry();
                    if (e.dir() && t.expanded.contains(e.path())) {
                        t.expanded.remove(e.path());
                    } else if (r.depth() > 0) {
                        Path parent = e.path().getParent();     // jump to the folder's own row
                        for (Entries.Row q : rows) {
                            if (q.entry().path().equals(parent)) t.selected = parent;
                        }
                    } else {
                        Path home = Path.of(System.getProperty("user.home"));
                        if (!t.dir.equals(home) && t.dir.getParent() != null) {
                            navigate(m.active, t.dir.getParent());
                        }
                    }
                }
                default -> { }
            }
        } else {                                                 // the grid: a cell per press, a row on up/down
            int perRow = Math.max(1, (int) (t.paneWidth / 96));
            int step = switch (k) {
                case DOWN -> perRow;
                case UP -> -perRow;
                case RIGHT -> 1;
                default -> -1;
            };
            int to = Math.max(0, Math.min(rows.size() - 1, at < 0 ? 0 : at + step));
            t.selected = rows.get(to).entry().path();
            t.gridRows.scrollTo(to / Math.max(1, perRow));
        }
    }

    private List<Entries.Row> wsEntries(Model.TabState t) {
        return Entries.view(store.snap(t.dir).entries(), store::snap, t.sort,
                settings.foldersFirst, t.search, settings.showHidden, t.expanded);
    }

    private void toolbarPlaceholder(Context c) {
        box(c).height(44).fillWidth().background(JspaceTheme.palette(c).bar()).label("toolbar");
    }

    private void paneGrid(Context c) {
        switch (m.layout) {
            case SINGLE -> box(c).fill().label("pane-grid").children(() -> pane(c, 0));
            case DOUBLE -> split(c, m.hSplit[0], () -> pane(c, 0), () -> pane(c, 1)).label("pane-grid");
            case GRID -> splitVertical(c, m.vSplit,
                    () -> split(c, m.hSplit[0], () -> pane(c, 0), () -> pane(c, 1)),
                    () -> split(c, m.hSplit[1], () -> pane(c, 2), () -> pane(c, 3))).label("pane-grid");
        }
    }

    private void pane(Context c, int i) {
        new FilePane(this, i).build(c);
    }
}
