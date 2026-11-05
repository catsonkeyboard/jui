package jui.jspace;

import jui.jspace.store.FileOps;
import jui.jspace.store.FileStore;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class FileOpsTest {
    static Path dir(String tag) throws Exception {
        return Files.createTempDirectory("jspace-ops-" + tag);
    }

    /** Runs op against target with a DIRECT publish, waiting for its one report (null means success). */
    static String sync(Path target, java.util.function.BiConsumer<Path, Consumer<String>> op) throws InterruptedException {
        AtomicReference<String> err = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicBoolean reported = new java.util.concurrent.atomic.AtomicBoolean();
        op.accept(target, e -> { err.set(e); reported.set(true); });
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (!reported.get()) {
            if (System.nanoTime() > deadline) throw new AssertionError("op did not report for " + target);
            Thread.sleep(5);
        }
        return err.get();
    }

    public void testMkdirPicksAFreeName() throws Exception {
        Path d = dir("mkdir");
        Check.equal(null, sync(d, (t, done) -> FileOps.mkdir(t, "未命名文件夹", FileStore.DIRECT, done)));
        Check.isTrue(Files.isDirectory(d.resolve("未命名文件夹")), "first folder");
        Check.equal(null, sync(d, (t, done) -> FileOps.mkdir(t, "未命名文件夹", FileStore.DIRECT, done)));
        Check.isTrue(Files.isDirectory(d.resolve("未命名文件夹 2")), "second folder");
    }

    public void testRenameRefusesAnExistingName() throws Exception {
        Path d = dir("rename");
        Files.writeString(d.resolve("a.txt"), "a");
        Files.writeString(d.resolve("b.txt"), "b");
        Check.equal(null, sync(d.resolve("a.txt"), (t, done) -> FileOps.rename(t, "c.txt", FileStore.DIRECT, done)));
        Check.isTrue(Files.exists(d.resolve("c.txt")), "renamed");
        Check.equal("目标已存在", sync(d.resolve("c.txt"), (t, done) -> FileOps.rename(t, "b.txt", FileStore.DIRECT, done)));
    }

    public void testDeleteRemovesDirectoriesRecursively() throws Exception {
        Path d = dir("delete");
        Path sub = Files.createDirectories(d.resolve("x/y"));
        Files.writeString(sub.resolve("f.txt"), "f");
        Check.equal(null, sync(d, (t, done) -> FileOps.delete(t, FileStore.DIRECT, done)));
        Check.isTrue(!Files.exists(d), "gone");
    }

    public void testCopyAndMoveBetweenDirectories() throws Exception {
        Path a = dir("a"), b = dir("b");
        Files.writeString(a.resolve("f.txt"), "hello");
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, neverAsk(), done)));
        Check.equal("hello", Files.readString(b.resolve("f.txt")));
        Check.isTrue(Files.exists(a.resolve("f.txt")), "a copy leaves the source");
        // b/f.txt now exists: the move clashes, and replacing it is the answer a user gives.
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, true, b, FileStore.DIRECT,
                        (name, answer) -> answer.accept(FileOps.Decision.OVERWRITE), done)));
        Check.isTrue(!Files.exists(a.resolve("f.txt")), "a move takes it away");
        Check.equal("hello", Files.readString(b.resolve("f.txt")));
    }

    public void testPasteIntoItsOwnDirectoryMakesACopyName() throws Exception {
        Path d = dir("self");
        Files.writeString(d.resolve("f.txt"), "x");
        Check.equal(null, sync(d.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, d, FileStore.DIRECT, neverAsk(), done)));
        Check.isTrue(Files.exists(d.resolve("f 副本.txt")), "copy name");
    }

    public void testConflictAsksThenOverwritesOrSkips() throws Exception {
        Path a = dir("ca"), b = dir("cb");
        Files.writeString(a.resolve("f.txt"), "new");
        Files.writeString(b.resolve("f.txt"), "old");
        java.util.concurrent.atomic.AtomicReference<FileOps.Decision> seen = new java.util.concurrent.atomic.AtomicReference<>();
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, (name, answer) -> {
                    seen.set(FileOps.Decision.OVERWRITE);
                    answer.accept(FileOps.Decision.OVERWRITE);
                }, done)));
        Check.equal(FileOps.Decision.OVERWRITE, seen.get());
        Check.equal("new", Files.readString(b.resolve("f.txt")));

        Files.writeString(a.resolve("f.txt"), "newer");
        Check.equal(null, sync(a.resolve("f.txt"), (t, done) ->
                FileOps.paste(t, false, b, FileStore.DIRECT, (name, answer) ->
                        answer.accept(FileOps.Decision.SKIP), done)));
        Check.equal("new", Files.readString(b.resolve("f.txt")));   // skip leaves it
    }

    static FileOps.ConflictAsk neverAsk() {
        return (name, answer) -> { throw new AssertionError("no conflict expected for " + name); };
    }
}
