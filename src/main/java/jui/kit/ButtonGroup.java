package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's button group, after the ButtonGroup: related actions joined
 * into one surface with hairline dividers. One highlight lives under all
 * segments and glides to the hovered or focused one on the snappy spring,
 * fading in where it lands when it arrives from outside; the pressed
 * segment dips its content while the highlight deepens. Segment widths are
 * measured up front, so labels never resize anything: hover, press and a
 * label change leave every segment at its box.
 */
public final class ButtonGroup {

    /** One segment: a fixed label slot, an icon, and the action. */
    public static final class Segment {
        public final String label;
        public final String icon;
        public final boolean iconOnly;
        public final String content;
        public final boolean disabled;
        public final Runnable onSelect;

        public Segment(String label, String icon, boolean iconOnly, String content, boolean disabled,
                       Runnable onSelect) {
            this.label = label;
            this.icon = icon;
            this.iconOnly = iconOnly;
            this.content = content;
            this.disabled = disabled;
            this.onSelect = onSelect;
        }

        public static Segment of(String label, String icon, Runnable onSelect) {
            return new Segment(label, icon, false, null, false, onSelect);
        }

        public static Segment icon(String label, String icon, Runnable onSelect) {
            return new Segment(label, icon, true, null, false, onSelect);
        }

        public static Segment value(String label, String content, Runnable onSelect) {
            return new Segment(label, null, false, content, false, onSelect);
        }
    }

    /** The trailing chevron segment's menu items. */
    public record MenuItem(String label, String icon, boolean destructive, boolean disabled, Runnable onSelect) {}

