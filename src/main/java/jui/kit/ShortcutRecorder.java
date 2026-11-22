package jui.kit;

import jui.ui.Align;
import jui.ui.BoolRef;
import jui.ui.Color;
import jui.ui.Context;
import jui.ui.Element;
import jui.ui.Key;
import jui.ui.Mod;
import jui.ui.Shortcut;
import jui.ui.TextKey;
import jui.ui.TextRef;

import static jui.ui.Ui.*;

/**
 * The kit's shortcut recorder, after the ShortcutRecorder: record key
 * combinations into key caps. Click to arm, press the combination, and it
 * lands as Kbd caps with a conflict note when the combination is already
 * taken; Escape clears back to the resting face.
 */
public final class ShortcutRecorder {

    /** Names a key the way a key cap reads it. */
    public static String name(Key k) {
        return switch (k) {
            case ENTER -> "Enter";
            case TAB -> "Tab";
            case SPACE -> "Space";
            case BACKSPACE -> "Backspace";
            case ESCAPE -> "Esc";
            case DELETE -> "Del";
            case INSERT -> "Ins";
            case HOME -> "Home";
            case END -> "End";
            case PAGE_UP -> "PageUp";
            case PAGE_DOWN -> "PageDown";
            case LEFT -> "←";
            case RIGHT -> "→";
            case UP -> "↑";
            case DOWN -> "↓";
            case MINUS -> "-";
            case EQUAL -> "=";
            case COMMA -> ",";
            case PERIOD -> ".";
            case SLASH -> "/";
            case SEMICOLON -> ";";
            case QUOTE -> "'";
            case LEFT_BRACKET -> "[";
            case RIGHT_BRACKET -> "]";
            case BACKSLASH -> "\\";
            case BACKQUOTE -> "`";
            case CONTEXT_MENU -> "Menu";
            default -> k.name();
        };
    }

    /** The modifier prefix: ⌘⇧⌥⌃. */
    public static String mods(int m) {
        StringBuilder s = new StringBuilder();
        if ((m & Mod.CMD) != 0) {
            s.append('⌘');
        }
        if ((m & Mod.SHIFT) != 0) {
            s.append('⇧');
        }
        if ((m & Mod.ALT) != 0) {
            s.append('⌥');
        }
        if ((m & Mod.CTRL) != 0) {
            s.append('⌃');
        }
        return s.toString();
    }

    /** The recorded combination, or null. */
    public static class Capture {
        public int mods;
        public Key key;
    }

    /**
     * The recorder: label, the ref holding the current combination as text,
     * the taken combinations for the conflict note, and a disabled state.
     * The consumer receives what was recorded; return false to reject it
     * and show the conflict face.
     */
    public static Element recorder(Context c, String label, TextRef recorded, boolean recording,
                                   BoolRef armed, java.util.function.BiFunction<Integer, Key, Boolean> take,
                                   boolean disabled) {
        Element host = column(c).fillWidth().gap(4).label("kit-shortcut-" + label);
        host.children(() -> {
            if (label != null && !label.isEmpty()) {
                text(c, label).fontSize(12).fontWeight(500).textColor(c.theme().text());
            }
            Element face = buttonBase(c).fillWidth().height(34).padding(0, 10).gap(6).radius(Kit.RADIUS_XS + 2)
                    .label("kit-shortcut-face").justify(jui.ui.Justify.START);
            if (armed.get() && recording) {
                face.background(c.theme().background()).border(1, c.theme().accent());
                c.after(java.time.Duration.ofMillis(16));
            } else {
                face.background(Kit.surface(c)).border(1, Kit.hairline(c));
                if (face.hovered() && !disabled) {
                    face.background(c.theme().surfaceHover());
                }
            }
            if (disabled) {
                face.disabled(true);
            }
            face.children(() -> {
                if (armed.get() && recording) {
                    box(c).size(13, 13).shrink(0).draw(Spinner.draw(c));
                    text(c, "Press keys…").singleLine().fontSize(12).textColor(c.theme().textMuted());
                } else {
                    String rec = recorded.get() == null ? "" : recorded.get();
                    if (rec.isEmpty()) {
                        text(c, "Record shortcut").singleLine().fontSize(12)
                                .textColor(c.theme().textMuted());
                        spacer(c).grow(1);
                        box(c).size(14, 14).shrink(0).draw(Icon.draw("bolt", c.theme().textMuted()));
                    } else {
                        String[] caps = rec.split("\\s*(?=[⌘⇧⌥⌃])");
                        for (String cap : caps.length == 0 ? new String[]{rec} : caps) {
                            Kbd.kbd(c, cap);
                        }
                        spacer(c).grow(1);
                        box(c).size(13, 13).shrink(0).draw(Icon.draw("xmark", c.theme().textMuted()));
                    }
                }
            });
            if (!disabled && face.clicked()) {
                armed.set(true);
                c.invalidate();
            }
        });
        // The capture rides a thin, focused element that registers every
        // modifier combination, so function keys, arrows and the rest all
        // land, not only the ones a text input sees.
        if (armed.get()) {
            Element listener = box(c).size(1, 1).opacity(0).focusable()
                    .label("kit-shortcut-listen");
            listener.autoFocus();
            boolean captured = false;
            for (Shortcut s : ALL_COMBOS) {
                if (listener.shortcut(s)) {
                    if (s.key() == Key.ESCAPE) {
                        armed.set(false);
                    } else if (s.key() == Key.TAB || s.key() == Key.BACKSPACE) {
                        recorded.set("");
                        armed.set(false);
                    } else {
                        boolean ok = take == null || take.apply(s.mods(), s.key());
                        if (ok) {
                            recorded.set((mods(s.mods()) + " " + name(s.key())).trim());
                        }
                        armed.set(false);
                    }
                    captured = true;
                    break;
                }
            }
            if (captured) {
                c.invalidate();
            }
        }
        return host;
    }

    /** Every combination the recorder listens for while armed: 12 modifier sets over all keys. */
    private static final Shortcut[] ALL_COMBOS = buildAll();

    private static Shortcut[] buildAll() {
        int[] modsList = {0, Mod.SHIFT, Mod.CTRL, Mod.ALT, Mod.CMD,
                Mod.SHIFT | Mod.CTRL, Mod.SHIFT | Mod.ALT, Mod.SHIFT | Mod.CMD,
                Mod.CTRL | Mod.ALT, Mod.CTRL | Mod.CMD, Mod.ALT | Mod.CMD,
                Mod.SHIFT | Mod.CTRL | Mod.ALT};
        Key[] keys = Key.values();
        Shortcut[] all = new Shortcut[modsList.length * keys.length];
        int at = 0;
        for (int m : modsList) {
            for (Key k : keys) {
                all[at++] = Shortcut.of(m, k);
            }
        }
        return all;
    }

    /** A recorder that keeps its own state, reporting through the text ref. */
    public static Element recorder(Context c, String label, TextRef recorded) {
        Element host = box(c).invisible();
        BoolRef armed = localBool(host, "armed", false);
        return recorder(c, label, recorded, armed.get(), armed, null, false);
    }

    private ShortcutRecorder() {}
}
