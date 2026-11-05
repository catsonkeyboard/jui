package jui.jspace;

import jui.ui.Context;
import jui.ui.View;

import java.nio.file.Path;

import static jui.ui.Ui.*;

/** The main window: toolbar, sidebar, the pane grid and the status line. */
final class Workspace implements View {
    private final Model m;
    private final jui.jspace.store.Settings settings;
    private final jui.jspace.store.FileStore store;

    Workspace(Model m) { this(m, new jui.jspace.store.Settings(), Runnable::run); }

    Workspace(Model m, jui.jspace.store.Settings settings, jui.jspace.store.FileStore.Publish publish) {
        this.m = m;
        this.settings = settings;
        this.store = new jui.jspace.store.FileStore(publish);
    }

    Model model() { return m; }

    jui.jspace.store.Settings settings() { return settings; }

    jui.jspace.store.FileStore store() { return store; }

    /** The view this workspace builds itself as. */
    View view() { return this; }

    /** Run when a setting changed the look; the app resets its windows' theme pairs. */
    Runnable onAppearanceChange = () -> {};

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

    /** Menu: show or hide the sidebar (Task 11 fills it in). */
    void toggleSidebar() {}

    /** Menu: open the settings window (Task 15 fills it in). */
    void openSettings() {}

    // Building -----------------------------------------------------------------

    @Override
    public void build(Context c) {
        column(c).fill().children(() -> {
            toolbarPlaceholder(c);
            row(c).grow(1).children(() -> {
                if (m.sidebarOpen) {
                    box(c).width(230).background(JspaceTheme.palette(c).sidebar()).label("sidebar");
                }
                paneGrid(c);
            });
            statusPlaceholder(c);
        });
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
