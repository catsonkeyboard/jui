package jui.jspace;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The app's whole state, read and written on the main thread only. */
final class Model {
    enum Layout { SINGLE, DOUBLE, GRID }
    enum ViewMode { LIST, GRID }

    static final class SortState {
        Entries.SortKey key = Entries.SortKey.NAME;
        boolean asc = true;
    }

    /** One open tab in one pane: where it is and how it shows it. */
    static final class TabState {
        Path dir;
        final ArrayDeque<Path> back = new ArrayDeque<>(), forward = new ArrayDeque<>();
        ViewMode view = ViewMode.LIST;
        final SortState sort = new SortState();
        final Set<Path> expanded = new HashSet<>();
        final jui.ui.ScrollState listScroll = new jui.ui.ScrollState();
        final jui.ui.ScrollState gridScroll = new jui.ui.ScrollState();
        final jui.ui.ListState listRows = new jui.ui.ListState();
        final jui.ui.ListState gridRows = new jui.ui.ListState();
        Path selected;                    // the one selected entry, null for none
        String search = "";
        String renameNew;                 // the inline rename input's initial text, null hides it
        int rowCount;                     // last frame's visible row count, for the status line
        float paneWidth;                  // last frame's content width, for the grid's columns

        TabState(Path dir) { this.dir = dir; }
    }

    static final class PaneState {
        final List<TabState> tabs = new ArrayList<>();
        int current;
        TabState tab() { return tabs.get(current); }
    }

    /** The clipboard one Copy or Cut filled, for the next Paste. */
    static final class Clip {
        final Path path;
        final boolean move;
        Clip(Path path, boolean move) { this.path = path; this.move = move; }
    }

    /** The bottom line: what the panes hold, and a note that fades. */
    static final class Status {
        String left = "";
        String note = "";                 // shown right, replaced by the next note
        long noteAt;                      // System.nanoTime() when set, for the fade
    }

    Layout layout = Layout.GRID;
    int active;                           // the pane clicks and keys act on
    final PaneState[] panes = new PaneState[] { new PaneState(), new PaneState(), new PaneState(), new PaneState() };
    final jui.ui.FloatRef vSplit = new jui.ui.FloatRef(380);      // the grid's horizontal line, in DIP
    final jui.ui.FloatRef[] hSplit = { new jui.ui.FloatRef(460), new jui.ui.FloatRef(460) };  // each row's line
    Clip clip;
    final Status status = new Status();
    boolean sidebarOpen = true;

    private final jui.jspace.store.Settings settings;

    jui.jspace.store.Settings settings() { return settings; }

    Model() { this(new jui.jspace.store.Settings()); }

    /** Takes its defaults (layout, sort, density) from the loaded settings. */
    Model(jui.jspace.store.Settings s) {
        this.settings = s;
        layout = switch (s.defaultLayout) {
            case SINGLE -> Layout.SINGLE;
            case DOUBLE -> Layout.DOUBLE;
            case GRID -> Layout.GRID;
        };
        Path home = Path.of(System.getProperty("user.home"));
        for (PaneState p : panes) {
            TabState t = new TabState(home);
            t.sort.key = s.defaultSort;
            t.sort.asc = s.defaultSortAsc;
            p.tabs.add(t);
        }
    }
}
