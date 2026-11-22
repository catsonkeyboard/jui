package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Painter;
import jui.ui.Rect;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's charts, after the chart family. One file holds the shared
 * plot plumbing — axes, gridlines, the gliding crosshair, series colors —
 * and the entry points for each chart: line, bar, donut, streamgraph,
 * brush, waffle, slope, sparkline, gauge, activity heatmap, animated
 * counter, ridgeline and treemap. Plots draw with painter primitives each
 * frame, so a series morphs as its data changes.
 */
public final class Charts {

    /** The series palette, after the accents. */
    public static final int[] SERIES = {0x3B82F6, 0x22C55E, 0xF59E0B, 0xEF4444, 0x8B5CF6, 0x14B8A6};

    public static Color series(int i) {
        return Color.rgb(SERIES[i % SERIES.length]);
    }

    private record Plot(float x, float y, float w, float h) {}

    /** The plot box: the axes labels outside, the plot inside. */
    private static Plot plot(Painter p, Rect b, double yMax, String[] xLabels, Context c) {
        float left = 34, bottom = 16;
        float px = b.x() + left, py = b.y() + 4;
        float pw = b.width() - left - 4, ph = b.height() - bottom - 8;
        Color grid = c.theme().border();
        Color label = c.theme().textMuted();
        // Horizontal gridlines with their labels.
        for (int g = 0; g <= 2; g++) {
            float gy = py + ph - ph * g / 2f;
            p.line(px, gy, px + pw, gy, 1, g == 0 ? grid : Color.rgba(grid.rgba() >>> 8, 0.5f));
            String s = yMax >= 1000 ? (long) Math.round(yMax * g / 2) / 1000 + "k"
                    : String.valueOf(Math.round(yMax * g / 2));
            drawLabel(p, px - 6, gy, s, label, true);
        }
        if (xLabels != null) {
            for (int i = 0; i < xLabels.length; i++) {
                float lx = px + pw * (i + 0.5f) / xLabels.length;
                drawLabel(p, lx, py + ph + 8, xLabels[i], label, false);
            }
        }
        return new Plot(px, py, pw, ph);
    }

    // The text engine lays out whole paragraphs, so a label draws through a
    // one-element cache keyed by its text and place.
    private static final ThreadLocal<String> TextHolder = new ThreadLocal<>();

    private static void drawLabel(Painter p, float x, float y, String s, Color col, boolean right) {
        p.text(s, right ? x - s.length() * 3.2f : x - s.length() * 3.2f, y - 5, 9, 400, col, 80);
    }

    /** A gentle horizontal sweep of the crosshair under the pointer. */
    private static float crosshair(Element plot) {
        return KitMotion.spring(plot, "cross", plot.hovered() ? 1 : 0, KitMotion.SNAPPY);
    }

    /**
     * The line chart: multi-series lines with a gliding crosshair, gridlines
     * and x labels; the paths morph between ranges as the caller changes
     * the arrays.
     */
    public static Element lineChart(Context c, String[] names, float[][] series, double yMax,
                                    String[] xLabels, float height) {
        Element host = box(c).fillWidth().height(height).label("kit-chart-line");
        Ref<Integer> hover = local(host, "hover", () -> -1);
        // Pointer facts read in the build pass: a draw callback cannot query
        // the element, only close over what the build already knew.
        final boolean hot = host.hovered();
        final float pointerX = host.pointerInX();
        final float hostWidth = Math.max(1, host.bounds().width());
        host.draw((Painter p, Rect b) -> {
            Plot pl = plot(p, b, yMax, xLabels, c);
            for (int s = 0; s < series.length; s++) {
                float[] data = series[s];
                if (data.length < 2) {
                    continue;
                }
                Color col = series(s);
                float step = pl.w() / (data.length - 1);
                for (int i = 1; i < data.length; i++) {
                    float x0 = pl.x() + (i - 1) * step, x1 = pl.x() + i * step;
                    float y0 = pl.y() + pl.h() - (float) (data[i - 1] / yMax) * pl.h();
                    float y1 = pl.y() + pl.h() - (float) (data[i] / yMax) * pl.h();
                    p.line(x0, y0, x1, y1, 2, col);
                }
            }
            // The gliding crosshair at the pointer's nearest index.
            if (hot) {
                int count = series.length > 0 ? series[0].length : 0;
                float frac = Math.max(0, Math.min(1, (pointerX / hostWidth * b.width() - 34)
                        / Math.max(1, b.width() - 38)));
                int idx = count > 0 ? Math.round(frac * (count - 1)) : -1;
                if (idx >= 0 && idx < count) {
                    float step = pl.w() / Math.max(1, count - 1);
                    float cx = pl.x() + idx * step;
                    p.line(cx, pl.y(), cx, pl.y() + pl.h(), 1, Color.rgba(c.theme().text().rgba() >>> 8, 0.2f));
                    for (int s = 0; s < series.length && idx < series[s].length; s++) {
                        float cy = pl.y() + pl.h() - (float) (series[s][idx] / yMax) * pl.h();
                        p.circle(cx, cy, 3.5f, Color.rgb(0xFFFFFF));
                        p.circle(cx, cy, 2.5f, series(s));
                    }
                }
            }
        });
        // The legend.
        row(c).gap(10).children(() -> {
            for (int s = 0; s < names.length; s++) {
                int ss = s;
                row(c).gap(5).children(() -> {
                    box(c).size(9, 9).radius(2).background(series(ss));
                    text(c, names[ss]).singleLine().fontSize(10.5f).textColor(c.theme().textMuted());
                });
            }
        });
        return host;
    }

