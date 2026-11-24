package jui.kit;

import jui.examples.KitDemo;
import jui.examples.LobeDemo;
import jui.examples.MotionGallery;
import jui.testing.Check;
import jui.ui.Color;
import jui.ui.Tester;
import jui.ui.View;

import java.util.List;

import static jui.ui.Ui.*;

/**
 * The gallery's ink must read in both looks. The strict scan reports ink
 * drawn in a color indistinguishable from what is behind it; the few
 * remaining hits are white-on-accent pairings whose rounded fills the
 * scan samples conservatively, so the ceilings below are generous but
 * catch regressions like the whole icon set going white again.
 */
public final class KitContrastTest {
    private static float[] base(Tester tt) {
        int rgb = tt.engine().theme().background().rgba() >>> 8;
        return new float[]{((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f};
    }

    private static int scan(View view, boolean dark, int width, int height) {
        Tester tt = Tester.of(view, width, height);
        tt.setDark(dark);
        tt.frame();
        tt.frame();
        tt.frame();
        return SceneContrast.scan(tt.scene(), base(tt), true).size();
    }

    private static View kitDemo() {
        try {
            var ctor = KitDemo.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ((KitDemo) ctor.newInstance())::build;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public void testNoGalleryLosesItsIconsInEitherLook() {
        List<View> galleries = List.of(
                new MotionGallery()::build,
                kitDemo(),
                new LobeDemo());
        int[] ceilings = {8, 4, 2};           // Motion, Kit, Lobe: known white-on-accent samples
        for (boolean dark : new boolean[]{false, true}) {
            for (int g = 0; g < galleries.size(); g++) {
                int n = scan(galleries.get(g), dark, 900, 2000);
                Check.isTrue(n <= ceilings[g],
                        "gallery " + g + (dark ? " dark" : " light") + " has " + n
                                + " invisible foregrounds (ceiling " + ceilings[g] + ")");
            }
        }
    }

    public void testAnIconFollowsTheColorTheCallerPicks() {
        // Draw one plus in dark ink and one in white: the glyph must put the
        // caller's color into every stroke, in both looks.
        Color dark = Color.rgb(0x101010), white = Color.rgb(0xFFFFFF);
        int darkCount = 0, whiteCount = 0, wrong = 0;
        for (boolean darkLook : new boolean[]{false, true}) {
            Tester tt = Tester.of(c -> column(c).children(() -> {
                box(c).size(16, 16).draw(Icon.draw("plus", dark));
                box(c).size(16, 16).draw(Icon.draw("plus", white));
            }), 60, 60);
            tt.setDark(darkLook);
            tt.frame();
            var scene = tt.scene();
            for (int i = 0; i < scene.count(); i++) {
                float[] d = scene.instance(i);
                if ((int) d[18] != 3) {
                    continue;                 // strokes only
                }
                boolean isDark = d[8] < 0.2f && d[9] < 0.2f && d[10] < 0.2f;
                boolean isWhite = d[8] > 0.9f && d[9] > 0.9f && d[10] > 0.9f;
                if (isDark) {
                    darkCount++;
                    if (!inBox(d, 0)) wrong++;
                } else if (isWhite) {
                    whiteCount++;
                    if (!inBox(d, 1)) wrong++;
                } else {
                    wrong++;                   // an in-between color: the icon ignored its caller
                }
            }
        }
        Check.isTrue(darkCount > 0, "a dark plus draws dark strokes");
        Check.isTrue(darkCount == whiteCount, "both colors draw the same glyph: " + darkCount + "/" + whiteCount);
        Check.isTrue(wrong == 0, "every stroke carries its caller's color, wrong=" + wrong);
    }

    /** The two pluses sit side by side: strokes 0..15 in the first box, 16.. in the second. */
    private static boolean inBox(float[] d, int which) {
        float center = d[1] + d[3] / 2;       // the two boxes stack vertically
        return which == 0 ? center < 16 : center >= 16;
    }
}
