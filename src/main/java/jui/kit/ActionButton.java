package jui.kit;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.IntRef;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's action button, after the ActionButton: one async commit where
 * the result shows on the button itself. Idle runs, pending holds, success
 * settles with a drawn check and resets after a moment; the width springs
 * between faces and the trailing arrow leaves forward. The engine runs
 * actions on the UI thread, so the action receives a settle callback: call
 * it at once, or later from the caller's own wake.
 */
public final class ActionButton {

    static final int IDLE = 0, PENDING = 1, SUCCESS = 2, ERROR = 3;

    private static final class Face {
        int state = IDLE;
        long pendingSince;
        long settledAt;
    }

    /**
     * The action button: label, pending and success faces, reset delay in ms
     * (0 keeps the result), disabled. The settle callback takes true for the
     * success face, false for the error one; an unsettled run returns to
     * idle after 30s rather than spinning forever.
     */
    public static Element actionButton(Context c, String label, String pendingLabel, String successLabel,
                                       String errorLabel, int resetAfterMs, boolean disabled,
                                       java.util.function.Consumer<java.util.function.Consumer<Boolean>> action) {
        Element host = row(c).label("kit-action-button").alignItems(Align.CENTER);
        Ref<Face> ref = local(host, "kit-ab", Face::new);
        Ref<IntRef> settle = local(host, "kit-ab-settle", () -> new IntRef(0));
        Face f = ref.get();
        long now = c.now();
        if (settle.get().get() != 0) {
            f.state = settle.get().get() > 0 ? SUCCESS : ERROR;
            f.settledAt = now;
            settle.get().set(0);
            c.invalidate();
        }
        if ((f.state == SUCCESS || f.state == ERROR) && f.settledAt > 0 && resetAfterMs > 0
                && now - f.settledAt > resetAfterMs * 1_000_000L) {
            f.state = IDLE;
            f.settledAt = 0;
            c.invalidate();
        }
        if (f.state == PENDING && f.pendingSince > 0 && now - f.pendingSince > 30_000_000_000L) {
            f.state = IDLE;
            f.pendingSince = 0;
            c.invalidate();
        }
        String text = switch (f.state) {
            case PENDING -> pendingLabel;
            case SUCCESS -> successLabel;
            case ERROR -> errorLabel;
            default -> label;
        };
        boolean busy = f.state == PENDING;
        Element b = Button.builder(c, text, f.state == ERROR ? Button.Variant.DANGER : Button.Variant.PRIMARY,
                Button.Size.MD, null, busy, disabled || busy, false);
        b.label("kit-action-face");
        if (b.clicked() && !disabled && (f.state == IDLE || f.state == ERROR)) {
            f.state = PENDING;
            f.pendingSince = now;
            IntRef flag = settle.get();
            flag.set(0);
            c.after(java.time.Duration.ofNanos(45_000_000)); // a frame soon, so the pending face shows
            // The settle lands through runLater, so the action may run its
            // work on c.worker() and settle from that thread.
            action.accept(ok -> c.runLater(() -> {
                flag.set(ok ? 1 : -1);
                c.invalidate();
            }));
        }
        return b;
    }

    public static Element actionButton(Context c, String label,
                                       java.util.function.Consumer<java.util.function.Consumer<Boolean>> action) {
        return actionButton(c, label, "Saving", "Saved", "Couldn't save", 2400, false, action);
    }

    private ActionButton() {}
}