    private static int nearest(Element plot, Plot pl, int count) {
        if (count <= 0 || pl.w() <= 0) {
            return -1;
        }
        float x = plot.pointerInX();
        if (x < 0) {
            return -1;
        }
        return Math.max(0, Math.min(count - 1, Math.round(x / pl.w() * (count - 1))));
    }

    /**
     * The bar chart: one measure across days, the hovered bar lifting with
     * its value read above it.
     */
    public static Element barChart(Context c, String[] labels, float[] values, double yMax,
                                   float height) {
        Element host = box(c).fillWidth().height(height).label("kit-chart-bar");
        final boolean hot = host.hovered();
        host.draw((Painter p, Rect b) -> {
            Plot pl = plot(p, b, yMax, labels, c);
            int n = Math.max(1, values.length);
            float slot = pl.w() / n;
            float bw = Math.min(28, slot * 0.62f);
            for (int i = 0; i < n; i++) {
                float x = pl.x() + slot * i + (slot - bw) / 2;
                float h = (float) (values[i] / yMax) * pl.h();
                Color col = Color.rgba(c.theme().accent().rgba() >>> 8, hot ? 1 : 0.75f);
                p.roundedBox(x, pl.y() + pl.h() - h, bw, h, 3, col);
                if (hot) {
                    String s = String.valueOf(Math.round(values[i]));
                    drawLabel(p, x + bw / 2, pl.y() + pl.h() - h - 6, s, c.theme().text(), false);
                }
            }
        });
        return host;
    }

    /**
     * The donut chart: arcs morph between datasets, with the active value
     * rolling into the center. Arcs draw as tick fans the painter can make.
     */
    public static Element donutChart(Context c, String[] names, float[] values, float size,
                                     int active) {
        Element host = box(c).size(size, size).label("kit-chart-donut");
        float total = 0;
        for (float v : values) {
            total += v;
        }
        float sum = Math.max(0.001f, total);
        host.draw((Painter p, Rect b) -> {
            float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
            float r = Math.min(b.width(), b.height()) / 2 - 6;
            float a0 = -(float) Math.PI / 2;
            for (int i = 0; i < values.length; i++) {
                float share = values[i] / sum;
                float a1 = a0 + share * 2 * (float) Math.PI;
                // The arc as chords along the ring, thick enough to read.
                int steps = Math.max(6, (int) (share * 64));
                for (int k = 0; k < steps; k++) {
                    float t0 = a0 + (a1 - a0) * k / steps, t1 = a0 + (a1 - a0) * (k + 1) / steps;
                    float rr = r - (i == active ? 2 : 0);
                    p.line(cx + (float) Math.cos(t0) * rr, cy + (float) Math.sin(t0) * rr,
                            cx + (float) Math.cos(t1) * rr, cy + (float) Math.sin(t1) * rr,
                            12, series(i));
                }
                a0 = a1;
            }
            // The center value.
            String center = active >= 0 && active < values.length
                    ? String.valueOf(Math.round(values[active])) : String.valueOf(Math.round(sum));
            drawLabel(p, cx, cy - 4, center, c.theme().text(), false);
            if (active >= 0 && active < names.length) {
                drawLabel(p, cx, cy + 8, names[active], c.theme().textMuted(), false);
            }
        });
        return host;
    }

    /**
     * The streamgraph: layered streams on a wiggle baseline, drawn stacked
     * and mirrored around the middle.
     */
    public static Element streamgraph(Context c, float[][] series, double yMax, String[] xLabels,
                                      float height) {
        Element host = box(c).fillWidth().height(height).label("kit-chart-stream");
        host.draw((Painter p, Rect b) -> {
            Plot pl = plot(p, b, yMax, xLabels, c);
            int n = series.length > 0 ? series[0].length : 0;
            if (n < 2) {
                return;
            }
            float step = pl.w() / (n - 1);
            // The streams stack from the bottom, then the whole shifts to center.
            float[] totals = new float[n];
            for (float[] data : series) {
                for (int i = 0; i < n; i++) {
                    totals[i] += data[i];
                }
            }
            for (int s = series.length - 1; s >= 0; s--) {
                float[] data = series[s];
                Color col = series(s);
                for (int i = 1; i < n; i++) {
                    float x0 = pl.x() + (i - 1) * step, x1 = pl.x() + i * step;
                    float base0 = pl.y() + pl.h(), base1 = pl.y() + pl.h();
                    float top0 = base0 - (float) (totals[i - 1] / yMax) * pl.h();
                    float top1 = base1 - (float) (totals[i] / yMax) * pl.h();
                    Color fill = Color.rgba(col.rgba() >>> 8, 0.5f);
                    p.line(x0, top0, x1, top1, Math.max(2f, pl.h() / (float) yMax * data[i]), fill);
                }
            }
        });
        return host;
    }

