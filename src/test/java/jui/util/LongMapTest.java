package jui.util;

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

    public void testRemoveKeepsTheOtherKeysFindable() {
        LongMap<String> m = new LongMap<>();
        for (long k = 0; k < 100; k++) m.put(k * 16, "v" + k);   // same low bits: long probe chains
        Check.equal("v50", m.remove(50 * 16));
        Check.equal(null, m.get(50 * 16));
        for (long k = 0; k < 100; k++) if (k != 50) Check.equal("v" + k, m.get(k * 16));
        Check.equal(99, m.size());
        Check.equal(null, m.remove(12345));
    }
}
