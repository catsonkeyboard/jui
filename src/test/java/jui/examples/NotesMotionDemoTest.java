package jui.examples;

import java.time.Duration;
import jui.testing.Check;
import jui.ui.Rect;
import jui.ui.Tester;

public final class NotesMotionDemoTest {
    public void testNarrowEditorKeepsTheEndOfTheDocumentScrollable() {
        NotesMotionDemo demo = new NotesMotionDemo();
        Tester t = Tester.of(demo::view, 760, 690);
        try {
            t.frame();
            t.advance(Duration.ofMillis(50));
            Check.isTrue(t.find("document-canvas").height() > 1550,
                    "the 43-line manuscript fits inside its scroll content at this width");
        } finally { t.close(); }
    }

    public void testAddingAnotherGroupKeepsExistingGroupsExpanded() {
        NotesMotionDemo demo = new NotesMotionDemo();
        Tester t = Tester.of(demo::view, 1180, 690);
        try {
            t.frame();
            t.click("new-group");
            t.advance(Duration.ofMillis(450));
            float expanded = t.find("group-1").height();
            t.click("new-group");
            Check.near(expanded, t.find("group-1").height());
            t.advance(Duration.ofMillis(450));
            Check.isTrue(t.find("new-group").y() > t.find("group-2").y(), "add button follows both new groups");
        } finally { t.close(); }
    }

    public void testFilterPillMovesAndExpandsWithoutJumping() {
        NotesMotionDemo demo = new NotesMotionDemo();
        Tester t = Tester.of(demo::view, 1180, 690);
        try {
            t.frame();
            Rect initial = t.find("filter-pill");
            t.click("filter-recent");
            Check.equal(2, demo.filter);
            Check.near(initial.x(), t.find("filter-pill").x());
            t.advance(Duration.ofMillis(100));
            Rect moving = t.find("filter-pill");
            Check.isTrue(moving.x() > initial.x(), "pill moves to the selected filter");
            t.advance(Duration.ofMillis(400));
            Check.near(t.find("filter-recent").x(), t.find("filter-pill").x());
            Check.near(t.find("filter-recent").width(), t.find("filter-pill").width());
            t.click("filter-inbox");
            t.advance(Duration.ofMillis(400));
            Check.near(initial.x(), t.find("filter-pill").x());
        } finally { t.close(); }
    }

    public void testCollapsingPanesReclaimsSpaceAndCanBeInterrupted() {
        NotesMotionDemo demo = new NotesMotionDemo();
        Tester t = Tester.of(demo::view, 1180, 690);
        try {
            t.frame();
            float start = t.find("editor").x();
            t.click("toggle-navigation");
            t.advance(Duration.ofMillis(90));
            float moving = t.find("editor").x();
            Check.isTrue(moving > 296 && moving < start, "editor follows the shrinking navigation");
            t.click("toggle-navigation");
            t.advance(Duration.ofMillis(450));
            Check.near(start, t.find("editor").x());
            t.click("toggle-navigation");
            t.advance(Duration.ofMillis(450));
            Check.near(296, t.find("editor").x());
            t.click("toggle-sheets");
            t.advance(Duration.ofMillis(450));
            Check.near(0, t.find("editor").x());
            t.click("toggle-navigation");
            t.advance(Duration.ofMillis(450));
            Check.isTrue(t.find("editor").width() < 1180, "navigation can reopen in focus mode");
        } finally { t.close(); }
    }

    public void testNewGroupPushesTheAddButtonAndNewSheetOpens() {
        NotesMotionDemo demo = new NotesMotionDemo();
        Tester t = Tester.of(demo::view, 1180, 690);
        try {
            t.frame();
            float before = t.find("new-group").y();
            t.click("new-group");
            t.advance(Duration.ofMillis(450));
            Check.isTrue(t.find("new-group").y() > before, "new group makes room in the list");
            Check.isTrue(t.hasText("Untitled group"), "new group is visible");
            t.click("new-sheet");
            t.advance(Duration.ofMillis(450));
            Check.equal(2, demo.sheets.size());
            Check.equal("Untitled", demo.current().title);
            Check.isTrue(t.hasText("Untitled"), "new sheet is open in the editor");
        } finally { t.close(); }
    }
}