    /**
     * The brush chart: a dense series with an overview strip. Dragging in
     * the strip moves the window, its handles resize it, and the main plot
     * draws only the window's slice.
     */
    public static Element brushChart(Context c, float[] data, Ref<int[]> window, double yMax,
                                     float height) {
        Element host = column(c).fillWidth().gap(4).label("kit-chart-brush");
        int n = data.length;
        int[] w0 = window.get();
        if (w0 == null || w0.length != 2 || w0[0] > w0[1] || w0[1] > n || w0[0] < 0) {
            w0 = new int[]{0, n - 1};
            window.set(w0);
        }
        final int[] win = w0;
        // The main plot: the window's slice.
        Element main = box(c).fillWidth().height(height - 34).label("kit-brush-main");
        main.draw((Painter p, Rect b) -> {
            Plot pl = plot(p, b, yMax, null, c);
            int len = Math.max(2, win[1] - win[0]);
            float step = pl.w() / (len - 1);
            p.line(pl.x(), pl.y() + pl.h(), pl.x() + pl.w(), pl.y() + pl.h(), 1, c.theme().border());
            for (int i = 1; i < len; i++) {
                int i0 = win[0] + i - 1, i1 = win[0] + i;
                float x0 = pl.x() + (i - 1) * step, x1 = pl.x() + i * step;
                float y0 = pl.y() + pl.h() - (float) (data[Math.min(i0, n - 1)] / yMax) * pl.h();
                float y1 = pl.y() + pl.h() - (float) (data[Math.min(i1, n - 1)] / yMax) * pl.h();
                p.line(x0, y0, x1, y1, 1.6f, c.theme().accent());
            }
        });
        // The overview strip with the draggable window.
        Element strip = box(c).fillWidth().height(30).radius(4).background(c.theme().surface())
                .clip().draggable().label("kit-brush-strip");
        Rect sb = strip.bounds();
        float sw = sb == null ? 0 : sb.width();
        float f0 = n > 1 ? win[0] / (float) (n - 1) : 0, f1 = n > 1 ? win[1] / (float) (n - 1) : 1;
        float a0 = KitMotion.spring(strip, "w0", f0, KitMotion.SNAPPY);
        float a1 = KitMotion.spring(strip, "w1", f1, KitMotion.SNAPPY);
        strip.draw((Painter p, Rect b) -> {
            // The mini series.
            Color faint = Color.rgba(c.theme().textMuted().rgba() >>> 8, 0.6f);
            for (int i = 1; i < n; i++) {
                float x0 = b.x() + b.width() * (i - 1) / (n - 1f);
                float x1 = b.x() + b.width() * i / (n - 1f);
                float y0 = b.y() + b.height() - 3 - (float) (data[i - 1] / yMax) * (b.height() - 6);
                float y1 = b.y() + b.height() - 3 - (float) (data[i] / yMax) * (b.height() - 6);
                p.line(x0, y0, x1, y1, 1, faint);
            }
            // The window: a light panel with edge handles.
            float wx0 = b.x() + b.width() * a0, wx1 = b.x() + b.width() * a1;
            p.roundedBox(wx0, b.y(), Math.max(6, wx1 - wx0), b.height(), 3,
                    Color.rgba(c.theme().accent().rgba() >>> 8, 0.18f));
            p.line(wx0, b.y(), wx0, b.y() + b.height(), 2, c.theme().accent());
            p.line(wx1, b.y(), wx1, b.y() + b.height(), 2, c.theme().accent());
        });
        if (strip.pressed() && sw > 0 && strip.dragX() != 0) {
            int delta = Math.round(strip.dragX() / sw * n);
            int na = Math.max(0, Math.min(n - 2, win[0] + delta));
            int nb = Math.min(n - 1, na + Math.max(2, win[1] - win[0]));
            win[0] = na;
            win[1] = nb;
            c.invalidate();
        }
        return host;
    }

