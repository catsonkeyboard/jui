package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Cursor;
import jui.ui.Element;
import jui.ui.Rect;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's sliders, after the Slider and its elastic, histogram and
 * ruler variants. The shared base is a track whose thumb follows a drag:
 * the value starts where it was when the press began and moves with the
 * pointer's offset. The variants add a value tag that follows the thumb,
 * rubber-banding past the ends with detents, a histogram lighting its bars
 * inside the range, and a ruler of ticks sliding under a fixed needle.
 */
public final class Sliders {

    private static final class Start {
        double v;
        boolean lo;

        Start(double v, boolean lo) {
            this.v = v;
            this.lo = lo;
        }
    }

    /** A drag's fraction across the track: dragX over the track's width. */
    private static float dragFraction(Element track) {
        Rect b = track.bounds();
        if (b == null || b.width() <= 0) {
            return 0;
        }
        return track.dragX() / b.width();
    }

    /**
     * The slider: value between lo and hi, a value tag above the thumb,
     * and a step that rounds the drag. A press jumps the value to where
     * the pointer landed, as the track follows the finger; the thumb
     * swells while held.
     */
    public static Element slider(Context c, jui.ui.DoubleRef value, double lo, double hi, double step,
                                 boolean disabled, java.util.function.DoubleFunction<String> format) {
        Element host = column(c).gap(4).label("kit-slider");
        host.children(() -> {
        Element track = box(c).fillWidth().height(20).alignItems(Align.CENTER).label("kit-slider-track");
        if (!disabled) {
            track.draggable().cursor(Cursor.POINTER);
        }
        Ref<Start> start = local(track, "start", () -> new Start(value.get(), false));
        boolean held = track.pressed() && !disabled;
        if (held) {
            // The track follows the pointer: the value sits under it, drag
            // or fresh press alike, as the elastic slider does.
            double v = lo + Math.max(0, Math.min(1, track.pointerInX()
                    / Math.max(1, track.contentWidth()))) * (hi - lo);
            if (step > 0) {
                v = lo + Math.round((v - lo) / step) * step;
            }
            v = Math.max(lo, Math.min(hi, v));
            if (v != value.get()) {
                value.set(v);
                c.invalidate();
            }
        }
        Rect b = track.bounds();
        float w = b == null ? 0 : b.width();
        float f = hi > lo ? (float) ((value.get() - lo) / (hi - lo)) : 0;
        float animF = Kit.spring(track, "f", f, Kit.SNAPPY);
        track.children(() -> {
            box(c).fillWidth().height(6).radius(3).background(c.theme().surfacePressed());
            box(c).absolute().left(0).top(7).bottom(7).width(Math.max(6, w * animF))
                    .radius(3).background(disabled ? c.theme().textMuted() : c.theme().accent());
            float swell = Kit.spring(track, "swell", held ? 1 : 0, Kit.SNAPPY);
            Element thumb = box(c).absolute().left(w * animF - 8 - 2 * swell).top(2)
                    .size(16 + 4 * swell, 16 + 4 * swell).radius(9 + 2 * swell)
                    .background(Color.rgb(0xFFFFFF))
                    .border(1, disabled ? c.theme().textMuted() : c.theme().accent());
            thumb.shadow(0, 1, 3, 0, Color.rgba(0x000000, 0.2f));
            if (format != null && !disabled) {
                Element tag = box(c).absolute().left(w * animF - 18).top(-24).padding(2, 6).radius(5)
                        .background(c.theme().background()).border(1, Kit.hairline(c))
                        .children(() -> text(c, format.apply(value.get())).singleLine().fontSize(10.5f)
                                .textColor(c.theme().text()));
                tag.opacity(held || track.hovered() ? 1 : 0);
            }
        });
        });
        return host;
    }

    /** The plain slider. */
    public static Element slider(Context c, jui.ui.DoubleRef value, double lo, double hi) {
        return slider(c, value, lo, hi, 0, false, null);
    }

