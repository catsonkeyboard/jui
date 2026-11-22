package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's expanding button group, after the ExpandingButtonGroup: icon
 * buttons in a compact group where one action always shows its label. The
 * one you point at grows to reveal its label on the morph run while its
 * neighbours slide aside; an action confirms in place, its icon becoming a
 * check and its label the done word for a moment.
 */
public final class ExpandingButtonGroup {

    /** One action: icon, label, and the optional done word. */
    public static final class Item {
        public final String label;
        public final String doneLabel;
        public final String icon;
        public final boolean danger;
        public final boolean disabled;
        public final Runnable onSelect;

        public Item(String label, String doneLabel, String icon, boolean danger, boolean disabled,
                    Runnable onSelect) {
            this.label = label;
            this.doneLabel = doneLabel;
            this.icon = icon;
            this.danger = danger;
            this.disabled = disabled;
            this.onSelect = onSelect;
        }

        public static Item of(String label, String icon, Runnable onSelect) {
            return new Item(label, null, icon, false, false, onSelect);
        }

        public static Item done(String label, String doneLabel, String icon, Runnable onSelect) {
            return new Item(label, doneLabel, icon, false, false, onSelect);
        }
    }

    private static float textWidth(Context c, String s, float size) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return c.layoutText(s, size, 500, 4096).width;
    }

    private static final class Run {
        int confirm = -1;
        long at;
    }

    /**
     * The group: sm (28) or md (36) heights, one resting expanded action,
     * confirmDuration ms before a confirmation tucks back.
     */
    public static Element group(Context c, String name, Item[] items, String defaultExpanded, boolean small,
                                int confirmDuration, boolean disabled) {
        float h = small ? 28 : 36;
        float icon = small ? 14 : 16;
        float fs = small ? 12 : 12.5f;
        float pad = 10;
        float gap = 6;
        Element host = row(c).gap(1).label("kit-expanding-group").alignItems(Align.CENTER);
        Ref<Integer> expandedRef = local(host, "exp", () -> -1);
        Ref<Run> runRef = local(host, "run", Run::new);
        Run run = runRef.get();
        long now = c.now();
        if (run.confirm >= 0 && now - run.at > confirmDuration * 1_000_000L) {
            run.confirm = -1;
            c.invalidate();
        }
        // The resting expanded index: the default, or what the pointer took.
        int expanded = expandedRef.get() != null && expandedRef.get() >= 0 ? expandedRef.get() : -1;
        if (expanded < 0) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].label.equals(defaultExpanded)) {
                    expanded = i;
                }
            }
            if (expanded < 0) {
                for (int i = 0; i < items.length; i++) {
                    if (!items[i].disabled) {
                        expanded = i;
                        break;
                    }
                }
            }
        }
        final int shown = expanded;
        host.children(() -> {
        for (int i = 0; i < items.length; i++) {
            Item it = items[i];
            boolean hot = i == shown && !disabled;
            boolean confirming = run.confirm == i;
            String word = confirming && it.doneLabel != null ? it.doneLabel : it.label;
            float iconW = h;
            float labelW = it.doneLabel != null
                    ? Math.max(textWidth(c, it.label, fs), textWidth(c, it.doneLabel, fs)) + gap
                    : textWidth(c, word, fs) + gap;
            // The width runs between icon-only and icon+label on the morph spring.
            float target = hot ? iconW + labelW + pad : iconW;
            float w = Kit.spring(host, "w" + i, target, Kit.MORPH);
            Element b = buttonBase(c).width(w).height(h).shrink(0).radius(Kit.RADIUS_XS + 2)
                    .label("kit-exp-" + it.label).clip();
            if (disabled || it.disabled) {
                b.disabled(true);
            }
            if (b.hovered() && !disabled && !it.disabled) {
                b.background(c.theme().surfaceHover());
                expandedRef.set(i);
            } else if (hot) {
                b.background(Color.rgba(0x808080, 0));
            }
            if (b.focusVisible()) {
                expandedRef.set(i);
            }
            float dip = Kit.press(b);
            Color t = it.danger ? Kit.danger(c) : c.theme().text();
            int ii = i;
            b.children(() -> {
                box(c).size(icon, icon).shrink(0).margin(0, (w - h) / 2)
                        .opacity(1 - 0.1f * dip)
                        .draw(confirming ? Icon.draw("check", t) : Icon.draw(it.icon, t));
                if (hot) {
                    // The label slides out from behind the icon with a short rise.
                    float open = Kit.spring(host, "o" + ii, 1, Kit.MORPH);
                    Element l = text(c, word).singleLine().fontSize(fs).textColor(t);
                    l.opacity(open).margin(0, -gap * (1 - open));
                }
            });
            if (!disabled && !it.disabled && b.clicked()) {
                boolean skip = false;
                if (it.onSelect != null) {
                    it.onSelect.run();
                }
                if (!skip && it.doneLabel != null) {
                    run.confirm = i;
                    run.at = c.now();
                    c.after(java.time.Duration.ofMillis(confirmDuration + 40));
                }
            }
        }
        });
        return host;
    }

    public static Element group(Context c, String name, Item[] items) {
        return group(c, name, items, null, false, 1400, false);
    }

    private ExpandingButtonGroup() {}
}
