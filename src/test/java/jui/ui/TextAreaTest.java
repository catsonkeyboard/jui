package jui.ui;

import java.time.Duration;
import jui.testing.Check;
import static jui.ui.Ui.*;

public final class TextAreaTest {
    public void testEnterBreaksTheLineAndDoesNotSubmit() {
        TextRef value = new TextRef("ab");
        int[] submits = {0};
        Tester tester = Tester.of(context -> {
            if (textArea(context, value).size(200, 100).label("area").submitted()) submits[0]++;
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.key(Shortcut.of(Key.ENTER));
        tester.type("中");
        Check.equal("ab\n中", value.get());
        Check.equal(0, submits[0]);
    }

    public void testEventsBetweenFramesUseTheNewLines() {
        TextRef value = new TextRef("ab");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.engine().textInsert("\nab", -1, 0);
        tester.engine().keyDown(Key.UP, 0, false);
        tester.engine().textInsert("X", -1, 0);
        tester.frame();
        Check.equal("abX\nab", value.get());
    }

    public void testLinesGrowAndShrinkWithoutTheStyledEightyMinimum() {
        TextRef value = new TextRef("");
        Tester tester = Tester.of(context -> textArea(context, value).width(160).lines(1, 4).label("area"), 300, 500);
        float small = tester.find("area").height();
        Check.isTrue(small < 80, "one line is allowed");
        value.set("a\n".repeat(20));
        tester.frame();
        float tall = tester.find("area").height();
        Check.isTrue(tall > small && tall < 140, "capped height");
        value.set("");
        tester.frame();
        Check.near(small, tester.find("area").height());
    }

    public void testWheelPositionSurvivesBlinkAndExternalTextChanges() {
        TextRef value = new TextRef("row\n".repeat(100));
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        Check.isTrue(tester.textScrollY("area") > 100, "starts at the end");
        tester.scroll("area", 0, -300);
        float before = tester.textScrollY("area");
        tester.advance(Duration.ofSeconds(2));
        Check.near(before, tester.textScrollY("area"));
        value.set(value.get() + "more\n");
        tester.frame();
        Check.near(before, tester.textScrollY("area"));
        tester.key(Shortcut.of(Key.DOWN));
        Check.isTrue(tester.textScrollY("area") > before, "a move reveals the caret again");
    }

    public void testAreaAtItsEndPassesWheelToOuterScroll() {
        ScrollState outer = new ScrollState();
        TextRef value = new TextRef("row\n".repeat(40));
        Tester tester = Tester.of(context -> scroll(context, outer).size(250, 180).children(() -> {
            textArea(context, value).height(100).label("area");
            box(context).height(1000);
        }), 300, 300);
        tester.scroll("area", 0, 40);
        Check.near(40, outer.y());
    }
}