    /**
     * The elastic slider: stretches like rubber past its ends, with a
     * swaying value tag, and detents the thumb clicks into. The value
     * never leaves [lo, hi]; the overshoot shows and drains back.
     */
    public static Element elastic(Context c, jui.ui.DoubleRef value, double lo, double hi, double step,
                                  double[] detents, boolean disabled) {
        Element host = column(c).gap(4).label("kit-elastic-slider");
        host.children(() -> {
        Element track = box(c).fillWidth().height(24).alignItems(Align.CENTER).label("kit-elastic-track");
        if (!disabled) {
            track.draggable().cursor(Cursor.POINTER);
        }
        Ref<Start> start = local(track, "start", () -> new Start(value.get(), false));
        Ref<Float> over = local(track, "over", () -> 0f);
        boolean held = track.pressed() && !disabled;
        if (held && track.dragX() != 0) {
            double raw = start.get().v + dragFraction(track) * (hi - lo);
            float o = 0;
            if (raw < lo) {
                o = (float) (lo - raw);
                raw = lo;
            } else if (raw > hi) {
                o = (float) (raw - hi);
                raw = hi;
            }
            if (detents != null) {
                for (double d : detents) {
                    if (Math.abs(raw - d) < (hi - lo) * 0.03) {
                        raw = d;
                        break;
                    }
                }
            }
            if (step > 0) {
                raw = lo + Math.round((raw - lo) / step) * step;
            }
            raw = Math.max(lo, Math.min(hi, raw));
            if (raw != value.get() || o != over.get()) {
                value.set(raw);
                over.set(o);
                c.invalidate();
            }
        } else if (over.get() != 0) {
            over.set(0f);
            c.invalidate();
        }
        Rect b = track.bounds();
        float w = b == null ? 0 : b.width();
        float f = hi > lo ? (float) ((value.get() - lo) / (hi - lo)) : 0;
        float rubber = Kit.spring(track, "over", over.get(), Kit.MORPH);
        float animF = Kit.spring(track, "f", f, Kit.SNAPPY);
        track.children(() -> {
            box(c).fillWidth().height(6).radius(3).background(c.theme().surfacePressed());
            float extend = (f > 0.5f ? rubber : -rubber) / (float) Math.max(1, hi - lo);
            box(c).absolute().left(0).top(9).bottom(9)
                    .width(Math.max(6, w * Math.max(0, Math.min(1.15f, animF + extend))))
                    .radius(3).background(c.theme().accent());
            Element thumb = box(c).absolute().left(w * animF - 8).top(4).size(16, 16).radius(8)
                    .background(Color.rgb(0xFFFFFF)).border(1, c.theme().accent());
            thumb.shadow(0, 1, 3, 0, Color.rgba(0x000000, 0.2f));
            float sway = Kit.spring(track, "sway", held ? (float) Math.max(-1, Math.min(1,
                    (value.get() - lo) / (hi - lo) - f)) : 0f, Kit.MORPH);
            Element tag = box(c).absolute().left(w * animF - 16 + sway * 4).top(-26).padding(2, 6).radius(5)
                    .background(c.theme().background()).border(1, Kit.hairline(c))
                    .children(() -> text(c, String.format("%.0f", value.get())).singleLine().fontSize(10.5f)
                            .textColor(c.theme().text()));
            tag.opacity(held ? 1 : 0);
        });
        });
        return host;
    }

