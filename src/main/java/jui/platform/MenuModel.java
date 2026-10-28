package jui.platform;

import java.util.List;
import jui.ui.EditCommand;
import jui.ui.Shortcut;

public record MenuModel(List<Item> items) {
    public MenuModel { items = List.copyOf(items); }

    public enum Kind { ITEM, SEPARATOR, SUBMENU }

    public record Item(int id, String key, String label, Kind kind, boolean enabled, boolean checked,
                       Shortcut shortcut, EditCommand command, List<Item> children) {
        public Item { children = List.copyOf(children); }
    }

    public Item find(int id) {
        return find(items, id);
    }

    private static Item find(List<Item> items, int id) {
        for (Item item : items) {
            if (item.id() == id) return item;
            Item found = find(item.children(), id);
            if (found != null) return found;
        }
        return null;
    }

    public boolean visible() {
        for (Item item : items) if (item.kind() != Kind.SEPARATOR) return true;
        return false;
    }
}
