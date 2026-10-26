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
        Check.isTrue(done.get() && tt.hasText("✓"), "checked, with its mark");
        tt.click("完成");
        Check.isTrue(!done.get() && !tt.hasText("✓"), "unchecked");
        Check.equal(2, changes[0]);
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
}
