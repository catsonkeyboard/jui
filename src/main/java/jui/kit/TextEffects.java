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
                float t = KitMotion.spring(host, "w" + ii, show ? 1 : 0, KitMotion.MORPH);
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
        float animW = KitMotion.spring(host, "w", w, KitMotion.MORPH);
        host.width(animW).height(fontSize * 1.35f);
        for (int i = 0; i < text.length(); i++) {
            int ii = i;
            char ch = text.charAt(ii);
            host.children(() -> {
                float t = KitMotion.spring(host, "ch" + ii, 1, KitMotion.MORPH);
                Element l = text(c, String.valueOf(ch)).singleLine().fontSize(fontSize)
                        .fontWeight(600).textColor(c.theme().text());
                l.opacity(t);
                l.top((1 - t) * fontSize * 0.35f);
            });
        }
        return host;
    }

    /**
     * The text shimmer: show ongoing work with a calm light across the
     * words — a light band sweeping the text's box, the text itself dimmed
     * until the band passes.
     */
    public static Element textShimmer(Context c, String text, float fontSize, boolean running) {
        Element host = box(c).clip().label("kit-text-shimmer");
        long t0 = c.now();
        host.children(() -> {
            Element l = text(c, text).singleLine().fontSize(fontSize)
                    .textColor(c.theme().textMuted());
            if (running) {
                c.after(java.time.Duration.ofMillis(32));
                host.draw((p, b) -> {
                    float phase = ((c.now() - t0) / 1_400_000_000L) % 1f;
                    float x = b.x() + (b.width() + 40) * phase - 20;
                    p.roundedBox(x, b.y() + 2, 18, b.height() - 4, 9,
                            Color.rgba(0xFFFFFF, 0.28f));
                });
                l.opacity(0.7f);
            }
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
                float settle = KitMotion.spring(host, "s" + ii, 1, KitMotion.MORPH);
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
