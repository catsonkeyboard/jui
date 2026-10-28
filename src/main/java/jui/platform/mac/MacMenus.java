package jui.platform.mac;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import jui.ffi.ObjC;
import jui.ffi.ObjCClass;
import jui.platform.MenuModel;
import jui.ui.Key;
import jui.ui.Mod;
import jui.ui.Shortcut;

final class MacMenus {
    private static final Map<Long, Popup> OWNERS = new HashMap<>();
    private static long targetClass;
    private Popup active;
    private boolean closed;

    private static long targetClass() {
        if (targetClass == 0) {
            targetClass = ObjCClass.define("JUIContextMenuTarget", "NSObject")
                    .method1("juiContextMenuItem:", (self, command, sender) -> {
                        Popup popup = OWNERS.get(self);
                        if (popup != null && !popup.cancelled && popup.chosen == 0) {
                            int id = (int) ObjC.send(sender, "tag");
                            MenuModel.Item item = popup.model.find(id);
                            if (item != null && item.enabled() && item.kind() == MenuModel.Kind.ITEM) popup.chosen = id;
                        }
                    }).register();
        }
        return targetClass;
    }

    static int owners() { return OWNERS.size(); }

    static Popup create(MenuModel model) { return new Popup(model); }

    void popup(MenuModel model, float x, float y, long view, IntConsumer result) {
        cancel();
        if (closed) { result.accept(0); return; }
        int chosen;
        long pool = ObjC.poolPush();
        try (Popup popup = create(model)) {
            active = popup;
            ObjC.popupMenu(popup.menu, "popUpMenuPositioningItem:atLocation:inView:", 0, x, y, view);
            chosen = popup.cancelled ? 0 : popup.chosen;
        } finally {
            active = null;
            ObjC.poolPop(pool);
        }
        result.accept(chosen);
    }

    void cancel() {
        if (active != null) {
            active.cancelled = true;
            ObjC.send(active.menu, "cancelTracking");
        }
    }

    void close() { closed = true; cancel(); }

    static final class Popup implements AutoCloseable {
        final MenuModel model;
        final long target;
        long menu;
        int chosen;
        boolean cancelled, disposed;

        Popup(MenuModel model) {
            this.model = model;
            target = ObjC.send(ObjC.send(targetClass(), "alloc"), "init");
            OWNERS.put(target, this);
            try {
                menu = build(model.items(), target);
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() {
            if (disposed) return;
            disposed = true;
            OWNERS.remove(target);
            ObjC.release(menu);
            ObjC.release(target);
        }
    }

    private static long build(java.util.List<MenuModel.Item> items, long target) {
        long menu = ObjC.create("NSMenu");
        try {
            ObjC.send(menu, "setAutoenablesItems:", 0);
            for (MenuModel.Item item : items) {
                if (item.kind() == MenuModel.Kind.SEPARATOR) {
                    ObjC.send(menu, "addItem:", ObjC.send(ObjC.cls("NSMenuItem"), "separatorItem"));
                    continue;
                }
                String key = item.shortcut() == null ? "" : equivalent(item.shortcut().key());
                boolean submenu = item.kind() == MenuModel.Kind.SUBMENU;
                long nativeItem = ObjC.send(ObjC.send(ObjC.cls("NSMenuItem"), "alloc"), "initWithTitle:action:keyEquivalent:",
                        ObjC.nsString(item.label()), submenu ? 0 : ObjC.sel("juiContextMenuItem:"), ObjC.nsString(key));
                try {
                    ObjC.send(nativeItem, "setTag:", item.id());
                    ObjC.send(nativeItem, "setEnabled:", item.enabled() ? 1 : 0);
                    ObjC.send(nativeItem, "setState:", item.checked() ? 1 : 0);
                    ObjC.send(nativeItem, "setKeyEquivalentModifierMask:", modifiers(item.shortcut()));
                    if (submenu) {
                        long child = build(item.children(), target);
                        try { ObjC.send(nativeItem, "setSubmenu:", child); }
                        finally { ObjC.release(child); }
                    } else ObjC.send(nativeItem, "setTarget:", target);
                    ObjC.send(menu, "addItem:", nativeItem);
                } finally {
                    ObjC.release(nativeItem);
                }
            }
            return menu;
        } catch (RuntimeException | Error failure) {
            ObjC.release(menu);
            throw failure;
        }
    }

    private static long modifiers(Shortcut shortcut) {
        if (shortcut == null) return 0;
        int mods = shortcut.mods();
        return ((mods & Mod.CMD) != 0 ? 1L << 20 : 0) | ((mods & Mod.ALT) != 0 ? 1L << 19 : 0)
                | ((mods & Mod.SHIFT) != 0 ? 1L << 17 : 0) | ((mods & Mod.CTRL) != 0 ? 1L << 18 : 0);
    }

    static String equivalent(Key key) {
        if (key.ordinal() >= Key.A.ordinal() && key.ordinal() <= Key.Z.ordinal()) return Character.toString('a' + key.ordinal() - Key.A.ordinal());
        if (key.ordinal() >= Key.DIGIT0.ordinal() && key.ordinal() <= Key.DIGIT9.ordinal()) return Character.toString('0' + key.ordinal() - Key.DIGIT0.ordinal());
        if (key.ordinal() >= Key.F1.ordinal() && key.ordinal() <= Key.F12.ordinal()) return Character.toString(0xF704 + key.ordinal() - Key.F1.ordinal());
        return switch (key) {
            case ENTER -> "\r"; case TAB -> "\t"; case SPACE -> " "; case ESCAPE -> "\u001b";
            case BACKSPACE -> "\u0008"; case DELETE -> "\uf728"; case INSERT -> "\uf727";
            case LEFT -> "\uf702"; case RIGHT -> "\uf703"; case UP -> "\uf700"; case DOWN -> "\uf701";
            case HOME -> "\uf729"; case END -> "\uf72b"; case PAGE_UP -> "\uf72c"; case PAGE_DOWN -> "\uf72d";
            case MINUS -> "-"; case EQUAL -> "="; case COMMA -> ","; case PERIOD -> "."; case SLASH -> "/";
            case SEMICOLON -> ";"; case QUOTE -> "'"; case LEFT_BRACKET -> "["; case RIGHT_BRACKET -> "]";
            case BACKSLASH -> "\\"; case BACKQUOTE -> "`";
            default -> "";
        };
    }
}
