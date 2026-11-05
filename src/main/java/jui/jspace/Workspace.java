package jui.jspace;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.View;

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

    /** Menu: a new tab in the active pane (Task 8 fills it in). */
    void newTab() {}

    /** Menu: close the active pane's tab (Task 8 fills it in). */
    void closeTab() {}

    /** Menu: show or hide the sidebar (Task 11 fills it in). */
    void toggleSidebar() {}

    /** Menu: open the settings window (Task 15 fills it in). */
    void openSettings() {}

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
            case SINGLE -> box(c).fill().label("pane-grid")
                    .children(() -> box(c).fill().label("pane-0"));
            case DOUBLE -> split(c, m.hSplit[0],
                    () -> box(c).fill().label("pane-0"),
                    () -> box(c).fill().label("pane-1")).label("pane-grid");
            case GRID -> splitVertical(c, m.vSplit,
                    () -> split(c, m.hSplit[0], () -> box(c).fill().label("pane-0"),
                            () -> box(c).fill().label("pane-1")),
                    () -> split(c, m.hSplit[1], () -> box(c).fill().label("pane-2"),
                            () -> box(c).fill().label("pane-3"))).label("pane-grid");
        }
    }

    private void statusPlaceholder(Context c) {
        box(c).height(24).fillWidth().background(JspaceTheme.palette(c).bar()).label("statusbar");
    }
}
