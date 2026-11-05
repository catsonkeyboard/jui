package jui.jspace.store;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** The safe file operations, each on its own virtual thread, results published back. */
public final class FileOps {
    public enum Decision { OVERWRITE, SKIP, CANCEL }

    /** The UI's chance to ask about a name clash; answer from any thread. */
    public interface ConflictAsk { void ask(String name, Consumer<Decision> answer); }

    public static void mkdir(Path dir, String baseName, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            String name = baseName;
            for (int i = 2; Files.exists(dir.resolve(name)); i++) name = baseName + " " + i;
            Files.createDirectory(dir.resolve(name));
            return null;
        });
    }

    public static void rename(Path file, String newName, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            Path to = file.getParent().resolve(newName);
            if (Files.exists(to)) return "目标已存在";
            Files.move(file, to);
            return null;
        });
    }

    public static void delete(Path file, FileStore.Publish p, Consumer<String> done) {
        onThread(p, done, () -> {
            walkDelete(file);
            return null;
        });
    }

    /** Copies or moves src into targetDir, asking about clashes; same-dir copies get a 副本 name. */
    public static void paste(Path src, boolean move, Path targetDir, FileStore.Publish p,
                             ConflictAsk ask, Consumer<String> done) {
        Thread.startVirtualThread(() -> {
            String name = src.getFileName().toString();
            Path target = targetDir.resolve(name);
            boolean sameDir = src.getParent().equals(targetDir.toAbsolutePath().normalize());
            if (sameDir && !move) {
                target = targetDir.resolve(copyName(name, targetDir));
            } else if (Files.exists(target)) {
                final Decision[] box = { null };
                p.publish(() -> ask.ask(name, answer -> {
                    synchronized (box) { box[0] = answer; box.notifyAll(); }
                }));
                Decision d;
                synchronized (box) {
                    while (box[0] == null) {
                        try { box.wait(); } catch (InterruptedException e) { return; }
                    }
                    d = box[0];
                }
                if (d != Decision.OVERWRITE) {
                    Decision fin = d;
                    p.publish(() -> done.accept(fin == Decision.CANCEL ? "已取消" : null));
                    return;
                }
            }
            String err = move ? tryMove(src, target) : tryCopy(src, target);
            p.publish(() -> done.accept(err));
        });
    }

    static String copyName(String name, Path dir) {
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        String copy = stem + " 副本" + ext;
        for (int i = 2; Files.exists(dir.resolve(copy)); i++) copy = stem + " 副本 " + i + ext;
        return copy;
    }

    static String tryCopy(Path src, Path target) {
        try {
            if (Files.isDirectory(target)) walkDelete(target);
            copyTree(src, target);
            return null;
        } catch (IOException e) { return e.toString(); }
    }

    static String tryMove(Path src, Path target) {
        try {
            if (Files.isDirectory(target)) walkDelete(target);
            try {
                Files.move(src, target);
            } catch (IOException crossDevice) {                  // e.g. EXDEV: copy, then delete
                copyTree(src, target);
                walkDelete(src);
            }
            return null;
        } catch (IOException e) { return e.toString(); }
    }

    static void copyTree(Path src, Path target) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) throws IOException {
                Files.createDirectories(target.resolve(src.relativize(d).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.copy(f, target.resolve(src.relativize(f).toString()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static void walkDelete(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.delete(f); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                if (e == null) Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** The entry count under a directory (1 for a plain file), for the delete confirmation. */
    public static long countEntries(Path dirOrFile) {
        if (!Files.isDirectory(dirOrFile)) return 1;
        AtomicLong n = new AtomicLong();
        try {
            Files.walkFileTree(dirOrFile, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    n.incrementAndGet(); return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes a) {
                    if (!d.equals(dirOrFile)) n.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return n.get();                                      // best effort
        }
        return n.get();
    }

    interface Thunk { String run() throws Exception; }

    static void onThread(FileStore.Publish p, Consumer<String> done, Thunk op) {
        Thread.startVirtualThread(() -> {
            String err;
            try { err = op.run(); } catch (Throwable e) { err = e.toString(); }
            final String e2 = err;
            p.publish(() -> done.accept(e2));
        });
    }

    private FileOps() {}
}
