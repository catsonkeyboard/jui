package jui.ui.engine;

import jui.testing.Check;
import jui.ui.Element;
import jui.ui.Theme;
import jui.ui.View;

import static jui.ui.Ui.box;
import static jui.ui.Ui.column;

public final class BuildTest {
    final Engine e = new Engine(null, Theme.light(), () -> {});

    public void testIdentityIsStableAcrossFrames() {
        View v = c -> column(c).children(() -> { box(c).label("a"); box(c).label("b"); });
        e.buildAndLayout(v, 100, 100);
        long a = e.identity("a");
        e.buildAndLayout(v, 100, 100);
        Check.equal(a, e.identity("a"));
        Check.isTrue(a != e.identity("b"), "siblings differ");
    }

    public void testKeyedIdentityIgnoresPosition() {
        boolean[] extra = {true};
        View v = c -> column(c).children(() -> {
            if (extra[0]) box(c).label("extra");
            box(c.key("k")).label("keyed");
            box(c).label("plain");
        });
        e.buildAndLayout(v, 100, 100);
        long keyed = e.identity("keyed"), plain = e.identity("plain");
        extra[0] = false;
        e.buildAndLayout(v, 100, 100);
        Check.equal(keyed, e.identity("keyed"));
        Check.isTrue(plain != e.identity("plain"), "an unkeyed element takes its position's identity");
    }

    public void testDuplicateSiblingKeysFail() {
        Check.fails(IllegalStateException.class, () -> e.buildAndLayout(
                c -> column(c).children(() -> { box(c.key(7L)); box(c.key(7L)); }), 100, 100));
    }

    public void testDuplicateKeyCheckKeepsLongListsFast() { // deferred minor: the dev check was O(n²)
        int n = 50_000;
        View v = c -> column(c).children(() -> {
            for (int i = 0; i < n; i++) {
                box(c.key(i));
            }
        });
        e.buildAndLayout(v, 100, 100); // warm up
        long start = System.nanoTime();
        e.buildAndLayout(v, 100, 100);
        long ms = (System.nanoTime() - start) / 1_000_000;
        Check.isTrue(ms < 400, n + " keyed siblings took " + ms + " ms to build in dev mode");
    }

    public void testUnusedKeyFails() {
        Check.fails(IllegalStateException.class, () -> e.buildAndLayout(c -> c.key("lost"), 100, 100));
    }

    public void testStaleElementFails() { // Review Focus 4
        Element[] kept = new Element[1];
        e.buildAndLayout(c -> kept[0] = box(c), 100, 100);
        e.buildAndLayout(c -> box(c), 100, 100);
        IllegalStateException x = Check.fails(IllegalStateException.class, () -> kept[0].label("late"));
        Check.isTrue(x.getMessage().contains("build pass"), "the message says why");
    }

    public void testStaleElementUsedDuringALaterBuildFails() { // final review #2
        Element[] kept = new Element[1];
        e.buildAndLayout(c -> kept[0] = box(c).label("old"), 100, 100);
        Check.fails(IllegalStateException.class, () -> e.buildAndLayout(c -> {
            box(c).label("victim");     // takes the index the kept element had
            kept[0].label("hijacked");  // must fail, not relabel the victim
        }, 100, 100));
    }

    public void testPoolIsReusedAcrossFrames() {
        View v = c -> { for (int i = 0; i < 1000; i++) box(c); };
        e.buildAndLayout(v, 100, 100);
        int chunks = e.poolChunks();
        e.buildAndLayout(v, 100, 100);
        Check.equal(chunks, e.poolChunks());
        Check.equal(1001, e.nodeCount()); // the root and 1000 boxes
    }

    public void testBuildingOffTheMainThreadFails() { // design §7.8
        Throwable[] thrown = new Throwable[1];
        Thread t = new Thread(() -> {
            try { e.buildAndLayout(c -> box(c), 100, 100); } catch (Throwable x) { thrown[0] = x; }
        });
        t.start();
        try { t.join(); } catch (InterruptedException x) { throw new AssertionError(x); }
        Check.isTrue(thrown[0] instanceof IllegalStateException, "only the main thread builds views");
    }
}
