package jui.examples;

import java.time.Duration;
import jui.testing.Check;
import jui.ui.*;

/** Integrated user journeys: one catalog behind motion, search, tabs and overlays. */
public final class NotesWorkspaceTest {
    private static Tester workspace(NotesDemo d) { return Tester.of(d::view, 1180, 690); }

    public void testThreePanesFollowAppearanceWithoutLosingSelectionOrSearch() {
        NotesDemo d = new NotesDemo();
        Tester t = workspace(d);
        try {
            t.frame();
            Check.isTrue(t.find("navigation-pane").width() > 0, "navigation visible");
            Check.isTrue(t.find("notes-pane").x() > 0, "list follows navigation");
            Check.isTrue(t.find("editor").x() > t.find("notes-pane").x(), "editor is third pane");
            t.click("note-row");
            var selected = d.current();
            d.query.set("0001"); t.frame();
            float light = t.scene().instance(0)[8];
            t.setDark(true);
            Check.isTrue(light > .5 && t.scene().instance(0)[8] < .2, "workspace changes its background");
            Check.isTrue(selected == d.current(), "theme keeps selected note");
            Check.equal("0001", d.query.get());
            t.setDark(false);
            Check.near(light, t.scene().instance(0)[8]);
        } finally { t.close(); }
    }

    public void testPaneMotionCanReverseAndEditorCanRestoreBothPanes() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("note-row"); var selected = d.current();
            float start = t.find("editor").x();
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(90));
            float middle = t.find("editor").x();
            Check.isTrue(middle > 296 && middle < start, "navigation moves continuously");
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(450));
            Check.near(start, t.find("editor").x());
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(450));
            t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            Check.near(0, t.find("editor").x());
            t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(450));
            Check.near(start, t.find("editor").x());
            Check.isTrue(selected == d.current() && d.open.size() == 1, "motion preserves open tab");
        } finally { t.close(); }
    }

    public void testGroupsNewNotesPaletteAndTabsShareOneCatalog() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("note-row"); var first = d.current();
            t.click("new-group"); t.advance(Duration.ofMillis(450)); t.click("group-1");
            Check.isTrue(t.hasText("没有匹配的笔记"), "new group begins empty");
            t.click("new-sheet"); t.advance(Duration.ofMillis(450));
            var created = d.current();
            Check.equal(10001, d.notes.size());
            Check.equal("Untitled", created.title);
            Check.isTrue(d.notes.contains(created) && d.open.contains(created), "one note object across catalog and tabs");
            t.click("filter-all"); t.advance(Duration.ofMillis(400));
            d.paletteOpen.set(true); t.frame(); t.type("0002"); t.click("palette-row");
            Check.isTrue(d.current().title.contains("0002"), "palette opens a catalog note");
            Check.isTrue(d.open.contains(first) && d.open.contains(created), "existing tabs survive navigation");
            t.click("信息"); t.frame(); Check.isTrue(d.infoOpen.get(), "popover retained");
            t.key(Shortcut.of(Key.ESCAPE)); t.frame(); Check.isTrue(!d.infoOpen.get(), "escape closes popover");
        } finally { t.close(); }
    }

    public void testDividerResizesByPointerAndKeyboardAfterPaneReopens() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            Rect r = t.find("divider"); float before = d.sidebarWidth.get();
            t.press(r.x() + r.width() / 2, 300);
            t.move(r.x() + r.width() / 2 + 40, 300); t.frame();
            Check.near(before + 40, d.sidebarWidth.get());
            t.release(r.x() + r.width() / 2 + 40, 300);
            t.key(Shortcut.of(Key.RIGHT)); t.frame();
            Check.near(before + 48, d.sidebarWidth.get());
        } finally { t.close(); }
    }

    public void testNewNoteStartsBlank() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("new-sheet");
            Check.isTrue(t.hasText("空白笔记"), "new note must not inherit another note's preview");
        } finally { t.close(); }
    }

    public void testBackgroundCatalogKeepsNewNotes() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            var loaded = java.util.List.of(d.notes.get(0), d.notes.get(1));
            t.frame(); t.click("new-sheet"); var created = d.current();
            d.replaceNotes(loaded); t.frame();
            Check.equal(3, d.notes.size());
            Check.isTrue(d.notes.contains(created) && created == d.current(), "late catalog scan preserves new note identity");
        } finally { t.close(); }
    }

    public void testSearchShortcutRestoresListAndKeepsQueryWhenOverlayCloses() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            t.key(Shortcut.cmd(Key.F)); t.advance(Duration.ofMillis(450));
            Check.isTrue(t.focused("search"), "search shortcut restores and focuses shared field");
            t.type("0002"); t.frame();
            d.paletteOpen.set(true); t.frame(); t.key(Shortcut.of(Key.ESCAPE)); t.frame();
            Check.isTrue(!d.paletteOpen.get(), "escape closes palette");
            Check.equal("0002", d.query.get());
        } finally { t.close(); }
    }

    public void testNarrowWindowKeepsEditorRestoreControlsReachable() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame(); t.click("note-row");
            t.resize(480, 420); t.frame(); t.settle();
            Check.isTrue(t.find("editor").width() >= 159, "narrow layout reserves an editor toolbar");
            Rect restore = t.find("toggle-navigation");
            Check.isTrue(restore.x() >= 0 && restore.x() + restore.width() <= 480, "restore button stays inside window");
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(450));
            t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            Check.near(0, t.find("editor").x());
            t.resize(1180, 690); t.frame(); t.settle();
            t.click("toggle-navigation"); t.advance(Duration.ofMillis(450));
            t.click("toggle-sheets"); t.advance(Duration.ofMillis(450));
            Check.near(296, d.sidebarWidth.get());
            Check.isTrue(t.find("editor").width() > 500, "preferred widths recover after expanding");
        } finally { t.close(); }
    }

    public void testTenThousandCardsRemainVirtualizedAndScrollable() {
        NotesDemo d = new NotesDemo(); Tester t = workspace(d);
        try {
            t.frame();
            Check.equal(10000, d.notes.size());
            Check.isTrue(d.files.last() < 40, "only a viewport is built");
            t.scroll("notes-list", 0, 5000); t.frame();
            Check.isTrue(d.files.first() > 40, "scroll operates on the note list");
            Check.isTrue(d.files.rowsBuilt() <= d.files.last() - d.files.first() + 6, "bounded row work");
        } finally { t.close(); }
    }
}
