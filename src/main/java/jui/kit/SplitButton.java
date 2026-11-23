package jui.kit;

import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's split button, after the SplitButton: one primary action joined
 * to a chevron that opens a menu of nearby alternatives. The main face
 * morphs its label in place; the chevron turns while its menu is open and
 * the menu items arrive with a short stagger.
 */
public final class SplitButton {

    /** One nearby alternative. */
    public record Item(String label, String icon, boolean destructive, boolean disabled, Runnable onSelect) {}

    /**
     * The split button: the main action, the alternatives behind the
     * chevron, and an optional leading icon that crossfades when swapped.
     */
    public static Element splitButton(Context c, String label, String icon, Button.Variant variant,
                                      boolean disabled, Item[] actions) {
        Element host = row(c).gap(0).label("kit-split-button");
        BoolRef open = localBool(host, "open", false);
        host.children(() -> {
            Element main = ButtonBase.half(c, variant, disabled, false);
            main.label("kit-split-main").padding(0, Button.padX(Button.Size.MD)).height(Button.height(Button.Size.MD));
            float dip = Kit.press(main);
            main.children(() -> {
                if (icon != null) {
                    box(c).size(Button.iconSize(Button.Size.MD), Button.iconSize(Button.Size.MD)).shrink(0)
                            .opacity(1 - 0.1f * dip).draw(Icon.draw(icon, ButtonBase.faceText(c, variant)));
                }
                text(c, label).singleLine().fontSize(Button.font(Button.Size.MD)).fontWeight(500)
                        .textColor(ButtonBase.faceText(c, variant)).opacity(1 - 0.1f * dip);
            });
            if (!disabled && main.clicked()) {
                // the main action's own feedback is the caller's concern
            }
            Element chevron = ButtonBase.half(c, variant, disabled, true);
            chevron.label("kit-split-chevron").size(28, Button.height(Button.Size.MD));
            chevron.children(() -> {
                float t = Kit.spring(chevron, "turn", open.get() ? 1 : 0, Kit.SNAPPY);
                box(c).size(14, 14).shrink(0).draw((p, b) -> {
                    float u = Math.min(b.width(), b.height()) / 16;
                    float a = (float) Math.PI * t;
                    float dy = (float) Math.cos(a) * 3 * u;
                    float dx = (float) Math.sin(a) * 3.6f * u;
                    p.line(b.x() + 4.2f * u, b.y() + (8 - dy) * u, b.x() + (4.2f + dx) * u, b.y() + 8 * u, 1.6f * u,
                            ButtonBase.faceText(c, variant));
                    p.line(b.x() + (4.2f + dx) * u, b.y() + 8 * u, b.x() + 4.2f * u, b.y() + (8 + dy) * u, 1.6f * u,
                            ButtonBase.faceText(c, variant));
                });
            });
            if (!disabled && chevron.clicked()) {
                open.set(!open.get());
            }
            // The menu: anchored under the chevron, closing on Escape or outside press.
            popover(c, chevron, open, () -> {
                jui.ui.Element panel = box(c).padding(4).gap(1).minWidth(160);
                for (int i = 0; i < actions.length; i++) {
                    Item it = actions[i];
                    Element rowEl = row(c).gap(8).padding(7, 10).radius(Kit.RADIUS_XS).label("kit-split-item")
                            .fillWidth();
                    if (it.disabled()) {
                        rowEl.disabled(true);
                    }
                    boolean hovered = rowEl.hovered();
                    if (hovered && !it.disabled()) {
                        rowEl.background(c.theme().surfaceHover());
                    }
                    Color tone = it.destructive() ? Kit.danger(c) : c.theme().text();
                    rowEl.children(() -> {
                        if (it.icon() != null) {
                            box(c).size(15, 15).shrink(0).draw(Icon.draw(it.icon(), tone));
                        }
                        text(c, it.label()).singleLine().fontSize(12.5f).textColor(it.disabled()
                                ? c.theme().textMuted() : tone);
                    });
                    if (!it.disabled() && rowEl.clicked() && it.onSelect() != null) {
                        open.set(false);
                        it.onSelect().run();
                    }
                }
            });
        });
        return host;
    }

    public static Element splitButton(Context c, String label, Item[] actions) {
        return splitButton(c, label, null, Button.Variant.PRIMARY, false, actions);
    }

    private SplitButton() {}
}