    /**
     * The waffle chart: a ten by ten unit chart where every cell is one
     * percent; cells fly to their group when the data changes, so the fill
     * reads on the morph run.
     */
    public static Element waffleChart(Context c, float[] shares, String[] names, float size) {
        Element host = box(c).size(size, size).label("kit-chart-waffle");
        host.draw((Painter p, Rect b) -> {
            float cell = Math.min(b.width(), b.height()) / 10 - 2;
            float gap = 2;
            int total = 100;
            int at = 0;
            for (int s = 0; s < shares.length && at < total; s++) {
                int count = Math.round(shares[s]);
                for (int k = 0; k < count && at < total; k++, at++) {
                    int cx = at % 10, cy = at / 10;
                    float x = b.x() + cx * (cell + gap), y = b.y() + cy * (cell + gap);
                    p.roundedBox(x, y, cell, cell, 2, series(s));
                }
            }
            while (at < total) {
                int cx = at % 10, cy = at / 10;
                float x = b.x() + cx * (cell + gap), y = b.y() + cy * (cell + gap);
                p.roundedBox(x, y, cell, cell, 2, c.theme().surfacePressed());
                at++;
            }
        });
        row(c).gap(10).children(() -> {
            for (int s = 0; s < names.length && s < shares.length; s++) {
                int ss = s;
                row(c).gap(5).children(() -> {
                    box(c).size(9, 9).radius(2).background(series(ss));
                    text(c, names[ss] + " " + Math.round(shares[ss]) + "%").singleLine().fontSize(10.5f)
                            .textColor(c.theme().textMuted());
                });
            }
        });
        return host;
    }

    /**
     * The slope chart: before and after on two axes. Lines draw in, rank
     * moves sit beside each value, and switching datasets slides every
     * line to its new slope.
     */
    public static Element slopeChart(Context c, String[] names, float[] before, float[] after,
                                     float width, float height) {
        Element host = box(c).size(width, height).label("kit-chart-slope");
        host.draw((Painter p, Rect b) -> {
            float max = 1;
            for (int i = 0; i < names.length; i++) {
                max = Math.max(max, Math.max(before[i], after[i]));
            }
            float x0 = b.x() + 40, x1 = b.x() + b.width() - 40;
            drawLabel(p, x0, b.y() + 8, "Before", c.theme().textMuted(), false);
            drawLabel(p, x1, b.y() + 8, "After", c.theme().textMuted(), false);
            for (int i = 0; i < names.length; i++) {
                float y0 = b.y() + b.height() - 10 - (before[i] / max) * (b.height() - 24);
                float y1 = b.y() + b.height() - 10 - (after[i] / max) * (b.height() - 24);
                p.line(x0, y0, x1, y1, 2, series(i));
                p.circle(x0, y0, 3, series(i));
                p.circle(x1, y1, 3, series(i));
                drawLabel(p, x0 - 6, y0 + 3, names[i], c.theme().text(), true);
                drawLabel(p, x1 + 6, y1 + 3, names[i], c.theme().text(), false);
            }
        });
        return host;
    }

    /** The sparkline: a compact trend beside a value. */
    public static Element sparkline(Context c, float[] data, float width, float height, Color color) {
        Element host = box(c).size(width, height).label("kit-sparkline");
        host.draw((Painter p, Rect b) -> {
            float max = 0.0001f;
            for (float v : data) {
                max = Math.max(max, v);
            }
            float step = b.width() / Math.max(1, data.length - 1);
            Color col = color == null ? c.theme().accent() : color;
            for (int i = 1; i < data.length; i++) {
                float x0 = b.x() + (i - 1) * step, x1 = b.x() + i * step;
                float y0 = b.y() + b.height() - data[i - 1] / max * (b.height() - 2) - 1;
                float y1 = b.y() + b.height() - data[i] / max * (b.height() - 2) - 1;
                p.line(x0, y0, x1, y1, 1.5f, col);
            }
            p.circle(b.x() + b.width() - 2, b.y() + b.height() - data[data.length - 1] / max
                    * (b.height() - 2) - 1, 2.5f, col);
        });
        return host;
    }

    /**
     * The gauge: a value against a known range, read as a filled arc from
     * the bottom with the value rolling in the middle.
     */
    public static Element gauge(Context c, double value, double lo, double hi, String unit,
                                float size) {
        Element host = box(c).size(size, size * 0.62f).label("kit-chart-gauge");
        host.draw((Painter p, Rect b) -> {
            float cx = b.x() + b.width() / 2, cy = b.y() + b.height() - 6;
            float r = Math.min(b.width() / 2, b.height()) - 8;
            float a0 = (float) Math.PI, a1 = 2f * (float) Math.PI;
            float f = (float) Math.max(0, Math.min(1, (value - lo) / (hi - lo)));
            float sweep = a0 + (a1 - a0) * f;
            int steps = 48;
            for (int k = 0; k < steps; k++) {
                float t0 = a0 + (a1 - a0) * k / steps, t1 = a0 + (a1 - a0) * (k + 1) / steps;
                Color col = t0 <= sweep ? c.theme().accent() : c.theme().surfacePressed();
                p.line(cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                        cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, 9, col);
            }
            String s = unit != null ? String.format("%.0f%s", value, unit) : String.format("%.0f", value);
            drawLabel(p, cx, cy - r / 3, s, c.theme().text(), false);
        });
        return host;
    }

