package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Justify;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's command palette: a trigger button opening a filter panel — type
 * to filter, click a command to run it. The pick lands in pick as the
 * command index, once, after gpui-kit's Command.
 */
public final class Command {
    /** One command's label. */
    public record Cmd(String label) {}

    public static Cmd cmd(String label) {
        return new Cmd(label);
    }

    public static Element palette(Context c, String triggerLabel, jui.ui.BoolRef open,
                                  TextRef query, java.util.function.IntConsumer pick, Cmd... commands) {
        Element anchor = Button.button(c, triggerLabel, Kit.Variant.DEFAULT, Kit.Size.MEDIUM,
                "bolt", false, false, false, false, true);
        popover(c, anchor, open, () -> {
            List<Cmd> matches = new ArrayList<>();
            String q = query.get().toLowerCase();
            for (Cmd cmd : commands) {
                if (q.isEmpty() || cmd.label().toLowerCase().contains(q)) matches.add(cmd);
            }
            column(c).width(220).gap(6).padding(8).children(() -> {
                textInput(c, query).placeholder("输入命令…").fillWidth().label("kit-command-input");
                for (int i = 0; i < Math.min(6, matches.size()); i++) {
                    int idx = i;
                    Element b = buttonBase(c).fillWidth().height(26).padding(0, 8).radius(Kit.RADIUS_XS)
                            .label("kit-command-" + matches.get(idx).label())
                            .justify(Justify.START).gap(6)
                            .children(() -> {
                                box(c).size(12, 12).draw(Icon.draw("bolt", c.theme().accent()));
                                text(c, matches.get(idx).label()).fontSize(12).textColor(c.theme().text());
                            });
                    if (b.hovered()) b.background(c.theme().surfaceHover());
                    if (b.clicked()) {
                        open.set(false);
                        query.set("");
                        pick.accept(idx);
                    }
                }
                if (matches.isEmpty()) {
                    text(c, "无匹配命令").fontSize(11).textColor(c.theme().textMuted());
                }
            });
        });
        return anchor;
    }

    private Command() {}
}
