package jui.jspace;

import jui.jspace.store.FileEntry;
import jui.jspace.store.FileStore;
import jui.testing.Check;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileStoreTest {
    static Path dir() throws IOException {
        Path d = Files.createTempDirectory("jspace-store");
        Files.writeString(d.resolve("b.txt"), "x");
        Files.createDirectory(d.resolve("A"));
        Files.writeString(d.resolve(".h"), "x");
        return d;
    }

    /** Scans run on virtual threads; DIRECT publishes as soon as one lands, so wait a bounded while. */
    static void await(FileStore s, Path d) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (s.snap(d).state() == FileStore.State.LOADING) {
            if (System.nanoTime() > deadline) throw new AssertionError("scan did not land for " + d);
            Thread.sleep(5);
        }
    }

    public void testScanReadsSortsNothingAndReportsSizes() throws Exception {
        Path d = dir();
        FileStore s = new FileStore(FileStore.DIRECT);
        Check.equal(FileStore.State.LOADING, s.snap(d).state());
        s.scan(d);
        await(s, d);
        Check.equal(FileStore.State.READY, s.snap(d).state());
        FileEntry[] es = s.snap(d).entries();
        Check.equal(3, es.length);                               // .h still there; filtering is Entries' job
        java.util.Arrays.sort(es, java.util.Comparator.comparing(e -> e.name()
                .toLowerCase(java.util.Locale.ROOT)));
        Check.equal(".h", es[0].name());                         // '.' sorts before letters
        Check.equal("A", es[1].name());
        Check.isTrue(es[1].dir(), "A is a dir");
        Check.equal(-1, es[1].size());
        Check.equal(1, es[2].size());                            // "b.txt" holds one char
        Check.equal("文件夹", es[1].kind());
    }

    public void testMissingDirectoryIsAnErrorNotAThrow() throws Exception {
        FileStore s = new FileStore(FileStore.DIRECT);
        Path gone = Path.of("/no/such/dir");
        s.scan(gone);
        await(s, gone);
        Check.equal(FileStore.State.ERROR, s.snap(gone).state());
        Check.isTrue(s.snap(gone).error() != null, "error message");
    }

    public void testADirectPublisherLandsRightAway() throws Exception {
        Path d = dir();
        FileStore s = new FileStore(FileStore.DIRECT);
        s.scan(d);
        await(s, d);
        Check.equal(FileStore.State.READY, s.snap(d).state());
    }
}
