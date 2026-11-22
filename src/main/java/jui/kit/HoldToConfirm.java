package jui.kit;

import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's hold to confirm, after the HoldToConfirm: confirm a
 * destructive action by holding, not tapping. A danger copy of the button
 * fills from the left while it is held, drains back on a spring when let go
 * early, and the action runs only at full. A "hold to confirm" hint sits
 * under the label until the first hold begins.
 */
public final class HoldToConfirm {

    private static final class St {
        float fill;
        long doneAt;
    }

    /**
     * The button: label, hold duration in ms, disabled. The action runs
     * once per completed hold; the button rests again right after.
     */
    public static Element holdToConfirm(Context c, String label, int holdMs, boolean disabled,
                                        Runnable onConfirm) {
        Element host = row(c).label("kit-hold-confirm").alignItems(jui.ui.Align.CENTER);
        Ref<St> ref = local(host, "st", St::new);
        St st = ref.get();
        Element b = buttonBase(c).height(36).radius(18).clip().shrink(0)
                .label("kit-hold-face").background(KitMotion.surface(c)).border(1, KitMotion.danger(c));
        if (disabled) {
            b.disabled(true);
        }
        b.children(() -> {
            // The danger fill, revealed from the left as the hold runs.
            float w = KitMotion.spring(host, "fill", st.fill, KitMotion.SNAPPY);
            Element track = box(c).absolute().left(0).top(0).bottom(0);
            track.width(java.lang.Math.max(0, host.bounds().width() * w)).background(KitMotion.danger(c));
            Element l = text(c, st.fill > 0.05 ? "Keep holding" : label).singleLine().fontSize(12.5f);
            l.textColor(KitMotion.mix(c.theme().text(), Color.rgb(0xFFFFFF), st.fill));
        });
        if (!disabled && b.pressed()) {
            st.fill = Math.min(1, st.fill + 0.04f);
            c.invalidate();
        } else if (st.fill > 0) {
            if (st.fill >= 1) {
                st.fill = 0;
                if (onConfirm != null) {
                    onConfirm.run();
                }
                c.after(java.time.Duration.ofMillis(40));
            } else {
                st.fill = Math.max(0, st.fill - 0.06f); // the drain back
                c.invalidate();
            }
        }
        return b;
    }

    public static Element holdToConfirm(Context c, String label, Runnable onConfirm) {
        return holdToConfirm(c, label, 1000, false, onConfirm);
    }

    private HoldToConfirm() {}
}
