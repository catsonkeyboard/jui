package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's chip group, after the ChipGroup: filter by a few facets with
 * chips that morph as you pick them. A picked chip fills with the accent's
 * tint and grows a check; an unpicked one stays quiet with a hairline. An
 * optional "All" chip resets, and the counts roll with the picks.
 */
public final class ChipGroup {

    /** One chip's facts: its label and its live count. */
    public record Chip(String label, int count) {}

    /** Single or multiple picks over the caller's ref. */
    public static Element chipGroup(Context c, Chip[] chips, Ref<int[]> picked, boolean multiple) {
        Element host = row(c).gap(6).wrap().alignContent(Align.START).label("kit-chip-group");
        int[] sel = picked.get();
        boolean all = sel == null || sel.length == 0;
        // "All" resets when any chip is picked.
        Element allChip = buttonBase(c).height(28).padding(0, 10).radius(14).shrink(0)
                .label("kit-chip-all");
        if (all) {
            allChip.background(c.theme().accent());
            allChip.children(() -> text(c, "All").singleLine().fontSize(11.5f)
                    .textColor(c.theme().accentText()));
        } else {
            allChip.background(Kit.surface(c)).border(1, Kit.hairline(c));
            allChip.children(() -> text(c, "All").singleLine().fontSize(11.5f)
                    .textColor(c.theme().textMuted()));
            if (allChip.hovered()) {
                allChip.background(c.theme().surfaceHover());
            }
            if (allChip.clicked()) {
                picked.set(new int[0]);
                c.invalidate();
            }
        }
        for (int i = 0; i < chips.length; i++) {
            int ii = i;
            boolean isOn = false;
            if (sel != null) {
                for (int s : sel) {
                    isOn |= s == ii;
                }
            }
            final boolean on = isOn;
            Chip ch = chips[ii];
            Element chip = buttonBase(c).height(28).padding(0, 10).gap(5).radius(14).shrink(0)
                    .label("kit-chip-" + ch.label());
            if (on) {
                chip.background(Color.rgba(c.theme().accent().rgba() >>> 8, 0.16f))
                        .border(1, c.theme().accent());
            } else {
                chip.background(Kit.surface(c)).border(1, Kit.hairline(c));
                if (chip.hovered()) {
                    chip.background(c.theme().surfaceHover());
                }
            }
            chip.children(() -> {
                text(c, ch.label()).singleLine().fontSize(11.5f)
                        .textColor(on ? c.theme().accent() : c.theme().text());
                if (ch.count() >= 0) {
                    Element n = text(c, String.valueOf(ch.count())).singleLine().fontSize(10.5f);
                    n.textColor(on ? c.theme().accent() : c.theme().textMuted());
                    n.opacity(0.75f + 0.25f * Kit.spring(host, "cnt" + ii, ch.count(), Kit.MORPH));
                }
                if (on) {
                    box(c).size(11, 11).shrink(0).draw(Icon.draw("check", c.theme().accent()));
                }
            });
            if (chip.clicked()) {
                if (multiple) {
                    int[] next = new int[on ? sel.length - 1 : sel.length + 1];
                    int at = 0;
                    for (int s : sel) {
                        if (s != ii) {
                            next[at++] = s;
                        }
                    }
                    if (!on) {
                        next[at] = ii;
                    }
                    picked.set(next);
                } else {
                    picked.set(on ? new int[0] : new int[]{ii});
                }
                c.invalidate();
            }
        }
        return host;
    }

    /** A chip group keeping its picks in local state. */
    public static Element chipGroup(Context c, Chip[] chips) {
        Element host = box(c).invisible();
        Ref<int[]> picked = local(host, "pick", () -> new int[0]);
        return chipGroup(c, chips, picked, false);
    }

    private ChipGroup() {}
}
