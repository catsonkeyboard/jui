package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's confirm morph, after the ConfirmMorph: a destructive button
 * that morphs in place into an inline question, then a spinner, then a
 * result with undo. Only the pill's width moves; every face sits at its
 * natural width against the pill's leading edge. Answer it with a press, a
 * press and hold that fills, or a countdown that can still be stopped.
 */
public final class ConfirmMorph {

    private static final int IDLE = 0, ASK = 1, HOLD = 2, PENDING = 3, DONE = 4, ERROR = 5, UNDOING = 6;

    public enum Mode { PRESS, HOLD, COUNTDOWN }

    private static final class St {
        int state = IDLE;
        long at;          // when the current state began
        float held;       // hold fill 0..1
    }

    private static float textWidth(Context c, String s, float size) {
        return s == null || s.isEmpty() ? 0 : c.layoutText(s, size, 500, 4096).width;
    }

    private record Face(String text, Color fill, Color text2, String icon, float h) {}

    /**
     * The control. confirmMode picks press, hold (holdDuration ms) or
     * countdown (countdown seconds). The work settles through the settle
     * callback (true for done, false for the error face); undo runs when
     * the result offers it.
     */
    public static Element confirmMorph(Context c, String label, String icon, String prompt, String confirmLabel,
                                       String cancelLabel, String pendingLabel, String doneLabel,
                                       String errorLabel, String undoLabel, Mode mode, int holdDurationMs,
                                       int countdown, boolean danger, boolean disabled,
                                       java.util.function.Consumer<java.util.function.Consumer<Boolean>> onConfirm,
                                       Runnable onUndo, Runnable onCancel) {
        float h = 32, fs = 12.5f, pad = 12;
        Element host = row(c).label("kit-confirm-morph").alignItems(Align.CENTER);
        Ref<St> ref = local(host, "st", St::new);
        Ref<jui.ui.IntRef> settle = local(host, "kit-cm-settle", () -> new jui.ui.IntRef(0));
        St st = ref.get();
        long now = c.now();
        if (st.state == PENDING && settle.get().get() != 0) {
            st.state = settle.get().get() > 0 ? DONE : ERROR;
            st.at = now;
            settle.get().set(0);
            c.invalidate();
        }
        String ask = prompt != null ? prompt : label + "?";
        // Face widths, measured, drive the pill's one moving edge.
        float wIdle = pad * 2 + textWidth(c, label, fs) + (icon != null ? 16 + 7 : 0);
        float wAsk = pad * 2 + textWidth(c, ask, fs) + 7 + textWidth(c, confirmLabel, 11.5f) + 16
                + textWidth(c, cancelLabel, 11.5f) + 10;
        float wPending = pad * 2 + textWidth(c, pendingLabel, fs) + 16 + 7;
        float wDone = pad * 2 + textWidth(c, doneLabel, fs) + 7 + textWidth(c, undoLabel, 11.5f) + 14;
        float wError = pad * 2 + textWidth(c, errorLabel, fs) + 7 + textWidth(c, "Retry", 11.5f) + 14;
        float target = switch (st.state) {
            case ASK, HOLD -> wAsk;
            case PENDING, UNDOING -> wPending;
            case DONE -> wDone;
            case ERROR -> wError;
            default -> wIdle;
        };
        float w = KitMotion.spring(host, "w", target, st.state >= PENDING ? KitMotion.MORPH : KitMotion.SMOOTH);
        Color dangerFill = KitMotion.danger(c);
        Color accent = c.theme().accent();
        Color confirmFill = danger ? dangerFill : accent;
        Element pill = row(c).height(h).width(w).radius(h / 2).shrink(0).clip()
                .label("kit-confirm-pill").background(st.state == ASK || st.state == HOLD
                        ? KitMotion.surface(c) : st.state == DONE ? KitMotion.mix(KitMotion.success(c), Color.rgb(0xFFFFFF), 0.92f)
                        : KitMotion.surface(c));
        pill.border(1, st.state == IDLE || st.state == ASK || st.state == HOLD
                ? KitMotion.hairline(c) : st.state == DONE ? KitMotion.success(c) : KitMotion.hairline(c));
        if (disabled && st.state == IDLE) {
            pill.disabled(true);
        }
        pill.children(() -> {
            switch (st.state) {
                case IDLE -> {
                    float dip = KitMotion.press(pill);
                    if (icon != null) {
                        box(c).size(16, 16).shrink(0).opacity(1 - 0.1f * dip)
                                .draw(Icon.draw(icon, c.theme().text()));
                    }
                    Element l = text(c, label).singleLine().fontSize(fs).opacity(1 - 0.1f * dip);
                    l.textColor(c.theme().text());
                    if (pill.clicked() && !disabled) {
                        st.state = mode == Mode.HOLD ? HOLD : ASK;
                        st.at = now;
                        c.invalidate();
                    }
                }
                case ASK -> {
                    Element q = text(c, ask).singleLine().fontSize(fs);
                    q.textColor(c.theme().text());
                    spacer(c).width(7);
                    Element ok = buttonBase(c).height(22).padding(0, 8).radius(11).shrink(0)
                            .label("kit-confirm-ok").background(confirmFill);
                    ok.children(() -> text(c, confirmLabel).singleLine().fontSize(11.5f)
                            .textColor(Color.rgb(0xFFFFFF)));
                    if (ok.clicked()) {
                        begin(c, host, st, pendingLabel, onConfirm);
                    }
                    spacer(c).width(6);
                    Element no = buttonBase(c).height(22).padding(0, 8).radius(11).shrink(0)
                            .label("kit-confirm-no");
                    no.children(() -> text(c, cancelLabel).singleLine().fontSize(11.5f)
                            .textColor(c.theme().textMuted()));
                    if (no.hovered()) {
                        no.background(c.theme().surfaceHover());
                    }
                    if (no.clicked()) {
                        st.state = IDLE;
                        if (onCancel != null) {
                            onCancel.run();
                        }
                        c.invalidate();
                    }
                    // An unanswered question returns to rest after 6s.
                    if (now - st.at > 6_000_000_000L) {
                        st.state = IDLE;
                        c.invalidate();
                    }
                }
                case HOLD -> {
                    // A solid danger copy reveals from the left as the hold fills.
                    Element q = text(c, ask).singleLine().fontSize(fs);
                    q.textColor(c.theme().textMuted());
                    spacer(c).width(7);
                    Element ok = buttonBase(c).height(22).width(textWidth(c, confirmLabel, 11.5f) + 16)
                            .radius(11).shrink(0).clip().label("kit-confirm-hold").background(c.theme().surface());
                    float held = KitMotion.spring(host, "hold", st.held, Duration0());
                    ok.children(() -> {
                        box(c).absolute().left(0).top(0).bottom(0).width(textWidth(c, confirmLabel, 11.5f) + 16)
                                .background(dangerFill).opacity(held);
                        Element t = text(c, confirmLabel).singleLine().fontSize(11.5f);
                        t.textColor(KitMotion.mix(c.theme().text(), Color.rgb(0xFFFFFF), held));
                    });
                    st.held = ok.pressed() ? Math.min(1, st.held + 0.05f) : Math.max(0, st.held - 0.12f);
                    if (st.held >= 1) {
                        begin(c, host, st, pendingLabel, onConfirm);
                    } else if (!ok.pressed() && st.held <= 0 && now - st.at > 6_000_000_000L) {
                        st.state = IDLE;
                        c.invalidate();
                    }
                }
                case PENDING, UNDOING -> {
                    box(c).size(14, 14).shrink(0).draw(Spinner.draw(c));
                    Element l = text(c, st.state == PENDING ? pendingLabel : undoLabel).singleLine().fontSize(fs);
                    l.textColor(c.theme().textMuted());
                }
                case DONE -> {
                    IconMorph.icon(c, IconMorph.COPY, true, KitMotion.success(c), 16);
                    Element l = text(c, doneLabel).singleLine().fontSize(fs);
                    l.textColor(KitMotion.success(c));
                    spacer(c).width(7);
                    Element undo = buttonBase(c).height(22).padding(0, 7).radius(11).shrink(0)
                            .label("kit-confirm-undo");
                    undo.children(() -> text(c, undoLabel).singleLine().fontSize(11.5f)
                            .textColor(c.theme().textMuted()));
                    if (undo.hovered()) {
                        undo.background(c.theme().surfaceHover());
                    }
                    if (undo.clicked() && onUndo != null) {
                        st.state = UNDOING;
                        st.at = now;
                        onUndo.run();
                        c.after(java.time.Duration.ofNanos(120_000_000));
                        c.invalidate();
                    }
                    if (now - st.at > 5_000_000_000L) {
                        st.state = IDLE;
                        c.invalidate();
                    }
                }
                case ERROR -> {
                    Element l = text(c, errorLabel).singleLine().fontSize(fs);
                    l.textColor(KitMotion.danger(c));
                    spacer(c).width(7);
                    Element retry = buttonBase(c).height(22).padding(0, 7).radius(11).shrink(0)
                            .label("kit-confirm-retry").background(KitMotion.surface(c));
                    retry.border(1, KitMotion.hairline(c));
                    retry.children(() -> text(c, "Retry").singleLine().fontSize(11.5f)
                            .textColor(c.theme().text()));
                    if (retry.clicked()) {
                        begin(c, host, st, pendingLabel, onConfirm);
                    }
                }
                default -> { }
            }
        });
        if (st.state == UNDOING && now - st.at > 120_000_000L) {
            st.state = IDLE;
            c.invalidate();
        }
        return pill;
    }

