package jui.util;

import java.util.Arrays;
import java.util.Objects;

/**
 * A hash map from long keys to non-null values, by open addressing, that
 * allocates nothing to look a key up, replace or remove it: the caches and
 * tables that frames hit for every glyph, text and element use it instead
 * of maps keyed by objects made per lookup.
 */
public final class LongMap<V> {
    private long[] keys;
    private Object[] values;
    private int size;

    public LongMap() {
        keys = new long[16];
        values = new Object[16];
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            Object v = values[i];
            if (v == null) {
                return null;
            }
            if (keys[i] == key) {
                return (V) v;
            }
        }
    }

    public void put(long key, V value) {
        Objects.requireNonNull(value);
        if ((size + 1) * 2 > keys.length) {
            grow();
        }
        int mask = keys.length - 1;
        for (int i = slot(key, mask); ; i = (i + 1) & mask) {
            if (values[i] == null) {
                keys[i] = key;
                values[i] = value;
                size++;
                return;
            }
            if (keys[i] == key) {
                values[i] = value;
                return;
            }
        }
    }

    /**
     * Removes key and returns its value, or null if it had none. Later keys
     * of its probe chain shift back into the gap, so no tombstone is left.
     */
    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int mask = keys.length - 1;
        int i = slot(key, mask);
        while (values[i] != null && keys[i] != key) {
            i = (i + 1) & mask;
        }
        if (values[i] == null) {
            return null;
        }
        V old = (V) values[i];
        for (int j = (i + 1) & mask; values[j] != null; j = (j + 1) & mask) {
            int home = slot(keys[j], mask);
            // The entry at j stays if its home lies cyclically in (i, j].
            boolean stays = i <= j ? i < home && home <= j : i < home || home <= j;
            if (!stays) {
                keys[i] = keys[j];
                values[i] = values[j];
                i = j;
            }
        }
        values[i] = null;
        size--;
        return old;
    }

    public int size() {
        return size;
    }

    public void clear() {
        Arrays.fill(values, null);
        size = 0;
    }

    private void grow() {
        long[] oldKeys = keys;
        Object[] oldValues = values;
        keys = new long[oldKeys.length * 2];
        values = new Object[oldKeys.length * 2];
        int mask = keys.length - 1;
        for (int j = 0; j < oldKeys.length; j++) {
            if (oldValues[j] != null) {
                int i = slot(oldKeys[j], mask);
                while (values[i] != null) {
                    i = (i + 1) & mask;
                }
                keys[i] = oldKeys[j];
                values[i] = oldValues[j];
            }
        }
    }

    private static int slot(long key, int mask) {
        long h = key * 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 32)) & mask;
    }
}
