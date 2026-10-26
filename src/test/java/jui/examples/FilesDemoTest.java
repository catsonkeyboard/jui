package jui.examples;

import jui.testing.Check;
import jui.ui.Tester;

public final class FilesDemoTest {
    public void testTheListShowsTenThousandNotesAndSelectsOnClick() {
        FilesDemo s = new FilesDemo();
        Tester tt = Tester.of(s::view, 360, 640);
        Check.isTrue(tt.hasText("10000 篇笔记"), "the count");
        String second = s.notes.get(1).title;
        tt.click(second);
        Check.equal(s.notes.get(1), s.selected);
    }

    public void testScrollingToTheEndShowsTheLastNoteAndBackToTop() {
        FilesDemo s = new FilesDemo();
        Tester tt = Tester.of(s::view, 360, 640);
        s.files.scrollTo(9_999);
        tt.frame();
        Check.isTrue(tt.hasText(s.notes.get(9_999).title), "the last note");
        tt.click("回到顶部");
        Check.equal(0, s.files.first());
    }
}
