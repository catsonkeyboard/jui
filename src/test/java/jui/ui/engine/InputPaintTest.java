package jui.ui.engine;

import jui.testing.Check;
import jui.ui.*;

import static jui.ui.Ui.*;

public final class InputPaintTest {
    static final Theme T = Theme.light();

    static int rgba(float[] in) {
        return Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16 | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255);
    }

    /** Boxes (not glyphs or shadows) filled with color. */
    static java.util.List<float[]> boxes(Tester tt, int color) {
        java.util.List<float[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && rgba(in) == color) out.add(in);
        }
        return out;
    }

    /** Boxes whose border (instance floats 12–15, width at 16) is color. */
    static int borders(Tester tt, int color) {
        int n = 0;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            int border = Math.round(in[12] * 255) << 24 | Math.round(in[13] * 255) << 16 | Math.round(in[14] * 255) << 8
                    | Math.round(in[15] * 255);
            if (in[18] == 0 && in[16] > 0 && border == color) n++;
        }
        return n;
    }

    /** Carets: accent boxes 1 DIP wide (2 pixels at the tester's scale 2). */
    static int carets(Tester tt) {
        int n = 0;
        for (float[] in : boxes(tt, T.accent().rgba())) {
            if (Math.abs(in[2] - 1) < 0.01) n++;
        }
        return n;
    }

    public void testTheCaretIsAnAccentLineOneDeviceLineWide() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> textInput(c, q).label("in")), 300, 200);
        tt.click("in");
        tt.key(Shortcut.of(Key.END));
        Rect caret = tt.textCaret();
        java.util.List<float[]> lines = boxes(tt, T.accent().rgba());
        boolean found = false;
        for (float[] in : lines) {
            if (Math.abs(in[2] - 1) < 0.01 && Math.abs(in[0] - caret.x()) < 0.51) found = true;  // scale 2: 2 px = 1 DIP
        }
        Check.isTrue(found, "a 1-DIP accent caret at the caret's x");
    }

    public void testTheCaretBlinksAndRestsShownAfter30Seconds() {
        TextRef q = new TextRef("ab");
        Tester tt = Tester.of(c -> textInput(c, q).label("in"), 300, 200);
        tt.click("in");                                   // the blink starts over at the press, at 0
        Check.equal(1, carets(tt));
        tt.advance(java.time.Duration.ofMillis(540));     // a frame at 530 ms hid it
        Check.equal(0, carets(tt));
        tt.advance(java.time.Duration.ofMillis(530));     // and one at 1060 ms showed it
        Check.equal(1, carets(tt));
        tt.advance(java.time.Duration.ofSeconds(31));
        Check.equal(1, carets(tt));                       // at rest, shown
        Check.isTrue(tt.engine().nextWake() == Long.MAX_VALUE, "no more timers");
    }

    public void testTheSelectionShowsOnlyWithFocus() {
        TextRef q = new TextRef("hello");
        Tester tt = Tester.of(c -> column(c).children(() -> { textInput(c, q).label("in"); button(c, "别处"); }), 300, 200);
        tt.click("in");
        tt.key(Shortcut.cmd(Key.A));
        Check.equal(1, boxes(tt, T.selection().rgba()).size());
        tt.click("别处");
        Check.equal(0, boxes(tt, T.selection().rgba()).size());
    }

    public void testThePlaceholderAndTheCompositionUnderline() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> textInput(c, q).placeholder("搜索笔记").label("in"), 300, 200);
        tt.frame();
        boolean muted = false;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] != 0 && (rgba(in) & 0xFFFFFF00) == (T.textMuted().rgba() & 0xFFFFFF00)) muted = true;
        }
        Check.isTrue(muted, "the placeholder's glyphs are muted");
        tt.click("in");
        tt.compose("ni", 2);
        boolean underline = false;
        for (float[] in : boxes(tt, T.text().rgba())) {
            if (Math.abs(in[3] - 1) < 0.01 && in[2] > 2) underline = true;
        }
        Check.isTrue(underline, "a 1-DIP underline under the composition, in the text's color");
    }

    public void testBorderColorsAndNoFocusRing() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> { button(c, "前"); textInput(c, q).label("in"); }), 300, 200);
        int hover = T.border().mix(T.text(), 0.25f).rgba();
        tt.hover("in");
        Check.equal(1, borders(tt, hover));               // hovered: the border leans to the text's color
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.TAB));                     // into the input, from the keyboard
        Check.equal(0, borders(tt, T.focusRing().rgba()));
        Check.equal(1, borders(tt, T.accent().rgba()));
    }

    public void testTheInputIsOneLineHigh() {
        TextRef q = new TextRef("");
        Tester tt = Tester.of(c -> column(c).children(() -> textInput(c, q).label("in")), 300, 200);
        tt.frame();
        float line = new jui.text.TextEngine("zh-Hans").lineHeight(13, 400);
        Check.near(line + 12 + 2, tt.find("in").height(), 0.01);
        q.set("中文");
        tt.frame();
        Check.near(line + 12 + 2, tt.find("中文").height(), 0.01);
    }
}
