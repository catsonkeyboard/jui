package jui.kit;

import java.util.ArrayList;
import java.util.List;

import jui.ui.Align;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Ref;

import static jui.ui.Ui.*;

/**
 * The kit's toast stack, after the ToastStack: stack short results at the
 * edge until you reach for them. New toasts rise in under the older ones,
 * each leaves on its own time or its close, and the caller pushes and
 * clears through the list ref it keeps.
 */
public final class ToastStack {

    /** One queued toast. */
    public record Note(Toast.Kind kind, String title, String body) {}

    /**
     * The stack: a column at the caller's corner. Toasts whose alive ref
     * went false drop out of the list; at most four show.
     */
    public static Element stack(Context c, Ref<List<Note>> notes) {
        Element host = column(c).gap(8).alignItems(Align.END).label("kit-toast-stack");
        List<Note> list = notes.get();
        if (list == null || list.isEmpty()) {
            return host;
        }
        List<Note> keep = new ArrayList<>();
        int shown = Math.min(4, list.size());
        for (int i = 0; i < shown; i++) {
            Note n = list.get(i);
            int idx = i;
            host.children(() -> {
                jui.ui.BoolRef alive = localBool(host, "alive" + idx, true);
                Toast.toast(c, n.kind(), n.title(), n.body(), null, null, alive);
            });
            keep.add(n);
        }
        if (keep.size() != list.size()) {
            notes.set(keep);
        }
        return host;
    }

    /** A stack keeping its own list in local state. */
    public static Element stack(Context c) {
        Element host = box(c).invisible();
        Ref<List<Note>> notes = local(host, "notes", java.util.ArrayList::new);
        return stack(c, notes);
    }

    private ToastStack() {}
}
