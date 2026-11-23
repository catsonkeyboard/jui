package jui.kit;

import jui.ui.Align;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's toast, after the Toast: brief confirmation for a completed
 * background action. The toast rises in on the morph run, a hairline
 * drains as its time runs out, and it leaves on the close or on its own;
 * the caller decides where it sits on screen.
 */
public final class Toast {

    public enum Kind { INFO, SUCCESS, WARNING, DANGER }

    private static final long LIFE = 4_000_000_000L;

    private static final class St {
        long born;
        boolean leaving;
        long leaveAt;
    }

    /**
     * The toast: kind, title, optional body, and an optional action row.
     * The element builds only while the caller keeps it alive; alive()
     * reports whether the time is still running.
     */
    public static Element toast(Context c, Kind kind, String title, String body,
                                String action, Runnable onAction, jui.ui.BoolRef alive) {
        Element host = box(c).label("kit-toast");
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        if (st.born == 0) {
            st.born = c.now();
        }
        long now = c.now();
        float life = 1 - (now - st.born) / (float) LIFE;
        if (st.leaving || life <= 0) {
            alive.set(false);
            return host.invisible();
        }
        c.after(java.time.Duration.ofMillis(64));
        alive.set(true);
        Kit.Tone tone = switch (kind) {
            case SUCCESS -> Kit.tone(c, Kit.Variant.SUCCESS);
            case WARNING -> Kit.tone(c, Kit.Variant.WARNING);
            case DANGER -> Kit.tone(c, Kit.Variant.DANGER);
            default -> Kit.tone(c, Kit.Variant.INFO);
        };
        String icon = switch (kind) {
            case SUCCESS -> "check";
            case WARNING -> "exclamationmark-triangle";
            case DANGER -> "xmark-circle";
            default -> "info-circle";
        };
        host.children(() -> {
            Element face = column(c).width(320).padding(10, 12).gap(4).radius(Kit.RADIUS + 2)
                    .background(c.theme().background()).border(1, Kit.hairline(c))
                    .label("kit-toast-face");
            Kit.floatShadow(face);
            float in = Kit.spring(face, "in", 1, Kit.MORPH);
            face.opacity(in).top((1 - in) * 8);
            face.children(() -> {
                row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                    box(c).size(15, 15).shrink(0).draw(Icon.draw(icon, tone.fill()));
                    text(c, title).singleLine().fontSize(12.5f).fontWeight(500).textColor(c.theme().text());
                    spacer(c).grow(1);
                    Element x = buttonBase(c).size(20, 20).radius(5).label("kit-toast-close");
                    if (x.hovered()) {
                        x.background(c.theme().surfaceHover());
                    }
                    x.children(() -> box(c).size(9, 9).shrink(0)
                            .draw(Icon.draw("xmark", c.theme().textMuted())));
                    if (x.clicked()) {
                        st.leaving = true;
                        c.invalidate();
                    }
                });
                if (body != null && !body.isEmpty()) {
                    text(c, body).fontSize(11.5f).textColor(c.theme().textMuted());
                }
                if (action != null && onAction != null) {
                    Element a = buttonBase(c).padding(2, 0).label("kit-toast-action");
                    a.children(() -> text(c, action).singleLine().fontSize(11.5f)
                            .textColor(c.theme().accent()));
                    if (a.clicked()) {
                        st.leaving = true;
                        onAction.run();
                    }
                }
                // The draining hairline.
                box(c).fillWidth().height(2).radius(1).background(c.theme().surfacePressed())
                        .children(() -> box(c).absolute().left(0).top(0).bottom(0)
                                .width(Math.max(2, host.bounds().width() * life))
                                .radius(1).background(tone.fill()));
            });
        });
        return host;
    }

    private static final class UndoSt {
        long born;
        boolean done;
    }

    /**
     * The undo toast, after the: a destructive action sits pending while
     * the toast is up — the hairline drains over the lifetime, 撤销 cancels
     * the commit, and expiring runs it once. alive reads false after either
     * ending.
     */
    public static Element undoToast(Context c, String title, String body, java.time.Duration lifetime,
                                    Runnable onUndo, Runnable commit, jui.ui.BoolRef alive) {
        Element host = box(c).label("kit-undo-toast");
        Ref<UndoSt> ref = local(host, "undo", UndoSt::new);
        UndoSt st = ref.get();
        if (st.born == 0) {
            st.born = c.now();
        }
        long now = c.now();
        float life = 1 - (now - st.born) / (float) Math.max(1, lifetime.toNanos());
        if (st.done || life <= 0) {
            if (!st.done) {
                st.done = true;
                commit.run();
            }
            alive.set(false);
            return host.invisible();
        }
        c.after(java.time.Duration.ofMillis(64));
        alive.set(true);
        Kit.Tone tone = Kit.tone(c, Kit.Variant.WARNING);
        host.children(() -> {
            Element face = column(c).width(320).padding(10, 12).gap(4).radius(Kit.RADIUS + 2)
                    .background(c.theme().background()).border(1, Kit.hairline(c))
                    .label("kit-undo-face");
            Kit.floatShadow(face);
            float in = Kit.spring(face, "in", 1, Kit.MORPH);
            face.opacity(in).top((1 - in) * 8);
            face.children(() -> {
                row(c).gap(8).alignItems(Align.CENTER).children(() -> {
                    box(c).size(15, 15).shrink(0).draw(Icon.draw("exclamationmark-triangle", tone.fill()));
                    text(c, title).singleLine().fontSize(12.5f).fontWeight(500).textColor(c.theme().text());
                    spacer(c).grow(1);
                    Element undo = buttonBase(c).padding(2, 0).label("kit-undo-action");
                    if (undo.hovered()) {
                        undo.background(c.theme().surfaceHover());
                    }
                    undo.children(() -> text(c, "撤销").singleLine().fontSize(11.5f)
                            .textColor(c.theme().accent()));
                    if (undo.clicked()) {
                        st.done = true;
                        alive.set(false);
                        if (onUndo != null) {
                            onUndo.run();
                        }
                    }
                    Element x = buttonBase(c).size(20, 20).radius(5).label("kit-undo-close");
                    x.children(() -> box(c).size(9, 9).shrink(0)
                            .draw(Icon.draw("xmark", c.theme().textMuted())));
                    if (x.clicked()) {
                        st.done = true;                              // closing confirms the commit
                    }
                });
                if (body != null && !body.isEmpty()) {
                    text(c, body).fontSize(11.5f).textColor(c.theme().textMuted());
                }
                box(c).fillWidth().height(2).radius(1).background(c.theme().surfacePressed())
                        .children(() -> box(c).absolute().left(0).top(0).bottom(0)
                                .width(Math.max(2, host.bounds().width() * life))
                                .radius(1).background(tone.fill()));
            });
        });
        return host;
    }

    private Toast() {}
}