    /**
     * The activity heatmap: a year of activity, one square per day, weeks
     * as columns; the level reads through four steps of the accent.
     */
    public static Element activityHeatmap(Context c, float[] days, float max, float width) {
        Element host = box(c).width(width).height(96).label("kit-chart-heatmap");
        host.draw((Painter p, Rect b) -> {
            float cell = 10, gap = 3;
            int weeks = (int) ((b.width() - 24) / (cell + gap));
            Color base = c.theme().surfacePressed();
            for (int i = 0; i < days.length && i < weeks * 7; i++) {
                int col = i / 7, row = i % 7;
                float x = b.x() + 20 + col * (cell + gap);
                float y = b.y() + 4 + row * (cell + gap);
                float f = max > 0 ? days[i] / max : 0;
                Color col2 = f <= 0 ? base
                        : Color.rgba(c.theme().accent().rgba() >>> 8, Math.max(0.25f, Math.min(1, f)));
                p.roundedBox(x, y, cell, cell, 2, col2);
            }
            String[] monthLabels = {"Jan", "Mar", "May", "Jul", "Sep", "Nov"};
            for (int m = 0; m < monthLabels.length; m++) {
                drawLabel(p, b.x() + 20 + weeks * (cell + gap) * m / 6f, b.y() + 8,
                        monthLabels[m], c.theme().textMuted(), false);
            }
        });
        return host;
    }

    /**
     * The animated counter: changing totals get a clear sense of movement —
     * the value rolls on the morph run and settles into place.
     */
    public static Element animatedCounter(Context c, jui.ui.DoubleRef value, String prefix, String suffix,
                                          float fontSize) {
        Element host = box(c).label("kit-animated-counter");
        float shown = KitMotion.spring(host, "v", (float) value.get(), KitMotion.MORPH);
        host.children(() -> {
            String s = (prefix == null ? "" : prefix)
                    + (shown == Math.floor(shown) ? String.valueOf((long) shown)
                    : String.format("%.1f", shown))
                    + (suffix == null ? "" : suffix);
            text(c, s).singleLine().fontSize(fontSize).fontWeight(600).textColor(c.theme().text());
        });
        return host;
    }

    /**
     * The ridgeline: overlapping distributions, one ridge per group. Hover
     * lifts a ridge and reads its quartiles.
     */
    public static Element ridgeline(Context c, String[] names, float[][] distributions,
                                    float width, float height) {
        Element host = box(c).size(width, height).label("kit-chart-ridgeline");
        host.draw((Painter p, Rect b) -> {
            int groups = names.length;
            float rowH = b.height() / Math.max(1, groups);
            for (int g = groups - 1; g >= 0; g--) {
                float[] d = distributions[g];
                if (d.length < 2) {
                    continue;
                }
                float max = 0.0001f;
                for (float v : d) {
                    max = Math.max(max, v);
                }
                float lift = 0;
                Color col = series(g);
                float baseY = b.y() + rowH * g + rowH * 0.8f;
                float step = b.width() / (d.length - 1);
                for (int i = 1; i < d.length; i++) {
                    float x0 = b.x() + (i - 1) * step, x1 = b.x() + i * step;
                    float y0 = baseY - d[i - 1] / max * (rowH * 0.75f);
                    float y1 = baseY - d[i] / max * (rowH * 0.75f);
                    p.line(x0, y0, x1, y1, 1.6f, col);
                }
                // The fill under the ridge, faint.
                Color fill = Color.rgba(col.rgba() >>> 8, 0.14f);
                for (int i = 1; i < d.length; i++) {
                    float x0 = b.x() + (i - 1) * step, x1 = b.x() + i * step;
                    float y0 = baseY - d[i - 1] / max * (rowH * 0.75f);
                    float y1 = baseY - d[i] / max * (rowH * 0.75f);
                    p.line(x0, y0, x1, y1, 2.4f, fill);
                }
                drawLabel(p, b.x() + 4, baseY - 2, names[g], c.theme().textMuted(), false);
            }
        });
        return host;
    }

