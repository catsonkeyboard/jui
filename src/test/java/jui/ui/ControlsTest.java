package jui.ui;

import jui.testing.Check;

import static jui.ui.Ui.*;

public final class ControlsTest {
    static final Color RED = Color.rgb(0xFF0000);

    /** The instance painted exactly over the element labeled s, or fails. */
    static float[] boxOf(Tester tt, String s) {
        Rect r = tt.find(s);
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && Math.abs(in[0] - r.x()) < 0.01 && Math.abs(in[1] - r.y()) < 0.01
                    && Math.abs(in[2] - r.width()) < 0.01 && Math.abs(in[3] - r.height()) < 0.01) return in;
        }
        throw new AssertionError("no box over " + s);
    }

    static int rgba(float[] in) {
        return Math.round(in[8] * 255) << 24 | Math.round(in[9] * 255) << 16 | Math.round(in[10] * 255) << 8 | Math.round(in[11] * 255);
    }

    public void testButtonClicks() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (button(c, "保存").clicked()) n[0]++; }, 300, 200);
        tt.click("保存");
        Check.equal(1, n[0]);
    }

    public void testButtonColorsFollowHoverAndPress() {
        Theme t = Theme.light();
        Tester tt = Tester.of(c -> column(c).padding(20).gap(10).children(() -> {
            button(c, "普通").label("b");
            primaryButton(c, "主要").label("p");
        }), 300, 200);
        tt.frame();
        Check.equal(t.surface().rgba(), rgba(boxOf(tt, "b")));
        Check.equal(t.accent().rgba(), rgba(boxOf(tt, "p")));
        tt.hover("b");
        Check.equal(t.surfaceHover().rgba(), rgba(boxOf(tt, "b")));
        Rect r = tt.find("b");
        tt.press(r.x() + 5, r.y() + 5);
        Check.equal(t.surfacePressed().rgba(), rgba(boxOf(tt, "b")));
        tt.release(r.x() + 5, r.y() + 5);
        tt.hover("p");
        Check.equal(t.accentHover().rgba(), rgba(boxOf(tt, "p")));
    }

    public void testABackgroundSetLaterWinsOverHover() {
        Tester tt = Tester.of(c -> button(c, "x").label("b").background(RED), 300, 200);
        tt.hover("b");
        Check.equal(RED.rgba(), rgba(boxOf(tt, "b")));
    }

    public void testDisabledButtonHasNoHoverColor() {
        Tester tt = Tester.of(c -> button(c, "x").label("b").disabled(true), 300, 200);
        tt.hover("b");
        float[] in = boxOf(tt, "b");
        Check.equal(Theme.light().surface().rgba() & 0xFFFFFF00, rgba(in) & 0xFFFFFF00);
        Check.near(0.5, in[11], 0.01);
    }

    public void testButtonIsFocusableAndSpaceClicksIt() {
        int[] n = {0};
        Tester tt = Tester.of(c -> { if (button(c, "x").label("b").clicked()) n[0]++; }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        Check.isTrue(tt.focused("b"), "focused by tab");
        tt.key(Shortcut.of(Key.SPACE));
        Check.equal(1, n[0]);
    }

    public void testCheckboxTogglesItsRefAndReportsChanged() {
        BoolRef done = new BoolRef(false);
        int[] changes = {0};
        Tester tt = Tester.of(c -> { if (checkbox(c, done, "完成").changed()) changes[0]++; }, 300, 200);
        tt.click("完成");
        Check.isTrue(done.get() && checkMarks(tt) == 1, "checked, with its mark");
        tt.click("完成");
        Check.isTrue(!done.get() && checkMarks(tt) == 0, "unchecked");
        Check.equal(2, changes[0]);
    }

    /** The strokes the frame painted: a check box's mark is one. */
    static int checkMarks(Tester tt) {
        int n = 0;
        for (int i = 0; i < tt.instances(); i++) if (tt.scene().instance(i)[18] == 3) n++;
        return n;
    }

    public void testTheCheckMarkIsMyGOsPath() {
        BoolRef on = new BoolRef(true);
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> checkbox(c, on, "x")), 300, 200);
        tt.frame();
        float[] box = null, mark = null;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && in[2] == 16 && in[3] == 16) box = in;
            if (in[18] == 3) mark = in;
        }
        Check.isTrue(box != null && mark != null, "the box and its mark");
        float x = box[0], y = box[1];
        float[] want = {x + 3.52f, y + 8.32f, x + 6.72f, y + 11.36f, x + 12.48f, y + 4.96f};
        float[] got = {mark[4], mark[5], mark[6], mark[7], mark[12], mark[13]};
        for (int i = 0; i < 6; i++) Check.near(want[i], got[i], 0.01f);
        Check.near(1, mark[16]);                                // half the width 2
        int accentText = Theme.light().accentText().rgba();
        Check.near(((accentText >>> 24) & 0xFF) / 255f, mark[8]);
        Check.near(((accentText >>> 16) & 0xFF) / 255f, mark[9]);
        Check.near(((accentText >>> 8) & 0xFF) / 255f, mark[10]);
        Check.near((accentText & 0xFF) / 255f, mark[11]);
    }

    public void testOnChangeRunsAfterTheBuild() {
        BoolRef on = new BoolRef(false);
        java.util.List<Boolean> log = new java.util.ArrayList<>();
        Tester tt = Tester.of(c -> checkbox(c, on, "x").onChange(() -> log.add(on.get())), 300, 200);
        tt.click("x");
        Check.equal(java.util.List.of(true), log);
    }

    public void testCheckboxSpaceTogglesAndEnterYieldsToAWindowShortcut() {
        BoolRef on = new BoolRef(false);
        boolean[] windowEnter = {false};
        int[] enters = {0};
        Tester tt = Tester.of(c -> {
            if (windowEnter[0] && c.shortcut(Shortcut.of(Key.ENTER))) enters[0]++;
            checkbox(c, on, "x");
        }, 300, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.SPACE));
        Check.isTrue(on.get(), "space toggles");
        tt.key(Shortcut.of(Key.ENTER));
        Check.isTrue(!on.get(), "enter toggles when the window does not want it");
        windowEnter[0] = true; tt.frame();
        tt.key(Shortcut.of(Key.ENTER));
        Check.isTrue(!on.get() && enters[0] == 1, "the window's enter wins over the toggle");
    }

    public void testToggleSwitchesAndMovesItsKnob() {
        BoolRef on = new BoolRef(false);
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> toggle(c, on, "深色")), 300, 200);
        tt.frame();
        float offKnob = knobX(tt);
        tt.click("深色");
        Check.isTrue(on.get(), "on");
        Check.near(offKnob, knobX(tt));                              // the slide starts in this frame
        tt.advance(java.time.Duration.ofMillis(200));                // 140 ms slide
        Check.near(offKnob + 16, knobX(tt));                         // 36 - 2*2 - 16 = 16 across
    }

    /** x of the 16 by 16 white knob. */
    static float knobX(Tester tt) {
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] == 0 && in[2] == 16 && in[3] == 16 && in[8] == 1 && in[9] == 1 && in[10] == 1) return in[0];
        }
        throw new AssertionError("no knob");
    }

    public void testSliderFollowsThePointer() {
        DoubleRef v = new DoubleRef(0);
        int[] changes = {0};
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> {
            if (slider(c, v, 0, 100).width(220).label("s").changed()) changes[0]++;
        }), 400, 200);
        tt.frame();
        Rect r = tt.find("s");                     // content box: x + 10, 200 wide
        tt.press(r.x() + 10 + 50, r.y() + 10);
        Check.near(25, v.get());
        tt.move(r.x() + 10 + 230, r.y() + 10);
        Check.near(100, v.get());                  // clamped
        int before = changes[0];
        tt.move(r.x() + 10 + 260, r.y() + 10);    // still past the end: no change
        Check.equal(before, changes[0]);
        tt.release(r.x() + 10 + 260, r.y() + 10);
    }

    public void testSliderStepsWithKeys() {
        DoubleRef v = new DoubleRef(50), w = new DoubleRef(0);
        Tester tt = Tester.of(c -> column(c).children(() -> {
            slider(c, v, 0, 100).label("v");
            slider(c, w, 0, 100, 5).label("w");
        }), 400, 200);
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.RIGHT)); Check.near(51, v.get());
        tt.key(Shortcut.of(Key.PAGE_UP)); Check.near(61, v.get());
        tt.key(Shortcut.of(Key.END)); Check.near(100, v.get());
        tt.key(Shortcut.of(Key.HOME)); Check.near(0, v.get());
        tt.key(Shortcut.of(Key.TAB));
        tt.key(Shortcut.of(Key.RIGHT)); Check.near(5, w.get());
        Rect r = tt.find("w");
        tt.press(r.x() + 10 + (r.width() - 20) * 0.33f, r.y() + 10);
        Check.near(35, w.get());                   // 33 snaps to the step of 5
    }

    public void testSliderPaintsTrackFillAndKnob() {
        DoubleRef v = new DoubleRef(25);
        Theme t = Theme.light();
        Tester tt = Tester.of(c -> column(c).padding(20).children(() -> slider(c, v, 0, 100).width(220).label("s")), 400, 200);
        tt.frame();
        Rect r = tt.find("s");
        boolean track = false, fill = false, knob = false;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            if (in[18] != 0) continue;
            if (in[3] == 6 && in[2] == 200 && rgba(in) == t.border().rgba()) track = true;
            if (in[3] == 6 && Math.abs(in[2] - 50) < 0.01 && rgba(in) == t.accent().rgba()) fill = true;
            if (in[2] == 20 && in[3] == 16 && Math.abs(in[0] + 10 - (r.x() + 10 + 50)) < 0.01) knob = true;
        }
        Check.isTrue(track && fill && knob, "track " + track + ", fill " + fill + ", knob " + knob);
    }

    public void testADisabledSliderIgnoresThePointer() { // final review I1
        DoubleRef v = new DoubleRef(0);
        Tester tt = Tester.of(c -> column(c).padding(10).children(() -> slider(c, v, 0, 100).label("s").disabled(true)), 300, 200);
        Rect r = tt.find("s");
        tt.press(r.x() + r.width() - 5, r.y() + r.height() / 2);
        tt.release(r.x() + r.width() - 5, r.y() + r.height() / 2);
        Check.near(0, v.get());
    }

    public void testASliderDisabledWhileHeldStopsFollowing() { // final review I1, Review Focus 5 for sliders
        DoubleRef v = new DoubleRef(0);
        boolean[] off = {false};
        Tester tt = Tester.of(c -> column(c).padding(10).children(() ->
                slider(c, v, 0, 100).width(220).label("s").disabled(off[0])), 300, 200);
        Rect r = tt.find("s");
        float y = r.y() + r.height() / 2;
        tt.press(r.x() + 10 + 50, y);                 // 25
        off[0] = true; tt.frame(); tt.frame();
        tt.move(r.x() + 10 + 150, y);                 // would be 75
        Check.near(25, v.get());
        tt.release(r.x() + 10 + 150, y);
    }

    /** Focus rings painted: boxes 2 wide in the theme's focus ring color, with no fill. */
    static int rings(Tester tt) {
        int ring = Theme.light().focusRing().rgba(), n = 0;
        for (int i = 0; i < tt.instances(); i++) {
            float[] in = tt.scene().instance(i);
            int border = Math.round(in[12] * 255) << 24 | Math.round(in[13] * 255) << 16 | Math.round(in[14] * 255) << 8
                    | Math.round(in[15] * 255);
            if (in[18] == 0 && Math.abs(in[16] - 2) < 0.01 && border == ring) n++;
        }
        return n;
    }

    public void testFocusRingFalseHidesTheRingOnAControlsPart() { // M2 final review minor
        BoolRef check = new BoolRef(false), on = new BoolRef(false);
        DoubleRef v = new DoubleRef(50);
        boolean[] show = {true};
        Tester tt = Tester.of(c -> column(c).padding(10).gap(10).children(() -> {
            slider(c, v, 0, 100).label("s").focusRing(show[0]);
            checkbox(c, check, "c").focusRing(show[0]);
            toggle(c, on, "t").focusRing(show[0]);
        }), 300, 200);
        for (int k = 0; k < 3; k++) {
            show[0] = true;
            tt.key(Shortcut.of(Key.TAB));
            Check.equal(1, rings(tt));                    // by default each shows its ring, on its part
            show[0] = false;
            tt.frame();
            Check.equal(0, rings(tt));
        }
    }
}
