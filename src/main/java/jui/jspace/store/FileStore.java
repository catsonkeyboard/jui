package jui.jspace.store;

import java.nio.file.Path;

/** Directory snapshots, scanned off the main thread (Task 5 fills it in). */
public final class FileStore {
    public enum State { LOADING, READY, ERROR }
    public record DirSnap(State state, FileEntry[] entries, String error) {}

    private FileStore() {}
}