    /**
     * The treemap: a squarified treemap. Clicking a tile drills and the
     * tiles grow to fill the view; the caller re-roots through the ref.
     */
    public static Element treemap(Context c, String[] names, float[] values, Ref<Integer> drill,
                                  float width, float height) {
        Element host = box(c).size(width, height).gap(2).wrap().alignContent(Align.START)
                .clip().label("kit-chart-treemap");
        float totalSum = 0.0001f;
        for (float v : values) {
            totalSum += v;
        }
        final float total = totalSum;
        // Squarify-lite: rows of tiles whose areas read like the shares.
        float x = 0, y = 0;
        float w = width, h = height;
        int i = 0;
        while (i < names.length) {
            boolean rowFirst = w >= h;
            int count = Math.max(1, (int) (rowFirst ? h / 34 : w / 34));
            float shareSum = 0;
            for (int k = i; k < Math.min(names.length, i + count); k++) {
                shareSum += values[k];
            }
            float thickness = rowFirst
                    ? h * (shareSum / total)
                    : w * (shareSum / total);
            float along = 0;
            float alongSize = rowFirst ? w : h;
            for (int k = i; k < Math.min(names.length, i + count); k++) {
                int kk = k;
                float a = alongSize * values[kk] / Math.max(shareSum, 0.0001f);
                float tw = rowFirst ? a : thickness;
                float th = rowFirst ? thickness : a;
                float tx = rowFirst ? x + along : x;
                float ty = rowFirst ? y : y + along;
                Element tile = box(c).absolute().left(tx).top(ty).width(tw - 2).height(th - 2)
                        .radius(4).background(series(kk % SERIES.length)).clip()
                        .padding(6).label("kit-tile-" + names[kk]);
                tile.children(() -> {
                    text(c, names[kk]).singleLine().fontSize(10).textColor(Color.rgb(0xFFFFFF));
                    text(c, Math.round(values[kk] / total * 100) + "%").singleLine().fontSize(9)
                            .textColor(Color.rgba(0xFFFFFF, 0.75f));
                });
                if (tile.clicked() && drill != null) {
                    drill.set(kk);
                    c.invalidate();
                }
                along += a;
            }
            if (rowFirst) {
                y += thickness;
                h -= thickness;
            } else {
                x += thickness;
                w -= thickness;
            }
            i += count;
            if (h <= 0 || w <= 0) {
                break;
            }
        }
        return host;
    }

    /**
     * The funnel chart: stages narrowing top to bottom, each band its
     * share of the widest stage, a label left and the value inside.
     */
    public static Element funnelChart(Context c, String[] stages, float[] values,
                                      float width, float height) {
        Element host = box(c).width(width).height(height).label("kit-chart-funnel");
        host.draw((Painter p, Rect b) -> {
            float max = 1;
            for (float v : values) {
                max = Math.max(max, v);
            }
            float bandH = b.height() / Math.max(1, values.length);
            float gap = Math.min(4, bandH * 0.18f);
            float bandH2 = bandH - gap;
            for (int i = 0; i < values.length; i++) {
                float w0 = b.width() * Math.max(0.08f, values[i] / max);
                Color col = series(i);
                p.roundedBox(b.x() + (b.width() - w0) / 2, b.y() + i * bandH, w0, bandH2, 3,
                        Color.rgba(col.rgba() >>> 8, 0.85f));
                // The connector to the next stage, a tapering scanline fill.
                if (i + 1 < values.length) {
                    float w1 = b.width() * Math.max(0.08f, values[i + 1] / max);
                    float y0 = b.y() + i * bandH + bandH2;
                    for (int t = 0; t < 10; t++) {
                        float u = (t + 0.5f) / 10;
                        float w = w0 + (w1 - w0) * u;
                        p.box(b.x() + (b.width() - w) / 2, y0 + gap * u, w, gap / 10 + 0.5f,
                                Color.rgba(col.rgba() >>> 8, 0.35f * (1 - u)));
                    }
                }
                String label = stages[i];
                p.text(label, b.x() - label.length() * 6.4f, b.y() + i * bandH + bandH2 / 2 - 6,
                        10.5f, 500, c.theme().textMuted(), 120);
                String value = String.valueOf(Math.round(values[i]));
                p.text(value, b.x() + (b.width() - w0) / 2 + 8, b.y() + i * bandH + bandH2 / 2 - 6,
                        10.5f, 600, Color.rgb(0xFFFFFF), 80);
            }
        });
        return host;
    }

