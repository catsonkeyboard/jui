package jui.jspace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** What the panes had open, so the next launch can put it back. */
final class WorkspaceState {
    static Path defaultFile() {
        return Path.of(System.getProperty("user.home"),
                "Library/Application Support/jui-jspace/workspace.properties");
    }

    /** A snapshot of the model's shape, safe to hand to a writing thread. */
    static Properties capture(Model m) {
        Properties p = new Properties();
        p.setProperty("layout", m.layout.name());
        p.setProperty("active", Integer.toString(m.active));
        int shown = switch (m.layout) { case SINGLE -> 1; case DOUBLE -> 2; case GRID -> 4; };
        for (int i = 0; i < shown; i++) {
            Model.PaneState pane = m.panes[i];
            for (int j = 0; j < pane.tabs.size(); j++) {
                Model.TabState t = pane.tabs.get(j);
                p.setProperty("pane" + i + ".tab" + j, t.dir == null ? "" : t.dir.toString());
                p.setProperty("pane" + i + ".view" + j, t.view.name());
            }
        }
        return p;
    }

    static void write(Properties p, Path file) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var out = Files.newOutputStream(tmp)) {
                p.store(out, "QSpace workspace");
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void save(Model m, Path file) { write(capture(m), file); }

    /** Writes the snapshot off the main thread; the capture itself is taken now. */
    static void saveAsync(Model m, Path file) {
        Properties p = capture(m);
        Thread.startVirtualThread(() -> {
            try {
                write(p, file);
            } catch (RuntimeException e) {
                // a failed snapshot write must never take the app down
            }
        });
    }

    static void apply(Model m, Path file) {
        if (!Files.isRegularFile(file)) return;
        Properties p = new Properties();
        try (var in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        try {
            m.layout = Model.Layout.valueOf(p.getProperty("layout", m.layout.name()));
        } catch (IllegalArgumentException ignored) { }
        try {
            m.active = Math.max(0, Math.min(3, Integer.parseInt(p.getProperty("active", "0"))));
        } catch (NumberFormatException ignored) { }
        int shown = switch (m.layout) { case SINGLE -> 1; case DOUBLE -> 2; case GRID -> 4; };
        for (int i = 0; i < shown; i++) {
            Model.PaneState pane = m.panes[i];
            pane.tabs.clear();
            int j = 0;
            while (p.containsKey("pane" + i + ".tab" + j)) {
                String dir = p.getProperty("pane" + i + ".tab" + j);
                Model.TabState t = new Model.TabState(dir.isEmpty()
                        ? Path.of(System.getProperty("user.home")) : Path.of(dir));
                try {
                    t.view = Model.ViewMode.valueOf(p.getProperty("pane" + i + ".view" + j, "LIST"));
                } catch (IllegalArgumentException e) {
                    t.view = Model.ViewMode.LIST;
                }
                pane.tabs.add(t);
                j++;
            }
            pane.current = 0;
            if (pane.tabs.isEmpty()) pane.tabs.add(new Model.TabState(Path.of(System.getProperty("user.home"))));
        }
    }

    private WorkspaceState() {}
}
