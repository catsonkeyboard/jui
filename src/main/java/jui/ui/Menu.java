package jui.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import jui.platform.MenuModel;
import jui.ui.engine.Dev;

public final class Menu {
    private final Run run;
    private final MenuItem parent;
    private final List<MenuItem> items = new ArrayList<>();
    private boolean active;

    private Menu(Run run, MenuItem parent) {
        this.run = run;
        this.parent = parent;
    }

    public MenuItem item(String label) {
        return add(label, MenuModel.Kind.ITEM);
    }

    public void separator() {
        add("", MenuModel.Kind.SEPARATOR);
    }

    public MenuItem submenu(String label, Consumer<Menu> build) {
        MenuItem item = add(label, MenuModel.Kind.SUBMENU);
        if (valid()) item.build = build;
        return item;
    }

    public void editItems() {
        if (!valid()) return;
        for (MenuModel.Item edit : run.edits) {
            MenuItem item = add(edit.label(), edit.kind());
            item.disabled = !edit.enabled();
            item.command = edit.command();
        }
    }

    private MenuItem add(String label, MenuModel.Kind kind) {
        if (label == null) {
            Dev.fail("a menu item's label cannot be null");
            label = "";
        }
        MenuItem item = new MenuItem(this, parent, label, kind,
                (parent == null ? "" : parent.key) + "/" + items.size() + ":" + label.length() + ":" + label);
        if (valid()) items.add(item);
        return item;
    }

    boolean valid() {
        if (active) return true;
        Dev.fail("Menu and MenuItem are valid only during their builder callback");
        return false;
    }

    boolean chosen(MenuItem item) {
        if (!valid() || run.chosen || run.choice == null || item.kind != MenuModel.Kind.ITEM
                || item.command != null || !item.enabled() || !run.choice.key().equals(item.key)) return false;
        run.chosen = true;
        return true;
    }

    private void execute(Consumer<Menu> build) {
        active = true;
        try {
            if (build != null) build.accept(this);
        } finally {
            active = false;
        }
        for (MenuItem item : items) {
            if (item.kind == MenuModel.Kind.SUBMENU) {
                item.child = new Menu(run, item);
                item.child.execute(item.build);
            }
        }
    }

    private List<MenuModel.Item> freeze(int[] next, boolean enabled) {
        List<MenuModel.Item> result = new ArrayList<>();
        for (MenuItem item : items) {
            int id = ++next[0];
            boolean on = enabled && !item.disabled && item.kind != MenuModel.Kind.SEPARATOR;
            List<MenuModel.Item> children = item.child == null ? List.of() : item.child.freeze(next, on);
            result.add(new MenuModel.Item(id, item.key, item.label, item.kind, on, item.checked,
                    item.shortcut, item.command, children));
        }
        return result;
    }

    public record Result(MenuModel model, boolean chosen) {}

    public static Result build(Consumer<Menu> build, List<MenuModel.Item> edits, MenuModel.Item choice) {
        Run run = new Run(edits, choice);
        Menu menu = new Menu(run, null);
        menu.execute(build);
        return new Result(new MenuModel(menu.freeze(new int[1], true)), run.chosen);
    }

    private static final class Run {
        final List<MenuModel.Item> edits;
        final MenuModel.Item choice;
        boolean chosen;

        Run(List<MenuModel.Item> edits, MenuModel.Item choice) {
            this.edits = edits;
            this.choice = choice;
        }
    }
}