    /**
     * The radar chart: one polygon over named axes, its fill suggested by
     * radial scans, over a web of rings and spokes.
     */
    public static Element radarChart(Context c, String[] axes, float[] values, double max, float size) {
        Element host = box(c).size(size, size).label("kit-chart-radar");
        host.draw((Painter p, Rect b) -> {
            float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
            float r = Math.min(b.width(), b.height()) / 2 - 14;
            int n = Math.max(3, axes.length);
            Color web = c.theme().border();
            for (int ring = 1; ring <= 3; ring++) {
                float rr = r * ring / 3f;
                for (int i = 0; i < n; i++) {
                    double a0 = -Math.PI / 2 + 2 * Math.PI * i / n;
                    double a1 = -Math.PI / 2 + 2 * Math.PI * (i + 1) / n;
                    p.line(cx + (float) Math.cos(a0) * rr, cy + (float) Math.sin(a0) * rr,
                            cx + (float) Math.cos(a1) * rr, cy + (float) Math.sin(a1) * rr, 1,
                            Color.rgba(web.rgba() >>> 8, ring == 3 ? 0.9f : 0.4f));
                }
            }
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + 2 * Math.PI * i / n;
                p.line(cx, cy, cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r, 1,
                        Color.rgba(web.rgba() >>> 8, 0.5f));
            }
            // The value polygon: radial scans read as a translucent fill.
            Color ink = series(0);
            for (int t = 1; t <= 18; t++) {
                float f = t / 18f;
                float alpha = f >= 0.92f ? 0.55f : 0.05f;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double ai = -Math.PI / 2 + 2 * Math.PI * i / n;
                    double aj = -Math.PI / 2 + 2 * Math.PI * j / n;
                    float ri = r * f * (float) Math.min(1, values[i] / max);
                    float rj = r * f * (float) Math.min(1, values[j] / max);
                    p.line(cx + (float) Math.cos(ai) * ri, cy + (float) Math.sin(ai) * ri,
                            cx + (float) Math.cos(aj) * rj, cy + (float) Math.sin(aj) * rj, 1.2f,
                            Color.rgba(ink.rgba() >>> 8, alpha));
                }
            }
            for (int i = 0; i < n; i++) {
                double a = -Math.PI / 2 + 2 * Math.PI * i / n;
                float ri = r * (float) Math.min(1, values[i] / max);
                p.circle(cx + (float) Math.cos(a) * ri, cy + (float) Math.sin(a) * ri, 3, ink);
                double la = a + Math.PI / n;
                String label = axes[i];
                p.text(label, cx + (float) Math.cos(la) * (r + 6) - label.length() * 3.2f,
                        cy + (float) Math.sin(la) * (r + 6) - 5, 9.5f, 400, c.theme().textMuted(), 100);
            }
        });
        return host;
    }

    /** The split bar: one horizontal measure cut into named segments, a
     *  legend under it, after the split bar. */
    public static Element splitBar(Context c, String[] names, float[] shares,
                                   float width, float height) {
        float total = 0;
        for (float v : shares) {
            total += v;
        }
        final float sum = Math.max(0.0001f, total);
        Element host = column(c).width(width).gap(6).label("kit-chart-splitbar");
        host.children(() -> {
            row(c).height(height).gap(1).children(() -> {
                for (int i = 0; i < shares.length; i++) {
                    int idx = i;
                    box(c).fillWidth().grow((float) (shares[idx] / sum)).height(height)
                            .radius(idx == 0 || idx == shares.length - 1 ? 3 : 1)
                            .background(series(idx));
                }
            });
            row(c).gap(10).children(() -> {
                for (int i = 0; i < names.length; i++) {
                    int ss = i;
                    row(c).gap(5).children(() -> {
                        box(c).size(9, 9).radius(2).background(series(ss));
                        text(c, names[ss]).singleLine().fontSize(10.5f)
                                .textColor(c.theme().textMuted());
                    });
                }
            });
        });
        return host;
    }

    /** One thick arc segment of a ring, as short line steps along the circle. */
    private static void ringSegment(Painter p, float cx, float cy, float r,
                                    double a0, double a1, float width, Color col) {
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) * r / 2.5f));
        for (int i = 0; i < steps; i++) {
            double t0 = a0 + (a1 - a0) * i / steps;
            double t1 = a0 + (a1 - a0) * (i + 1) / steps;
            p.line(cx + (float) Math.cos(t0) * r, cy + (float) Math.sin(t0) * r,
                    cx + (float) Math.cos(t1) * r, cy + (float) Math.sin(t1) * r, width, col);
        }
    }

    /** The sunburst: one ring cut into named arcs proportional to their
     *  share, the total in the middle, a legend under it. */
    public static Element sunburstChart(Context c, String[] names, float[] shares, float size) {
        float total = 0;
        for (float v : shares) {
            total += v;
        }
        final float sum = Math.max(0.0001f, total);
        Element host = column(c).gap(6).label("kit-chart-sunburst");
        host.children(() -> {
            box(c).size(size, size).draw((Painter p, Rect b) -> {
                float cx = b.x() + b.width() / 2, cy = b.y() + b.height() / 2;
                float r = Math.min(b.width(), b.height()) / 2 - 4;
                double a = -Math.PI / 2;
                for (int i = 0; i < shares.length; i++) {
                    double span = 2 * Math.PI * shares[i] / sum;
                    ringSegment(p, cx, cy, r, a + 0.02, a + span - 0.02, 14, series(i));
                    a += span;
                }
                String totalText = String.valueOf(Math.round(sum));
                p.text(totalText, cx - totalText.length() * 3.4f, cy - 7, 12, 600,
                        c.theme().text(), 80);
            });
            row(c).gap(10).children(() -> {
                for (int i = 0; i < names.length; i++) {
                    int ss = i;
                    row(c).gap(5).children(() -> {
                        box(c).size(9, 9).radius(2).background(series(ss));
                        text(c, names[ss]).singleLine().fontSize(10.5f)
                                .textColor(c.theme().textMuted());
                    });
                }
            });
        });
        return host;
    }

    /** The sankey flow: sources on the left, sinks on the right, one tapering
     *  ribbon per flow — a scanline fill interpolating the two spans. */
    public static Element sankeyFlow(Context c, String[] leftNames, String[] rightNames,
                                     float[][] flows, float width, float height) {
        float leftSum = 0, rightSum = 0;
        for (float[] row : flows) {
            float l = 0, r = 0;
            for (int j = 0; j < row.length; j++) {
                l += row[j];
                if (j < rightNames.length) {
                    r += row[j];
                }
            }
            leftSum += l;
            rightSum += Math.max(rightSum, 0) + 0;                 // column sums computed below
        }
        float[] colSum = new float[rightNames.length];
        for (float[] row : flows) {
            for (int j = 0; j < Math.min(rightNames.length, row.length); j++) {
                colSum[j] += row[j];
            }
        }
        for (float v : colSum) {
            rightSum = Math.max(rightSum, v);
        }
        rightSum = Math.max(1, rightSum);
        final float lTotal = Math.max(1, leftSum);
        final float rTotal = rightSum;
        Element host = box(c).width(width).height(height).label("kit-chart-sankey");
        host.draw((Painter p, Rect b) -> {
            float nodeW = 10, pad = 6;
            float lh = (b.height() - (leftNames.length - 1) * 8) / Math.max(1, leftNames.length);
            float rh = (b.height() - (rightNames.length - 1) * 8) / Math.max(1, rightNames.length);
            float x0 = b.x() + pad, x1 = b.x() + b.width() - nodeW - pad;
            float[] leftY = new float[leftNames.length];
            float ly = b.y();
            for (int i = 0; i < leftNames.length; i++) {
                leftY[i] = ly;
                float h = Math.max(6, lh * (rowSum(flows, i) / lTotal) * leftNames.length);
                p.roundedBox(x0, ly, nodeW, h, 3, series(i));
                p.text(leftNames[i], x0 + nodeW + 5, ly + h / 2 - 5, 10, 400,
                        c.theme().textMuted(), 120);
                ly += h + 8;
            }
            float[] rightY = new float[rightNames.length];
            float ry = b.y();
            for (int j = 0; j < rightNames.length; j++) {
                rightY[j] = ry;
                float h = Math.max(6, rh * (colSum[j] / rTotal) * rightNames.length);
                p.roundedBox(x1, ry, nodeW, h, 3, series(j));
                p.text(rightNames[j], x1 - rightNames[j].length() * 6.2f - 5, ry + h / 2 - 5,
                        10, 400, c.theme().textMuted(), 120);
                ry += h + 8;
            }
            // The ribbons: each flow a scanline fill between its slots.
            float[] leftSlot = new float[leftNames.length];
            float[] rightSlot = new float[rightNames.length];
            for (int i = 0; i < leftNames.length; i++) {
                for (int j = 0; j < Math.min(rightNames.length, flows[i].length); j++) {
                    float v = flows[i][j];
                    if (v <= 0) {
                        continue;
                    }
                    float lh0 = Math.max(6, lh * (rowSum(flows, i) / lTotal) * leftNames.length)
                            * (v / Math.max(1, rowSum(flows, i)));
                    float rh0 = Math.max(6, rh * (colSum[j] / rTotal) * rightNames.length)
                            * (v / Math.max(1, colSum[j]));
                    Color col = series((i + j) % SERIES.length);
                    int slices = 26;
                    for (int t = 0; t < slices; t++) {
                        float u = (t + 0.5f) / slices;
                        float x = x0 + nodeW + (x1 - x0 - nodeW) * u;
                        float sy = leftY[i] + leftSlot[i] + lh0 / 2;
                        float ey = rightY[j] + rightSlot[j] + rh0 / 2;
                        float half = (lh0 + (rh0 - lh0) * u) / 2;
                        float yy = sy + (ey - sy) * u;
                        p.box(x, yy - half / 2, (x1 - x0 - nodeW) / slices + 1, half,
                                Color.rgba(col.rgba() >>> 8, 0.30f));
                    }
                    leftSlot[i] += lh0;
                    rightSlot[j] += rh0;
                }
            }
        });
        return host;
    }

    private static float rowSum(float[][] flows, int i) {
        float sum = 0;
        for (float v : flows[i]) {
            sum += v;
        }
        return Math.max(0.0001f, sum);
    }

    private Charts() {}
}