    private static float textWidth(Context c, String s, float size) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        return c.layoutText(s, size, 500, 4096).width;
    }

    private static float start(int i, float[] widths) {
        float before = 0;
        for (int k = 0; k < i && k < widths.length; k++) {
            before += widths[k];
        }
        return before;
    }

    /**
     * The group: outline (neutral surface, quiet border) or solid (primary
     * fill), sm or md, horizontal or vertical, with an optional trailing
     * chevron menu. The highlight's hover read is one frame behind the
     * pointer, which no one can see; everything else is exact.
     */
    public static Element group(Context c, String name, Segment[] items, MenuItem[] menu,
                                boolean solid, boolean small, boolean vertical, boolean disabled) {
        float h = small ? 30 : 36;
        float pad = small ? 9 : 12;
        float fs = small ? 12 : 12.5f;
        float icon = small ? 14 : 16;
        Color fill = solid ? ButtonBase.faceFill(c, Button.Variant.PRIMARY) : KitMotion.surface(c);
        Color text = solid ? ButtonBase.faceText(c, Button.Variant.PRIMARY) : c.theme().text();
        Color hl = solid ? KitMotion.mix(fill, Color.rgb(0x000000), 0.14f) : c.theme().surfaceHover();
        int n = items.length + (menu != null ? 1 : 0);
        float[] widths = new float[n];
        for (int i = 0; i < items.length; i++) {
            Segment s = items[i];
            float w = pad * 2;
            if (s.icon != null) {
                w += icon + 7;
            }
            if (!s.iconOnly && s.content == null) {
                w += textWidth(c, s.label, fs);
            } else if (s.content != null) {
                w += textWidth(c, s.content, fs);
            }
            widths[i] = Math.max(w, h);
        }
        if (menu != null) {
            widths[items.length] = h;
        }
        Element host = vertical ? column(c) : row(c);
        host.label("kit-button-group").alignItems(Align.STRETCH)
                .background(fill).border(1, solid ? fill : KitMotion.hairline(c))
                .radius(Kit.RADIUS + 1).clip();
        Ref<Integer> hoverRef = local(host, "hover", () -> -1);
        // The gliding highlight, from last frame's hover: each segment's fill
        // takes the share of the highlight box that covers it.
        int last = hoverRef.get() != null && hoverRef.get() >= 0 && hoverRef.get() < n ? hoverRef.get() : -1;
        float a = KitMotion.spring(host, "hla", disabled ? 0 : last >= 0 ? 1 : 0, KitMotion.FADE);
        float hx = KitMotion.spring(host, "hlx", last >= 0 ? start(last, widths) : 0, KitMotion.SNAPPY);
        float hw = KitMotion.spring(host, "hlw", last >= 0 ? widths[last] : 0, KitMotion.SNAPPY);
        float[] share = new float[n];
        if (a > 0.01f && hw > 0.01f && !disabled) {
            float run = 0;
            for (int i = 0; i < n; i++) {
                float overlap = Math.min(hx + hw, run + widths[i]) - Math.max(hx, run);
                share[i] = widths[i] > 0 ? Math.max(0, Math.min(1, overlap / widths[i])) * a : 0;
                run += widths[i];
            }
        }
        int[] hoverBox = {-1};
        host.children(() -> {
            for (int i = 0; i < items.length; i++) {
                Segment s = items[i];
                float sh = share[i];
                Element seg = buttonBase(c).width(widths[i]).height(h).shrink(0).label("kit-group-seg-" + i);
                if (disabled || s.disabled) {
                    seg.disabled(true);
                }
                if (seg.hovered() && !disabled) {
                    hoverBox[0] = i;
                }
                if (seg.focusVisible()) {
                    hoverBox[0] = i;
                }
                float dip = KitMotion.press(seg);
                Color bg = KitMotion.mix(KitMotion.mix(fill, hl, sh), Color.rgb(0x000000), 0.12f * dip);
                if (!s.disabled && !disabled) {
                    seg.background(bg);
                }
                seg.children(() -> {
                    Color t = disabled || s.disabled ? c.theme().textMuted() : text;
                    if (s.icon != null) {
                        box(c).size(icon, icon).shrink(0).opacity(1 - 0.1f * dip).draw(Icon.draw(s.icon, t));
                    }
                    if (!s.iconOnly && s.content == null) {
                        text(c, s.label).singleLine().fontSize(fs).opacity(1 - 0.1f * dip).textColor(t);
                    } else if (s.content != null) {
                        text(c, s.content).singleLine().fontSize(fs).textColor(t);
                    }
                });
                if (!disabled && !s.disabled && seg.clicked() && s.onSelect != null) {
                    s.onSelect.run();
                }
            }
            if (menu != null) {
                int mi = items.length;
                float sh = share[mi];
                Element chev = buttonBase(c).size(h, h).shrink(0).label("kit-group-menu");
                if (disabled) {
                    chev.disabled(true);
                }
                if (chev.hovered() && !disabled) {
                    hoverBox[0] = mi;
                }
                if (chev.focusVisible()) {
                    hoverBox[0] = mi;
                }
                float dip = KitMotion.press(chev);
                if (!disabled) {
                    chev.background(KitMotion.mix(KitMotion.mix(fill, hl, sh), Color.rgb(0x000000), 0.12f * dip));
                }
                jui.ui.BoolRef open = localBool(chev, "open", false);
                chev.children(() -> {
                    Color t2 = disabled ? c.theme().textMuted() : text;
                    IconMorph.icon(c, IconMorph.CHEVRON, open.get(), t2, 14);
                });
                if (!disabled && chev.clicked()) {
                    open.set(!open.get());
                }
                jui.ui.Ui.popover(c, chev, open, () -> {
                    Element panel = box(c).padding(4).gap(1).minWidth(150);
                    for (MenuItem it : menu) {
                        Element r = row(c).gap(8).padding(7, 10).radius(Kit.RADIUS_XS).fillWidth().label("menu-it");
                        if (it.disabled()) {
                            r.disabled(true);
                        }
                        if (r.hovered() && !it.disabled()) {
                            r.background(c.theme().surfaceHover());
                        }
                        Color tone = it.destructive() ? KitMotion.danger(c) : c.theme().text();
                        r.children(() -> {
                            if (it.icon() != null) {
                                box(c).size(15, 15).shrink(0).draw(Icon.draw(it.icon(), tone));
                            }
                            text(c, it.label()).singleLine().fontSize(12.5f)
                                    .textColor(it.disabled() ? c.theme().textMuted() : tone);
                        });
                        if (!it.disabled() && r.clicked() && it.onSelect() != null) {
                            open.set(false);
                            it.onSelect().run();
                        }
                    }
                });
            }
        });
        hoverRef.set(hoverBox[0]);
        return host;
    }

    public static Element group(Context c, String name, Segment[] items) {
        return group(c, name, items, null, false, false, false, false);
    }

    private ButtonGroup() {}
}
