package jui.jspace.store;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Directory snapshots: scanned on virtual threads, published to the main thread, immutable once seen. */
public final class FileStore {

    /** How a scan's result reaches the main thread; Window::update in the app, Runnable::run in tests. */
    public interface Publish { void publish(Runnable r); }

    public static final Publish DIRECT = Runnable::run;

    public enum State { LOADING, READY, ERROR }

    public record DirSnap(State state, FileEntry[] entries, String error) {
        public static DirSnap loading() { return new DirSnap(State.LOADING, new FileEntry[0], null); }
        public static DirSnap error(String msg) { return new DirSnap(State.ERROR, new FileEntry[0], msg); }
    }

    private static final class Slot {
        volatile DirSnap snap = DirSnap.loading();
        volatile long gen;
    }

    private final Publish publish;
    private final Map<Path, Slot> slots = new ConcurrentHashMap<>();

    public FileStore(Publish publish) { this.publish = publish; }

    /** The directory's latest snapshot; LOADING before any scan was asked for. */
    public DirSnap snap(Path dir) {
        Slot s = slots.get(key(dir));
        return s == null ? DirSnap.loading() : s.snap;
    }

    /** Whether anyone ever asked: the pane shows its LOADING state only after a scan was asked for. */
    public boolean known(Path dir) { return slots.containsKey(key(dir)); }

    /** Scans the directory again off the main thread; the newest scan of one directory wins. */
    public void scan(Path dir) {
        Path k = key(dir);
        Slot s = slots.computeIfAbsent(k, p -> new Slot());
        long gen = ++s.gen;
        Thread.startVirtualThread(() -> {
            DirSnap result = read(k);
            publish.publish(() -> { if (gen == s.gen) s.snap = result; });
        });
    }

    private static Path key(Path dir) { return dir.toAbsolutePath().normalize(); }

    /** Reads one directory into a snapshot; errors become ERROR snapshots, never throws. */
    static DirSnap read(Path dir) {
        List<FileEntry> out = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                try {
                    var a = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
                    String name = p.getFileName().toString();
                    boolean d = a.isDirectory();
                    out.add(new FileEntry(name, p, d, d ? -1 : a.size(),
                            a.lastModifiedTime().toMillis(), KindMap.of(name, d), FileEntry.IconType.of(name, d)));
                } catch (IOException skipped) {
                    // one unreadable entry does not fail the directory
                }
            }
        } catch (IOException | RuntimeException e) {
            return DirSnap.error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
        return new DirSnap(State.READY, out.toArray(new FileEntry[0]), null);
    }
}
