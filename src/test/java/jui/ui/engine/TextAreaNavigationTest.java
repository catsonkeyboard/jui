package jui.ui.engine;

import jui.testing.Check;
import jui.text.TextEngine;
import jui.text.TextLayout;
import jui.ui.Key;
import jui.ui.Mod;

public final class TextAreaNavigationTest {
    private final TextEngine engine = new TextEngine("zh-Hans");

    private TextEdit editor(String value, float width) {
        TextEdit edit = new TextEdit(value, true);
        edit.configureLayout(engine, 13, 400, width, 100);
        return edit;
    }

    public void testVerticalMotionRetainsGoalThroughShortLines() {
        TextEdit edit = editor("abcdef\nx\nabcdef", 200);
        edit.select(5, 5);
        edit.key(Key.DOWN, 0, 0);
        Check.equal(8, edit.caret());
        edit.key(Key.DOWN, 0, 1);
        Check.equal(14, edit.caret());
        edit.key(Key.UP, Mod.SHIFT, 2);
        Check.equal(14, edit.anchor());
        Check.equal(8, edit.caret());
    }

    public void testGeometryIsFreshBetweenEvents() {
        TextEdit edit = editor("ab", 200);
        edit.insert("\nab", 0);
        edit.key(Key.UP, 0, 1);
        edit.insert("X", 2);
        Check.equal("abX\nab", edit.text());
    }

    public void testWrapAffinityAndEndThenHome() {
        TextEdit edit = editor("abcdef ghijkl mnopqr", 45);
        TextLayout layout = edit.ensureLayout();
        int boundary = layout.lineEnd(0);
        edit.select(0, 0);
        edit.key(Key.END, 0, 0);
        Check.equal(boundary, edit.caret());
        Check.isTrue(edit.caretUpstream, "End stays on the current line");
        edit.key(Key.RIGHT, 0, 1);
        Check.equal(boundary, edit.caret());
        Check.isTrue(!edit.caretUpstream, "right crosses the soft edge");
        edit.key(Key.LEFT, 0, 2);
        Check.isTrue(edit.caretUpstream, "left crosses back");
        edit.key(Key.HOME, 0, 3);
        Check.equal(0, edit.caret());
        Check.isTrue(layout == edit.ensureLayout(), "motion does not reshape");
    }

    public void testCollapsingASelectionRetainsItsVisualEdge() {
        TextEdit edit = editor("abcdef ghijkl mnopqr", 45);
        edit.select(0, 0);
        edit.key(Key.END, 0, 0);
        int boundary = edit.caret();
        edit.key(Key.LEFT, Mod.SHIFT, 1);
        edit.key(Key.RIGHT, 0, 2);
        Check.equal(boundary, edit.caret());
        Check.isTrue(edit.caretUpstream, "right collapses to the selected upstream edge");
        edit.key(Key.RIGHT, Mod.SHIFT, 3);
        edit.key(Key.RIGHT, Mod.SHIFT, 4);
        edit.key(Key.LEFT, 0, 5);
        Check.equal(boundary, edit.caret());
        Check.isTrue(edit.caretUpstream, "left preserves the anchor's upstream edge");
    }

    public void testPageMotionAndEmacsAliasesUseVisualLines() {
        TextEdit edit = editor("abcdef\n".repeat(20), 200);
        TextLayout layout = edit.ensureLayout();
        int distance = Math.max(1, (int) (100 / engine.lineHeight(13, 400)) - 1);
        edit.select(75, 75);
        edit.key(Key.PAGE_UP, 0, 0);
        Check.equal(layout.lineStart(10 - distance) + 5, edit.caret());
        edit.key(Key.PAGE_DOWN, Mod.SHIFT, 1);
        Check.equal(75, edit.caret());
        edit.key(Key.P, Mod.CTRL, 2);
        Check.equal(68, edit.caret());
        edit.key(Key.N, Mod.CTRL, 3);
        Check.equal(75, edit.caret());
        edit.key(Key.UP, Mod.CMD | Mod.SHIFT, 4);
        Check.equal(75, edit.anchor());
        Check.equal(0, edit.caret());
        edit.key(Key.DOWN, Mod.CMD, 5);
        Check.equal(edit.text().length(), edit.caret());
    }
}
