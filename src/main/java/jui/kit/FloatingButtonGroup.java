package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's floating button group, after the FloatingButtonGroup: separate
 * soft buttons in a quiet tray, with one shared highlight that travels in
 * position and size to the hovered or focused button and fades when the
 * pointer leaves. Pressing darkens the highlight and settles it; the
 * buttons themselves never scale, so neighbours and tooltips stay still.
 * An action toolbar: each button does something; nothing stays selected.
 */
public final class FloatingButtonGroup {

    /** One tray button. */
    public static final class Item {
        public final String label;
        public final String[] reserveLabels;
        public final String icon;
        public final boolean iconOnly;
        public final boolean pressed;
        public final String shortcut;
        public final boolean disabled;
        public final Runnable onSelect;

        public Item(String label, String[] reserveLabels, String icon, boolean iconOnly, boolean pressed,
                    String shortcut, boolean disabled, Runnable onSelect) {
            this.label = label;
            this.reserveLabels = reserveLabels;
            this.icon = icon;
            this.iconOnly = iconOnly;
            this.pressed = pressed;
            this.shortcut = shortcut;
            this.disabled = disabled;
            this.onSelect = onSelect;
        }

        public static Item of(String label, String icon, Runnable onSelect) {
            return new Item(label, null, icon, false, false, null, false, onSelect);
        }

        public static Item iconOnly(String label, String icon, Runnable onSelect) {
            return new Item(label, null, icon, true, false, null, false, onSelect);
        }

        /** A short rule between clusters. */
        public static Item separator() {
            return new Item("", null, null, true, false, null, false, null);
        }
    }

    private static float textWidth(Context c, String s, float size) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return c.layoutText(s, size, 500, 4096).width;
    }

    /**
     * The tray: muted (page surface) or floating (raised over content),
     * sm or md heights, horizontal or vertical. reserveLabels keep a
     * label-swapping button at its widest width, so nothing shifts.
     */
    public static Element tray(Context c, String name, Item[] items, boolean floating, boolean small,
                               boolean vertical, boolean iconOnlyAll, boolean disabled) {
        float h = small ? 30 : 36;
        float pad = small ? 7 : 9;
        float fs = small ? 12 : 12.5f;
        float icon = small ? 14 : 16;
        float gap = small ? 2 : 3;
        Color trayBg = floating ? c.theme().background() : c.theme().surface();
        Element tray = vertical ? column(c) : row(c);
        tray.label("kit-floating-tray").padding(4).gap(gap).background(trayBg).radius(Kit.RADIUS + 3)
                .alignItems(Align.CENTER);
        if (floating) {
            Kit.floatShadow(tray);
            tray.border(1, Kit.hairline(c));
        }
        // Measured widths, with reserve labels taking the widest slot.
        int n = items.length;
        float[] widths = new float[n];
        for (int i = 0; i < n; i++) {
            Item it = items[i];
            if (it.icon == null && it.label.isEmpty()) {
                widths[i] = 1; // separator
                continue;
            }
            boolean only = it.iconOnly || iconOnlyAll;
            float w = only ? h : pad * 2;
            if (!only && it.label != null) {
                float widest = textWidth(c, it.label, fs);
                if (it.reserveLabels != null) {
                    for (String r : it.reserveLabels) {
                        widest = Math.max(widest, textWidth(c, r, fs));
                    }
                }
                w += widest;
                if (it.icon != null) {
                    w += icon + 7;
                }
            } else if (!only) {
                w += h - pad * 2; // no label: square
            }
            widths[i] = w;
        }
        Ref<Integer> hoverRef = local(tray, "hover", () -> -1);
        int last = hoverRef.get() != null && hoverRef.get() >= 0 && hoverRef.get() < n ? hoverRef.get() : -1;
        // The highlight's travel: position and size on the morph run, alpha on the fade.
        float a = Kit.spring(tray, "hla", disabled ? 0 : last >= 0 ? 1 : 0, Kit.FADE);
        float hx = Kit.spring(tray, "hlx", last >= 0 ? start(last, widths, gap) : 0, Kit.MORPH);
        float hw = Kit.spring(tray, "hlw", last >= 0 ? widths[last] : 0, Kit.MORPH);
        float[] cover = new float[n];
        if (a > 0.01f && hw > 0.01f) {
            float run = 0;
            for (int i = 0; i < n; i++) {
                float overlap = Math.min(hx + hw, run + widths[i]) - Math.max(hx, run);
                cover[i] = widths[i] > 0 ? Math.max(0, Math.min(1, overlap / widths[i])) * a : 0;
                run += widths[i] + gap;
            }
        }
        int hover = -1;
        for (int i = 0; i < n; i++) {
            Item it = items[i];
            if (it.icon == null && it.label.isEmpty()) {
                // A separator: a short rule across the axis.
                Element sep = vertical ? box(c).height(1).width(h - 12).shrink(0)
                        : box(c).width(1).height(h - 12).shrink(0);
                sep.background(Kit.hairline(c)).margin(vertical ? 3 : 0, vertical ? 0 : 3);
                continue;
            }
            boolean only = it.iconOnly || iconOnlyAll;
            Color bg = Color.rgba(0x808080, 0);
            float share = cover[i];
            if (it.pressed) {
                bg = c.theme().surfacePressed();
                share = Math.max(share, 0.55f);
            }
            Color base = Kit.mix(bg, c.theme().surfaceHover(), share);
            Element b = buttonBase(c).size(widths[i], h).shrink(0).radius(Kit.RADIUS_XS + 2)
                    .label("kit-float-" + it.label).background(base);
            if (disabled || it.disabled) {
                b.disabled(true);
            }
            if (b.hovered() && !disabled && !it.disabled) {
                hover = i;
            }
            if (b.focusVisible()) {
                hover = i;
            }
            float dip = Kit.press(b);
            b.children(() -> {
                Color t = disabled || it.disabled ? c.theme().textMuted()
                        : it.pressed ? c.theme().accent() : c.theme().text();
                if (it.icon != null) {
                    box(c).size(icon, icon).shrink(0).opacity(1 - 0.1f * dip).draw(Icon.draw(it.icon, t));
                }
                if (!only && it.label != null) {
                    text(c, it.label).singleLine().fontSize(fs).opacity(1 - 0.1f * dip).textColor(t);
                }
            });
            if (!disabled && !it.disabled && b.clicked() && it.onSelect != null) {
                it.onSelect.run();
            }
        }
        hoverRef.set(hover);
        return tray;
    }

    private static float start(int i, float[] widths, float gap) {
        float before = 0;
        for (int k = 0; k < i && k < widths.length; k++) {
            before += widths[k] + gap;
        }
        return before;
    }

    public static Element tray(Context c, String name, Item[] items) {
        return tray(c, name, items, false, false, false, false, false);
    }

    private FloatingButtonGroup() {}
}
