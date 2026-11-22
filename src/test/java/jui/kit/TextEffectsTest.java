package jui.kit;

import jui.testing.Check;
import jui.ui.Tester;

import static jui.ui.Ui.*;

public final class TextEffectsTest {

    public void testBandAlphaIsABellCenteredOnTheSweep() {
        float width = 200;
        // The band's center crossing x=100: full brightness there.
        Check.isTrue(TextEffects.bandAlpha(0.5, width, 100) >= 0.9f,
                "center hit reads bright: " + TextEffects.bandAlpha(0.5, width, 100));
        // Far from the band: nothing.
        Check.isTrue(TextEffects.bandAlpha(0.5, width, 0) == 0f, "nothing far left");
        Check.isTrue(TextEffects.bandAlpha(0.5, width, 200) == 0f, "nothing far right");
        // Symmetric around the center.
        float left = TextEffects.bandAlpha(0.5, width, 92);
        float right = TextEffects.bandAlpha(0.5, width, 108);
        Check.isTrue(Math.abs(left - right) < 0.02f, "bell is symmetric: " + left + " / " + right);
        // Monotonic rise into the center.
        Check.isTrue(TextEffects.bandAlpha(0.5, width, 96) < TextEffects.bandAlpha(0.5, width, 100),
                "rises toward the center");
        // Never overdrives.
        Check.isTrue(TextEffects.bandAlpha(0.5, width, 100) <= 1.001f, "capped");
    }

    public void testBandAlphaPausesOffscreenBetweenPasses() {
        float width = 200;
        // Phase past 1 is the inter-pass pause: the band has swept past the end.
        Check.isTrue(TextEffects.bandAlpha(1.0, width, 200) == 0f, "pause: band past the end");
        Check.isTrue(TextEffects.bandAlpha(0.95, width, 100) == 0f, "pause: middle dark");
    }

    public void testRunningShimmerDrawsTheMaskedCopies() {
        int[] idle = new int[1], running = new int[1];
        Tester off = Tester.of(c -> TextEffects.textShimmer(c, "正在思考中", 13, false), 160, 40);
        off.frame();
        idle[0] = off.instances();
        Tester on = Tester.of(c -> TextEffects.textShimmer(c, "正在思考中", 13, true), 160, 40);
        on.frame();
        running[0] = on.instances();
        Check.isTrue(running[0] > idle[0], "the sweep draws bright glyph copies over the muted base: "
                + running[0] + " > " + idle[0]);
    }

    public void testReducedMotionShimmerIsStatic() {
        int[] idle = new int[1], running = new int[1];
        Tester off = Tester.of(c -> TextEffects.textShimmer(c, "正在思考中", 13, false), 160, 40);
        off.frame();
        idle[0] = off.instances();
        KitMotion.reducedMotion = true;
        try {
            Tester on = Tester.of(c -> TextEffects.textShimmer(c, "正在思考中", 13, true), 160, 40);
            on.frame();
            running[0] = on.instances();
        } finally {
            KitMotion.reducedMotion = false;
        }
        Check.isTrue(idle[0] == running[0], "reduced motion sweeps nothing: " + idle[0] + " vs " + running[0]);
    }
}
