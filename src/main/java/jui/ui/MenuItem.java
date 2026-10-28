package jui.ui;

import java.util.function.Consumer;
import jui.platform.MenuModel;

public final class MenuItem {
    final Menu menu;
    final MenuItem parent;
    final String label, key;
    final MenuModel.Kind kind;
    boolean disabled, checked;
    Shortcut shortcut;
    EditCommand command;
    Consumer<Menu> build;
    Menu child;

    MenuItem(Menu menu, MenuItem parent, String label, MenuModel.Kind kind, String key) {
        this.menu = menu;
        this.parent = parent;
        this.label = label;
        this.kind = kind;
        this.key = key;
    }

    public MenuItem disabled(boolean disabled) {
        if (menu.valid()) this.disabled = disabled;
        return this;
    }

    public MenuItem checked(boolean checked) {
        if (menu.valid() && kind == MenuModel.Kind.ITEM) this.checked = checked;
        return this;
    }

    public MenuItem shortcut(Shortcut shortcut) {
        if (menu.valid()) this.shortcut = shortcut;
        return this;
    }

    public boolean chosen() {
        return menu.chosen(this);
    }

    boolean enabled() {
        return !disabled && (parent == null || parent.enabled());
    }
}
