package jui.kit;

import java.util.List;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's multi-select, after the MultiSelect: select several values
 * while keeping the field readable. Chips of the picked values sit inside
 * the trigger; the menu rows carry checkboxes, "Clear" resets, and a
 * count line summarizes when nothing is picked.
 */
public final class MultiSelect {

    private static final class St {
        final java.util.ArrayList<Integer> picked = new java.util.ArrayList<>();
    }

    /** The trigger + checkbox menu over options; picks land in the caller's list ref. */
    public static Element multiSelect(Context c, String label, String placeholder, String[] options,
                                      Ref<List<Integer>> picked, BoolRef open, boolean disabled, float width) {
        Element host = column(c).gap(4).label("kit-multi-" + label);
        host.children(() -> {
            if (label != null && !label.isEmpty()) {
                text(c, label).fontSize(12).fontWeight(500).textColor(c.theme().text());
            }
            List<Integer> got = picked.get();
            boolean has = got != null && !got.isEmpty();
            Element trigger = buttonBase(c).width(width).height(has ? 38 : 34).padding(0, 7).gap(4)
                    .radius(Kit.RADIUS_XS + 2).justify(jui.ui.Justify.START).label("kit-multi-trigger");
            if (disabled) {
                trigger.disabled(true);
            } else {
                trigger.background(Kit.surface(c));
                if (trigger.hovered() || open.get()) {
                    trigger.background(c.theme().surfaceHover());
                }
                trigger.border(1, open.get() ? c.theme().accent() : Kit.hairline(c));
            }
            trigger.children(() -> {
                if (!has) {
                    text(c, placeholder == null ? "Select" : placeholder).singleLine().fontSize(12.5f)
                            .textColor(c.theme().textMuted()).padding(0, 4);
                    spacer(c).grow(1);
                    IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 14);
                } else {
                    int shown = 0;
                    for (int idx : got) {
                        if (idx >= 0 && idx < options.length && shown < 2) {
                            Element chip = row(c).gap(3).padding(2, 6).radius(Kit.RADIUS_XS).shrink(0)
                                    .background(c.theme().surface()).label("kit-multi-chip");
                            chip.children(() -> {
                                text(c, options[idx]).singleLine().fontSize(11)
                                        .textColor(c.theme().text());
                            });
                            shown++;
                        }
                    }
                    if (got.size() > 2) {
                        text(c, "+" + (got.size() - 2)).singleLine().fontSize(11)
                                .textColor(c.theme().textMuted()).padding(0, 2);
                    }
                    spacer(c).grow(1);
                    IconMorph.icon(c, IconMorph.CHEVRON, open.get(), c.theme().textMuted(), 14);
                }
            });
            if (!disabled && trigger.clicked()) {
                open.set(!open.get());
            }
            popover(c, trigger, open, () -> {
                Element panel = box(c).padding(4).gap(1).minWidth(width).maxHeight(260).clip()
                        .label("kit-multi-menu");
                row(c).padding(2, 6).children(() -> {
                    text(c, got == null ? "0 selected" : got.size() + " selected").fontSize(10.5f)
                            .textColor(c.theme().textMuted());
                    spacer(c).grow(1);
                    Element clear = buttonBase(c).label("kit-multi-clear").padding(2, 6).radius(4);
                    clear.children(() -> text(c, "Clear").singleLine().fontSize(10.5f)
                            .textColor(c.theme().accent()));
                    if (clear.clicked()) {
                        picked.set(new java.util.ArrayList<>());
                        c.invalidate();
                    }
                });
                divider(c).margin(3, 4);
                for (int i = 0; i < options.length; i++) {
                    int ii = i;
                    boolean on = got != null && got.contains(ii);
                    Element r = row(c).gap(8).padding(6, 8).radius(Kit.RADIUS_XS).fillWidth()
                            .label("kit-multi-row");
                    if (r.hovered()) {
                        r.background(c.theme().surfaceHover());
                    }
                    r.children(() -> {
                        Element cb = box(c).size(15, 15).radius(4).shrink(0);
                        if (on) {
                            cb.background(c.theme().accent());
                            cb.children(() -> box(c).size(13, 13).shrink(0)
                                    .draw(Icon.draw("check", Color.rgb(0xFFFFFF))));
                        } else {
                            cb.background(c.theme().background()).border(1, Kit.hairline(c));
                        }
                        text(c, options[ii]).singleLine().fontSize(12.5f).textColor(c.theme().text());
                    });
                    if (r.clicked()) {
                        java.util.ArrayList<Integer> next = new java.util.ArrayList<>(got == null
                                ? java.util.List.of() : got);
                        if (on) {
                            next.remove((Integer) ii);
                        } else {
                            next.add(ii);
                        }
                        picked.set(next);
                        c.invalidate();
                    }
                }
            });
        });
        return host;
    }

    /** A multi-select keeping its picks in local state. */
    public static Element multiSelect(Context c, String[] options) {
        Element host = box(c).invisible();
        Ref<List<Integer>> picked = local(host, "picked",
                () -> new java.util.ArrayList<Integer>());
        BoolRef open = localBool(host, "open", false);
        return multiSelect(c, null, "Select", options, picked, open, false, 200);
    }

    private MultiSelect() {}
}
