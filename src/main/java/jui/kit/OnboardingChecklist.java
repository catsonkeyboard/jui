package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;

import static jui.ui.Ui.*;

/**
 * The kit's onboarding checklist, after the OnboardingChecklist: guide a
 * new workspace through setup, one step at a time. Done steps keep a drawn
 * check and a struck line; the current one carries the accent dot and its
 * action, and a hairline connects the dots down the list.
 */
public final class OnboardingChecklist {

    /** One step: its title, whether done, and the action it opens. */
    public record Step(String title, boolean done, Runnable onOpen) {}

    /** The checklist: the steps in order. */
    public static Element checklist(Context c, Step[] steps) {
        int doneCount = 0;
        for (Step s : steps) {
            if (s.done()) {
                doneCount++;
            }
        }
        final int done = doneCount;
        Element host = column(c).fillWidth().gap(0).label("kit-onboarding");
        host.children(() -> {
            row(c).gap(8).alignItems(Align.CENTER).padding(0, 0, 10, 0).children(() -> {
                text(c, "Get started").singleLine().fontSize(12.5f).fontWeight(500)
                        .textColor(c.theme().text());
                spacer(c).grow(1);
                text(c, done + " of " + steps.length).singleLine().fontSize(11)
                        .textColor(c.theme().textMuted());
            });
            box(c).fillWidth().height(4).radius(2).background(c.theme().surfacePressed())
                    .clip().children(() -> {
                        float w = host.bounds().width();
                        float fill = Kit.spring(host, "prog", steps.length > 0 ? done / (float) steps.length : 0,
                                Kit.MORPH);
                        box(c).absolute().left(0).top(0).bottom(0).width(Math.max(2, w * fill))
                                .radius(2).background(c.theme().accent());
                    });
            for (int i = 0; i < steps.length; i++) {
                int ii = i;
                Step s = steps[ii];
                row(c).fillWidth().gap(10).padding(10, 0).alignItems(Align.CENTER).children(() -> {
                    // The dot, drawn or hollow, on a hairline spine.
                    Element dot = box(c).size(18, 18).radius(9).shrink(0)
                            .alignItems(Align.CENTER);
                    float fill = Kit.spring(host, "d" + ii, s.done() ? 1 : 0, Kit.SNAPPY);
                    dot.background(Color_r(c, c.theme().accent(), fill));
                    dot.border(1, s.done() ? c.theme().accent() : Kit.hairline(c));
                    dot.children(() -> {
                        if (fill > 0.3f) {
                            box(c).size(11, 11).shrink(0).opacity(fill)
                                    .draw(Icon.draw("check", c.theme().accent()));
                        } else if (ii == firstOpen(steps)) {
                            box(c).size(7, 7).shrink(0).radius(4).background(c.theme().accent());
                        }
                    });
                    column(c).gap(0).grow(1).children(() -> {
                        Element t = text(c, s.title()).singleLine().fontSize(12);
                        t.textColor(s.done() ? c.theme().textMuted() : c.theme().text());
                    });
                    if (!s.done() && ii == firstOpen(steps) && s.onOpen() != null) {
                        Element go = buttonBase(c).height(24).padding(0, 9).radius(12)
                                .background(c.theme().accent()).label("kit-onboard-go");
                        go.children(() -> text(c, "Start").singleLine().fontSize(10.5f)
                                .textColor(c.theme().accentText()));
                        if (go.clicked()) {
                            s.onOpen().run();
                        }
                    } else if (s.done()) {
                        box(c).size(13, 13).shrink(0)
                                .draw(Icon.draw("check", Kit.success(c)));
                    }
                });
            }
        });
        return host;
    }

    private static int firstOpen(Step[] steps) {
        for (int i = 0; i < steps.length; i++) {
            if (!steps[i].done()) {
                return i;
            }
        }
        return -1;
    }

    private static jui.ui.Color Color_r(Context c, jui.ui.Color base, float a) {
        return jui.ui.Color.rgba(base.rgba() >>> 8, a);
    }

    private OnboardingChecklist() {}
}