    private static void begin(Context c, Element host, St st, String pendingLabel,
                              java.util.function.Consumer<java.util.function.Consumer<Boolean>> onConfirm) {
        st.state = PENDING;
        st.at = c.now();
        Ref<jui.ui.IntRef> settle = local(host, "kit-cm-settle", () -> new jui.ui.IntRef(0));
        settle.get().set(0);
        if (onConfirm != null) {
            jui.ui.IntRef flag = settle.get();
            c.after(java.time.Duration.ofNanos(60_000_000));
            // The settle lands through runLater, so the work may run on
            // c.worker() and settle from that thread.
            onConfirm.accept(ok -> c.runLater(() -> {
                flag.set(ok ? 1 : -1);
                c.invalidate();
            }));
        } else {
            settle.get().set(1); // no work: settle at once
        }
        c.invalidate();
    }

    private static java.time.Duration Duration0() {
        return KitMotion.SNAPPY;
    }

    public static Element confirmMorph(Context c, String label,
                                       java.util.function.Consumer<java.util.function.Consumer<Boolean>> onConfirm) {
        return confirmMorph(c, label, "trash", null, "Delete", "Cancel", "Deleting", "Deleted",
                "Couldn't finish", "Undo", Mode.PRESS, 1000, 3, true, false, onConfirm, null, null);
    }

    private ConfirmMorph() {}
}
