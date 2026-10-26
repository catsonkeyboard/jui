package jui.examples;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jui.app.App;
import jui.app.Window;
import jui.app.WindowOptions;
import jui.ui.BoolRef;
import jui.ui.Context;
import jui.ui.Key;
import jui.ui.Shortcut;
import jui.ui.TextRef;
import jui.ui.Theme;

import static jui.ui.Ui.button;
import static jui.ui.Ui.checkbox;
import static jui.ui.Ui.column;
import static jui.ui.Ui.primaryButton;
import static jui.ui.Ui.row;
import static jui.ui.Ui.spacer;
import static jui.ui.Ui.text;
import static jui.ui.Ui.textInput;
import static jui.ui.Ui.toggle;

/**
 * A todo list: check boxes bound to each todo, rows keyed by its id, a
 * delete button whose callback changes the list the view iterates, a switch
 * that hides finished todos; a todo is typed in and added with Enter or the
 * Add button, and ⌘N adds one with a fixed title.
 *
 * <pre>./build.sh demo Todo</pre>
 */
public final class TodoDemo {
    private static final Shortcut NEW = Shortcut.cmd(Key.N);

    static final class Todo {
        final long id;
        final String title;
        final BoolRef done = new BoolRef(false);

        Todo(long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    final List<Todo> todos = new ArrayList<>();
    final BoolRef showDone = new BoolRef(true);
    /** The title being typed. */
    final TextRef title = new TextRef("");
    private long nextId = 1;

    TodoDemo() {
        add("买牛奶");
        add("给植物浇水");
        add("回复邮件");
    }

    private void add(String title) {
        todos.add(new Todo(nextId++, title));
    }

    private void addNew() {
        add("新待办 " + nextId);
    }

    /** Adds the typed title, if there is one, and clears the input. */
    private void addTyped() {
        String t = title.get().strip();
        if (!t.isEmpty()) {
            add(t);
        }
        title.set("");
    }

    void view(Context c) {
        if (c.shortcut(NEW)) {
            addNew();
        }
        Theme t = c.theme();
        int left = 0;
        for (Todo todo : todos) {
            left += todo.done.get() ? 0 : 1;
        }
        int remaining = left;
        boolean anyDone = remaining < todos.size();
        column(c).fill().padding(24).gap(12).children(() -> {
            text(c, "待办").fontSize(22).bold();
            row(c).gap(8).children(() -> {
                toggle(c, showDone, "显示已完成");
                spacer(c);
                text(c, "剩余 " + remaining + " 项").textColor(t.textMuted());
            });
            for (Todo todo : todos) {
                if (!showDone.get() && todo.done.get()) {
                    continue;
                }
                row(c.key(todo.id)).gap(8).children(() -> {
                    checkbox(c, todo.done, todo.title);
                    spacer(c);
                    button(c, "删除").label("删除 " + todo.title).onClick(() -> todos.remove(todo));
                });
            }
            // Keyed: the rows above come and go, and the input keeps its identity, so its focus too.
            row(c.key("new")).gap(8).children(() -> {
                if (textInput(c, title).placeholder("新待办").grow(1).submitted()) {
                    addTyped();
                }
                if (primaryButton(c, "添加").clicked()) {
                    addTyped();
                }
            });
            row(c).gap(8).children(() -> {
                if (button(c, "清除已完成").disabled(!anyDone).clicked()) {
                    todos.removeIf(todo -> todo.done.get());
                }
            });
        });
    }

    public static void main(String[] args) {
        TodoDemo s = new TodoDemo();
        App.run(() -> {
            Window w = Window.open(WindowOptions.of("待办").size(420, 480).content(s::view));
            if (Arrays.asList(args).contains("--print-window")) {
                System.out.println("window=" + w.windowNumber());
            }
        });
    }
}
