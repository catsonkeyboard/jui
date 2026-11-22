package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's text effects, after the Text reveal, In-view title, Text
 * morph, Text shimmer and Slot text. One file holds the five faces: a
 * line that rises in word by word, a title that follows the view, a label
 * that morphs to its next word, a calm light across the words while work
 * runs, and numbers that spin in on slot reels.
 */
public final class TextEffects {

    /**
     * The text reveal: words rise in on a stagger. The caller re-shows by
     * changing the text; the run restarts from the new words.
     */
    public static Element textReveal(Context c, String text, float fontSize, boolean show) {
        Element host = row(c).gap(fontSize * 0.28f).wrap().alignContent(Align.START)
                .label("kit-text-reveal");
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++) {
            int ii = i;
            host.children(() -> {
                float t = Kit.spring(host, "w" + ii, show ? 1 : 0, Kit.MORPH);
                Element w = text(c, words[ii]).singleLine().fontSize(fontSize)
                        .textColor(c.theme().text());
                w.opacity(t);
                w.top((1 - t) * fontSize * 0.4f);
            });
        }
        return host;
    }

    /**
     * The in-view title: the title arrives as it scrolls into view. The
     * engine has no viewport signal, so the caller tells it with a ref;
     * the run is the reveal's, at display weight.
     */
    public static Element inViewTitle(Context c, String text, float fontSize, Ref<Boolean> inView) {
        return textReveal(c, text, fontSize, inView != null && inView.get());
    }

    /**
     * The text morph: a label that morphs into its next word. Each letter
     * carries its own rise on the morph run — change the text and the new
     * letters land from a short fall while the width springs to fit — and
     * the whole stays on one baseline.
     */
    public static Element textMorph(Context c, String text, float fontSize) {
        Element host = box(c).clip().label("kit-text-morph");
        float w = c.layoutText(text, fontSize, 600, 4096).width;
        float animW = Kit.spring(host, "w", w, Kit.MORPH);
        host.width(animW).height(fontSize * 1.35f);
        for (int i = 0; i < text.length(); i++) {
            int ii = i;
            char ch = text.charAt(ii);
            host.children(() -> {
                float t = Kit.spring(host, "ch" + ii, 1, Kit.MORPH);
                Element l = text(c, String.valueOf(ch)).singleLine().fontSize(fontSize)
                        .fontWeight(600).textColor(c.theme().text());
                l.opacity(t);
                l.top((1 - t) * fontSize * 0.35f);
            });
        }
        return host;
    }

    // The sweep: the band crosses the box in 1.4 s and rests the rest of the
    // 2 s cycle, with a pause between passes.
    private static final long PASS_MS = 1_400, CYCLE_MS = 2_000;
    // Stepped bands that read as one soft bell edge over the glyphs: widest
    // and faintest first, the bright core last. Each is a plain clip over a
    // second bright draw of the same glyphs, so only glyph pixels lighten —
    // the framework's per-instance clip rect is the text mask.
    private static final float[] BAND_HALF = {3.2f, 2.2f, 1.5f, 0.8f};
    private static final float[] BAND_ALPHA = {0.14f, 0.26f, 0.45f, 0.75f};

    /**
     * The summed band brightness at x for a sweep phase, pure for tests: a
     * stepped bell centered on the band, zero outside the box, and zero for
     * any phase past 1 — the inter-pass pause.
     */
    static float bandAlpha(double phase, float width, float x) {
        float p = (float) Math.min(phase, 1.0);
        float half = (float) (width * 0.12 + 6);
        float center = -half + (width + 2 * half) * p;
        float d = Math.abs(x - center);
        float over = 1f;                                     // the layers composite, not add:
        for (int i = 0; i < BAND_HALF.length; i++) {         // 1 - Π(1 - a), always <= 1
            if (d <= BAND_HALF[i]) {
                over *= 1 - BAND_ALPHA[i];
            }
        }
        return 1 - over;
    }

    /**
     * The text shimmer: show ongoing work with a calm light across the
     * words — after the background-clip sweep, drawn here as the same
     * glyphs in the text color under a moving clip band over a muted base,
     * so only glyph pixels lighten and the gaps between them stay dark.
     * One short line of status text; the sweep asks for frames while it
     * runs and reduced motion shows plain steady text.
     */
    public static Element textShimmer(Context c, String text, float fontSize, boolean running) {
        jui.text.TextLayout layout = c.layoutText(text, fontSize, 500,
                Math.max(40, c.windowWidth() - 80));
        Element host = box(c).width(Math.max(1, layout.width))
                .height(Math.max(fontSize, layout.height)).clip()
                .label("kit-text-shimmer");
        if (!running || Kit.reducedMotion) {
            host.draw((p, b) -> p.text(layout, b.x(), b.y(),
                    running ? c.theme().text() : c.theme().textMuted()));
            return host;
        }
        long t0 = c.now();
        Color bright = c.theme().text();
        host.draw((p, b) -> {
            double phase = ((c.now() - t0) % CYCLE_MS) / (double) PASS_MS;
            float half = (float) (b.width() * 0.12 + 6);
            float center = b.x() - half + (float) ((b.width() + 2 * half)
                    * Math.min(phase, 1.0));
            p.text(layout, b.x(), b.y(), c.theme().textMuted());
            for (int i = 0; i < BAND_HALF.length; i++) {
                float bh = half * BAND_HALF[i] / 3.2f;
                p.clip(center - bh, b.y(), bh * 2, b.height());
                p.text(layout, b.x(), b.y(),
                        Color.rgba(bright.rgba() >>> 8, BAND_ALPHA[i]));
                p.popClip();
            }
            c.after(java.time.Duration.ofMillis(32));
        });
        return host;
    }

    /**
     * The slot text: text and numbers that spin into their new value on
     * staggered slot machine reels, one reel per character.
     */
    public static Element slotText(Context c, String text, float fontSize, String charset) {
        Element host = row(c).gap(1).label("kit-slot-text").alignItems(Align.CENTER);
        String chars = charset == null || charset.isEmpty() ? "0123456789" : charset;
        long now = c.now();
        for (int i = 0; i < text.length(); i++) {
            int ii = i;
            char target = text.charAt(ii);
            host.children(() -> {
                // Each reel settles on its own stagger.
                float settle = Kit.spring(host, "s" + ii, 1, Kit.MORPH);
                long spin = Math.max(0, (long) ((1 - settle) * 6));
                char shown = spin > 0
                        ? chars.charAt((int) ((now / 60_000_000L + ii) % chars.length()))
                        : target;
                Element cell = box(c).width(fontSize * 0.62f).height(fontSize * 1.15f)
                        .alignItems(Align.CENTER).clip()
                        .children(() -> text(c, String.valueOf(shown)).singleLine()
                                .fontSize(fontSize).fontWeight(600).textColor(c.theme().text()));
                if (spin > 0) {
                    c.after(java.time.Duration.ofMillis(48));
                }
            });
        }
        return host;
    }

    private TextEffects() {}
}