    /**
     * The range histogram slider: a range over a live histogram. The bars
     * inside the range take the accent, the two thumbs never cross, and
     * the nearer thumb follows the drag.
     */
    public static Element rangeHistogram(Context c, jui.ui.DoubleRef loRef, jui.ui.DoubleRef hiRef,
                                         double lo, double hi, float[] bars, boolean disabled) {
        Element host = column(c).gap(4).label("kit-histogram-slider");
        host.children(() -> {
        Element panel = box(c).fillWidth().height(72).alignItems(Align.END).gap(1).label("kit-hist-bars");
        int n = Math.max(1, bars.length);
        for (int i = 0; i < n; i++) {
            int ii = i;
            double from = lo + (hi - lo) * ii / n, to = lo + (hi - lo) * (ii + 1) / n;
            boolean lit = to > loRef.get() && from < hiRef.get();
            panel.children(() -> {
                Element bar = box(c).grow(1).height(Math.max(4, bars[ii] * 68)).radius(2)
                        .label("kit-hist-bar");
                bar.background(lit ? c.theme().accent() : c.theme().surfacePressed());
                bar.opacity(lit ? 1 : 0.55f);
            });
        }
        Element track = box(c).fillWidth().height(24).alignItems(Align.CENTER).label("kit-range-track");
        if (!disabled) {
            track.draggable().cursor(Cursor.POINTER);
        }
        Ref<Start> start = local(track, "start", () -> new Start(loRef.get(), true));
        boolean held = track.pressed() && !disabled;
        if (held && track.dragX() == 0) {
            double v = (Math.abs(loRef.get() - hiRef.get()) < (hi - lo) * 0.5) ? loRef.get() : hiRef.get();
            start.get().v = v;
            start.get().lo = Math.abs(v - loRef.get()) <= Math.abs(v - hiRef.get());
        }
        if (held && track.dragX() != 0) {
            double v = start.get().v + dragFraction(track) * (hi - lo);
            v = Math.max(lo, Math.min(hi, v));
            if (start.get().lo) {
                loRef.set(Math.min(v, hiRef.get()));
            } else {
                hiRef.set(Math.max(v, loRef.get()));
            }
            c.invalidate();
        }
        Rect b = track.bounds();
        float w = b == null ? 0 : b.width();
        float fLo = hi > lo ? (float) ((loRef.get() - lo) / (hi - lo)) : 0;
        float fHi = hi > lo ? (float) ((hiRef.get() - lo) / (hi - lo)) : 0;
        float aLo = Kit.spring(track, "lo", fLo, Kit.SNAPPY);
        float aHi = Kit.spring(track, "hi", fHi, Kit.SNAPPY);
        track.children(() -> {
            box(c).fillWidth().height(6).radius(3).background(c.theme().surfacePressed());
            box(c).absolute().left(w * aLo).top(9).bottom(9).width(Math.max(0, w * (aHi - aLo)))
                    .radius(3).background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.4f));
            for (int s = 0; s < 2; s++) {
                float fx = s == 0 ? aLo : aHi;
                Element thumb = box(c).absolute().left(w * fx - 7).top(5).size(14, 14).radius(7)
                        .background(Color.rgb(0xFFFFFF)).border(1, c.theme().accent());
                thumb.shadow(0, 1, 3, 0, Color.rgba(0x000000, 0.2f));
            }
        });
        });
        return host;
    }

    /**
     * The ruler slider: a tape of ticks sliding under a fixed needle, the
     * move eased by the morph run, snapping to ticks, and a readout that
     * rolls with the value.
     */
    public static Element ruler(Context c, jui.ui.DoubleRef value, double lo, double hi, double tickStep,
                                boolean disabled) {
        Element host = column(c).gap(4).label("kit-ruler-slider");
        host.children(() -> {
        Element window = box(c).fillWidth().height(34).clip().label("kit-ruler-window");
        if (!disabled) {
            window.draggable();
        }
        Ref<Start> start = local(window, "start", () -> new Start(value.get(), false));
        boolean held = window.pressed() && !disabled;
        if (held && window.dragX() == 0) {
            start.get().v = value.get();
        }
        Rect b = window.bounds();
        float w = b == null ? 0 : b.width();
        float span = (float) (hi - lo);
        float pxPerUnit = w > 40 ? (w - 40) / span : 10;
        if (held && window.dragX() != 0) {
            double v = start.get().v - window.dragX() / pxPerUnit;
            v = Math.max(lo, Math.min(hi, v));
            if (tickStep > 0) {
                v = Math.round(v / tickStep) * tickStep;
            }
            if (v != value.get()) {
                value.set(v);
                c.invalidate();
            }
        }
        float center = (float) (value.get() - lo) * pxPerUnit;
        float animCenter = Kit.spring(window, "c", center, Kit.MORPH);
        window.children(() -> {
            int ticks = (int) (span / Math.max(tickStep, 0.01)) + 2;
            for (int i = 0; i <= ticks; i++) {
                double at = lo + i * tickStep;
                float x = (float) (at - lo) * pxPerUnit - animCenter + w / 2;
                if (x < -20 || x > w + 20) {
                    continue;
                }
                boolean major = i % 5 == 0;
                Element tick = box(c).absolute().left(x).bottom(6).width(1)
                        .height(major ? 14 : 8).background(c.theme().textMuted());
                tick.opacity(major ? 0.9f : 0.4f);
                if (major) {
                    box(c).absolute().left(x - 12).bottom(0).width(24).alignItems(Align.CENTER)
                            .children(() -> text(c, String.format("%.0f", at)).singleLine().fontSize(9)
                                    .textColor(c.theme().textMuted()));
                }
            }
            box(c).absolute().left(w / 2 - 1).top(2).width(2).height(20).radius(1)
                    .background(c.theme().accent());
        });
        text(c, String.format("%.0f", value.get())).singleLine().fontSize(13).fontWeight(500)
                .textColor(c.theme().text());
        });
        return host;
    }

    private Sliders() {}
}
