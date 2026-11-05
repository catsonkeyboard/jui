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
    private final Sidebar sidebar;

    Workspace(Model m) { this(m, new jui.jspace.store.Settings(), Runnable::run); }

    Workspace(Model m, jui.jspace.store.Settings settings, jui.jspace.store.FileStore.Publish publish) {
        this.m = m;
        this.settings = settings;
        this.store = new jui.jspace.store.FileStore(publish);
        this.sidebar = new Sidebar(this);
    }

    Model model() { return m; }

    jui.jspace.store.Settings settings() { return settings; }

    jui.jspace.store.FileStore store() { return store; }

    /** The view this workspace builds itself as. */
    View view() { return this; }

    /** Run when a setting changed the look; the app resets its windows' theme pairs. */
    Runnable onAppearanceChange = () -> {};

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
    }

    void goBack(int pane) {
        Model.TabState t = m.panes[pane].tab();
        if (t.back.isEmpty() || t.dir == null) return;
        t.forward.push(t.dir);
        t.dir = t.back.pop();
        t.selected = null;
        store.scan(t.dir);
    }

    void goForward(int pane) {
        Model.TabState t = m.panes[pane].tab();
        if (t.forward.isEmpty() || t.dir == null) return;
        t.back.push(t.dir);
        t.dir = t.forward.pop();
        t.selected = null;
        store.scan(t.dir);
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

    /** Menu: open the settings window (Task 15 fills it in). */
    void openSettings() {}

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
            statusPlaceholder(c);
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
        row(c).height(44).fillWidth().padding(0, 12).gap(8)
                .background(pal.bar()).alignItems(jui.ui.Align.CENTER).label("toolbar").children(() -> {
            text(c, PaneTabs.title(t)).fontSize(14).bold().textColor(c.theme().text());
            spacer(c).grow(1);

            segButton(c, "view-list", t.view == Model.ViewMode.LIST, "列表",
                    () -> t.view = Model.ViewMode.LIST);
            segButton(c, "view-grid", t.view == Model.ViewMode.GRID, "图标",
                    () -> t.view = Model.ViewMode.GRID);
            box(c).width(8);
            segButton(c, "layout-single", m.layout == Model.Layout.SINGLE, "单栏",
                    () -> m.layout = Model.Layout.SINGLE);
            segButton(c, "layout-double", m.layout == Model.Layout.DOUBLE, "双栏",
                    () -> m.layout = Model.Layout.DOUBLE);
            segButton(c, "layout-grid", m.layout == Model.Layout.GRID, "四栏",
                    () -> m.layout = Model.Layout.GRID);
            box(c).width(8);

            sortPopover(c, t);
            plainButton(c, "toggle-sidebar", "边栏", this::toggleSidebar);
            plainButton(c, "open-settings", "设置", this::openSettings);

            box(c).width(140).height(28).radius(6).border(1, c.theme().border()).label("search-box")
                    .padding(0, 6).children(() -> {
                        jui.ui.Element in = textInput(c, searchRef).placeholder("搜索");
                        in.bind(searchHandle);
                        if (in.changed()) t.search = searchRef.get();
                    });
        });
    }

    private void segButton(Context c, String id, boolean on, String label, Runnable go) {
        jui.ui.Element b = buttonBase(c).height(28).padding(0, 10).radius(6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, label).fontSize(12)
                        .textColor(on ? c.theme().accent() : c.theme().textMuted()));
        if (on) b.background(c.theme().selection());
        if (b.clicked()) go.run();
    }

    private void plainButton(Context c, String id, String label, Runnable go) {
        jui.ui.Element b = buttonBase(c).height(28).padding(0, 10).radius(6).label(id)
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, label).fontSize(12).textColor(c.theme().text()));
        if (b.clicked()) go.run();
    }

    private void sortPopover(Context c, Model.TabState t) {
        jui.ui.Element anchor = buttonBase(c).height(28).padding(0, 10).radius(6).label("sort-menu")
                .alignItems(jui.ui.Align.CENTER)
                .children(() -> text(c, "排序").fontSize(12).textColor(c.theme().text()));
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
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.UP))) selectionKey(c, -1);
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.DOWN))) selectionKey(c, 1);
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.ENTER))) {
            Model.TabState t = m.panes[m.active].tab();
            if (t.selected != null) {
                for (Entries.Row r : wsEntries(t)) {
                    if (r.entry().path().equals(t.selected)) { openEntry(m.active, r.entry()); break; }
                }
            }
        }
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.F))) searchHandle.focus();
        if (c.shortcut(jui.ui.Shortcut.of(jui.ui.Key.ESCAPE))) {
            m.panes[m.active].tab().search = "";
            searchRef.set("");
        }
        if (c.shortcut(jui.ui.Shortcut.cmd(jui.ui.Key.R))) {
            Model.TabState t = m.panes[m.active].tab();
            if (t.dir != null) {
                store.scan(t.dir);
                for (java.nio.file.Path e : t.expanded) store.scan(e);
            }
        }
    }

    private void selectionKey(Context c, int step) {
        Model.TabState t = m.panes[m.active].tab();
        if (t.dir == null) return;
        List<Entries.Row> rows = wsEntries(t);
        if (rows.isEmpty()) return;
        int at = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).entry().path().equals(t.selected)) { at = i; break; }
        }
        int to = Math.max(0, Math.min(rows.size() - 1, at < 0 ? 0 : at + step));
        t.selected = rows.get(to).entry().path();
        t.listRows.scrollTo(to);
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

    private void statusPlaceholder(Context c) {
        box(c).height(24).fillWidth().background(JspaceTheme.palette(c).bar()).label("statusbar");
    }
}
