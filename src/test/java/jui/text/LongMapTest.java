package jui.text;

import jui.testing.Check;

public final class LongMapTest {
    public void testPutGetAndReplace() {
        LongMap<String> m = new LongMap<>();
        m.put(1, "a");
        m.put(2, "b");
        Check.equal("a", m.get(1));
        m.put(1, "c");
        Check.equal("c", m.get(1));
        Check.equal(2, m.size());
        Check.equal(null, m.get(3));
    }

    public void testGrowsAndKeepsEverything() {
        LongMap<Long> m = new LongMap<>();
        for (long i = 0; i < 10_000; i++) {
            m.put(i * 1_000_003L, i);
        }
        Check.equal(10_000, m.size());
        for (long i = 0; i < 10_000; i++) {
            Check.equal((Object) i, m.get(i * 1_000_003L));
        }
    }

    public void testKeysDifferingOnlyInHighBits() {
        LongMap<Integer> m = new LongMap<>();
        for (int i = 0; i < 64; i++) {
            m.put((long) i << 40, i);
        }
        for (int i = 0; i < 64; i++) {
            Check.equal((Object) i, m.get((long) i << 40));
        }
    }

    public void testClear() {
        LongMap<String> m = new LongMap<>();
        m.put(5, "x");
        m.clear();
        Check.equal(0, m.size());
        Check.equal(null, m.get(5));
    }
}
