package jui.ui;

import jui.testing.Check;
import jui.ui.engine.Host;
import static jui.ui.Ui.*;

public final class TextAreaRegressionTest {
    public void testBaseHasNoChromeAndExternalShrinkClampsScroll() {
        TextRef value = new TextRef("");
        Tester bare = Tester.of(context -> textAreaBase(context, value).fontSize(13).width(200).lines(1, 1).label("base"), 300, 240);
        float baseHeight = bare.find("base").height();
        Check.near(bare.engine().text().lineHeight(13, 400), baseHeight);
        Check.equal(0, bare.instances());

        value.set("line\n".repeat(100));
        Tester styled = Tester.of(context -> textArea(context, value).size(220, 60).label("area"), 300, 240);
        Check.near(60, styled.find("area").height());
        Check.isTrue(styled.textScrollY("area") > 100, "explicit height can be below eighty");
        value.set("short");
        styled.frame();
        Check.near(0, styled.textScrollY("area"));
    }

    public void testCaretMotionCancelsAWheelGlideEvenWithoutChangingTheOffset() {
        TextRef value = new TextRef("line\n".repeat(100));
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.HOME));
        tester.wheel("area", 0, 200);
        tester.key(Shortcut.of(Key.RIGHT));
        float offset = tester.textScrollY("area");
        tester.advance(java.time.Duration.ofMillis(300));
        Check.near(offset, tester.textScrollY("area"));
    }

    public void testOptionChangesDuringCompositionHaveOneSideEffect() {
        for (int option = 1; option <= 3; option++) {
            int selectedOption = option;
            int[] mode = {0}, changes = {0};
            TextRef value = new TextRef("");
            Tester tester = Tester.of(context -> {
                if (textArea(context, value).size(200, 120).label("area")
                        .readOnly(mode[0] == 1).disabled(mode[0] == 2).inputMethod(mode[0] != 3).changed()) changes[0]++;
            }, 300, 240);
            Host.Memory host = new Host.Memory();
            tester.engine().setHost(host);
            tester.key(Shortcut.of(Key.TAB));
            tester.compose("ni\nhao", 6);
            int discardedBefore = host.discards;
            mode[0] = selectedOption;
            tester.frame();
            tester.settle();
            Check.equal(selectedOption == 3 ? "ni\nhao" : "", value.get());
            Check.equal(selectedOption == 3 ? 1 : 0, changes[0]);
            Check.equal(discardedBefore + 1, host.discards);
        }
    }

    public void testReadOnlySpaceScrollsOuterWhenTheAreaIsAtItsEnd() {
        TextRef value = new TextRef("line\n".repeat(40));
        ScrollState outer = new ScrollState();
        Tester tester = Tester.of(context -> scroll(context, outer).size(250, 180).children(() -> {
            textArea(context, value).height(100).readOnly(true).label("area");
            box(context).height(1000);
        }), 300, 300);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.SPACE));
        Check.isTrue(outer.y() > 0, "Space reaches the outer scroller");
    }

    public void testWheelTakesOverFromAPendingCaretReveal() {
        TextRef value = new TextRef("line\n".repeat(100));
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        float before = tester.textScrollY("area");
        Rect bounds = tester.find("area");
        tester.engine().keyDown(Key.HOME, Mod.CTRL, false);
        tester.engine().scroll(bounds.x() + 20, bounds.y() + 20, 0, -100, true, 0);
        tester.frame();
        Check.near(before - 100, tester.textScrollY("area"));
    }

    public void testScrollbarPagesWithoutPullingBackToTheCaret() {
        TextRef value = new TextRef("line\n".repeat(100));
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.HOME));
        tester.hover("area");
        Rect bounds = tester.find("area");
        float x = bounds.x() + bounds.width() - 6, y = bounds.y() + bounds.height() - 15;
        tester.press(x, y);
        tester.release(x, y);
        Check.isTrue(tester.textScrollY("area") > 50, "track pages down");
        float offset = tester.textScrollY("area");
        tester.advance(java.time.Duration.ofSeconds(1));
        Check.near(offset, tester.textScrollY("area"));
    }

    public void testRemovingLinesRestoresDefaultAndInvalidLinesFail() {
        TextRef value = new TextRef("");
        boolean[] capped = {true};
        Tester tester = Tester.of(context -> {
            Element area = textArea(context, value).width(220).label("area");
            if (capped[0]) area.lines(1, 2);
        }, 300, 240);
        Check.isTrue(tester.find("area").height() < 80, "capped one line");
        capped[0] = false;
        tester.frame();
        Check.isTrue(tester.find("area").height() >= 80, "default height restored");
        Check.fails(IllegalStateException.class, () -> Tester.of(context -> textArea(context, value).lines(0, 2), 300, 200).frame());
        Check.fails(IllegalStateException.class, () -> Tester.of(context -> textInput(context, value).lines(1, 2), 300, 200).frame());
    }

    public void testModeSwitchPreservesCommittedEventsAndDropsOldComposition() {
        TextRef value = new TextRef("a");
        boolean[] multiline = {false};
        Tester tester = Tester.of(context -> {
            Element input = multiline[0] ? textArea(context, value) : textInput(context, value);
            input.label("input");
        }, 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Key.END));
        tester.engine().textInsert("b", -1, 0);
        tester.engine().textMark("ni", 2, 0, -1, 0);
        multiline[0] = true;
        tester.frame();
        Check.equal("ab", value.get());
        Check.equal("ab", tester.shownText("input"));
        tester.key(Shortcut.of(Key.ENTER));
        Check.equal("ab\n", value.get());
    }

    public void testMarkedCrlfUsesRawRangesAndNormalizesOnlyWhenCommitted() {
        TextRef value = new TextRef("a");
        Tester tester = Tester.of(context -> textArea(context, value).size(220, 120).label("area"), 300, 240);
        tester.key(Shortcut.of(Key.TAB));
        tester.key(Shortcut.of(Mod.CTRL, Key.END));
        tester.engine().textMark("b\r\nc", 4, 0, -1, 0);
        Check.equal("ab\r\nc", tester.engine().textIn(0, 100));
        int[] range = new int[2];
        tester.engine().textSelectedRange(range);
        Check.equal(5, range[0]);
        tester.engine().textUnmark();
        tester.frame();
        Check.equal("ab\nc", value.get());
    }

    public void testAListSecondRoundDoesNotCommitAnOptionTwice() {
        TextRef value = new TextRef("");
        boolean[] ime = {true};
        int[] changes = {0};
        int[] rowHeight = {60};
        ListState rows = new ListState();
        Tester tester = Tester.of(context -> list(context, rows, 100, index -> {
            if (index == 0) {
                if (textArea(context, value).size(220, 120).inputMethod(ime[0]).label("area").changed()) changes[0]++;
            } else box(context).height(rowHeight[0]);
        }).height(240), 300, 300);
        Host.Memory host = new Host.Memory();
        tester.engine().setHost(host);
        tester.key(Shortcut.of(Key.TAB));
        tester.compose("ni", 2);
        ime[0] = false;
        rowHeight[0] = 1;
        tester.frame();
        tester.settle();
        Check.equal("ni", value.get());
        Check.equal(1, changes[0]);
        Check.equal(1, host.discards);
    }
}
