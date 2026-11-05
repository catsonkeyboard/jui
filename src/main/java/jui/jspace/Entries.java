package jui.jspace;

import jui.jspace.store.FileEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/** Sorting, filtering and tree flattening of one directory's entries, pure and main-thread only. */
public final class Entries {
    public enum SortKey { NAME, DATE, SIZE, KIND }

    public record Row(FileEntry entry, int depth) {}

    public static List<Row> view(FileEntry[] entries, Function<Path, jui.jspace.store.FileStore.DirSnap> snapOf,
                                 Model.SortState sort, boolean foldersFirst, String search,
                                 boolean showHidden, Set<Path> expanded) {
        List<Row> out = new ArrayList<>();
        append(out, entries, snapOf, sort, foldersFirst, search, showHidden, expanded, 0);
        return out;
    }

    private static void append(List<Row> out, FileEntry[] entries,
                               Function<Path, jui.jspace.store.FileStore.DirSnap> snapOf,
                               Model.SortState sort, boolean foldersFirst, String search,
                               boolean showHidden, Set<Path> expanded, int depth) {
        List<FileEntry> kept = new ArrayList<>();
        String q = search.toLowerCase(Locale.ROOT);
        for (FileEntry e : entries) {
            if (!showHidden && e.name().startsWith(".")) continue;
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q)) continue;
            kept.add(e);
        }
        Comparator<FileEntry> byKey = switch (sort.key) {
            case NAME -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            case DATE -> Comparator.comparingLong(FileEntry::mtimeMillis);
            case SIZE -> Comparator.comparingLong(FileEntry::size);       // dirs are -1: first ascending
            case KIND -> Comparator.comparing(FileEntry::kind);
        };
        if (!sort.asc) byKey = byKey.reversed();
        if (foldersFirst) byKey = Comparator.comparing(FileEntry::dir).reversed().thenComparing(byKey);
        kept.sort(byKey);
        for (FileEntry e : kept) {
            out.add(new Row(e, depth));
            if (e.dir() && expanded.contains(e.path())) {
                jui.jspace.store.FileStore.DirSnap child = snapOf.apply(e.path());
                if (child != null && child.entries() != null) {
                    append(out, child.entries(), snapOf, sort, foldersFirst, search, showHidden, expanded, depth + 1);
                }
            }
        }
    }

    private Entries() {}
}
