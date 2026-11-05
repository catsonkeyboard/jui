package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;

import static jui.ui.Ui.*;

/**
 * The kit's select: a closed trigger opening a single-choice list below, the
 * selection held by an IntRef — gpui-kit's Select over the kit's dropdown.
 */
public final class Select {
    public static Element select(Context c, jui.ui.IntRef selected, jui.ui.BoolRef open,
                                 String... options) {
        String current = selected.get() >= 0 && selected.get() < options.length
                ? options[selected.get()] : "请选择";
        Element anchor = buttonBase(c).fillWidth().height(28).padding(0, 10).radius(Kit.RADIUS_SM)
                .border(1, Kit.line(c))
                .label("kit-select-" + current)
                .justify(Justify.START).gap(6)
                .children(() -> {
                    text(c, current).fontSize(12).textColor(c.theme().text());
                    spacer(c).grow(1);
                    box(c).size(12, 12).draw(Icon.draw("chevron-down", c.theme().textMuted()));
                });
                if (anchor.clicked()) open.set(true);
        popover(c, anchor, open, () -> column(c).width(180).padding(4).gap(2).children(() -> {
            for (int i = 0; i < options.length; i++) {
                int idx = i;
                boolean on = idx == selected.get();
                Element b = buttonBase(c).fillWidth().height(26).padding(0, 10).radius(Kit.RADIUS_XS)
                        .label("kit-select-opt-" + options[idx])
                        .justify(Justify.START).gap(6)
                        .children(() -> {
                            box(c).size(12, 12).draw(Icon.draw(on ? "check" : "circle",
                                    on ? c.theme().accent() : c.theme().border()));
                            text(c, options[idx]).fontSize(12).textColor(c.theme().text());
                        });
                if (b.hovered()) b.background(c.theme().surfaceHover());
                if (b.clicked()) {
                    selected.set(idx);
                    open.set(false);
                }
            }
        }));
        return anchor;
    }

    private Select() {}
}
