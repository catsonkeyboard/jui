package jui.examples;

import jui.testing.Check;
import jui.ui.Tester;

/** The Obsidian-like shell, as a whole: what its pieces do together. */
public final class NotesDemoTest {
    static Tester shell(NotesDemo d) {
        return Tester.of(d::view, 800, 600);
    }

    public void testTheShellSplitsAndOpensNotesInTabs() {
        NotesDemo d = new NotesDemo();
        Tester tt = shell(d);
        tt.frame();
        Check.isTrue(d.open.isEmpty(), "nothing open yet");
        tt.press(240, 300);                                    // the divider between sidebar and editor
        tt.move(280, 300);
        tt.frame();
        Check.near(280, d.sidebarWidth.get());                 // dragged
        tt.release(280, 300);
        tt.frame();
        tt.click("note-row");                                  // the first note in the list
        tt.frame();
        Check.equal(1, d.open.size());
        Check.equal(d.notes.get(0), d.current());
        tt.click("note-row");                                  // the same note again: still one tab
        tt.frame();
        Check.equal(1, d.open.size());
    }

    public void testThePaletteFindsAndOpensANote() {
        NotesDemo d = new NotesDemo();
        Tester tt = shell(d);
        tt.frame();
        d.paletteOpen.set(true);
        tt.frame();
        Check.isTrue(tt.focused("command") || !d.open.isEmpty(), "the palette's field takes the focus");
        tt.type("0002");                                       // the command filters the notes
        tt.frame();
        tt.click("palette-row");
        tt.frame();
        Check.isTrue(!d.paletteOpen.get(), "the palette closed");
        Check.equal(1, d.open.size());
        Check.isTrue(d.open.get(0).title.contains("0002"), "the note the palette found");
    }

    public void testThePopoverShowsTheOpenNoteAndOpensAnother() {
        NotesDemo d = new NotesDemo();
        Tester tt = shell(d);
        tt.frame();
        d.open(d.notes.get(0));
        tt.frame();
        tt.click("信息");
        tt.frame();
        Check.isTrue(d.infoOpen.get(), "the popover is open");
        Check.isTrue(tt.find("info") != null, "its panel shows");
        d.open(d.notes.get(1));                                // the tabs move on
        tt.frame();
        tt.key(jui.ui.Shortcut.of(jui.ui.Key.ESCAPE));
        tt.frame();
        Check.isTrue(!d.infoOpen.get(), "Escape closed the popover");
    }
}
