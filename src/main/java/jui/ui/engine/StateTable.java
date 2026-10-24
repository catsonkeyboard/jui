package jui.ui.engine;

import java.util.Arrays;

import jui.util.LongMap;

/**
 * A window's element states by identity. A frame marks the states of the
 * elements it builds as seen; after its last pass, states it did not see
 * are pruned onto a free list (at most 256, as MyGO keeps) for new
 * elements to reuse, so steady frames allocate nothing.
 */
public final class StateTable {
    private static final int MAX_FREE = 256;

    private final LongMap<ElementState> byId = new LongMap<>();
    private ElementState[] live = new ElementState[64];
    private int liveCount;
    private final ElementState[] free = new ElementState[MAX_FREE];
    private int freeCount;

    /** The state of identity id, or null if it has none. */
    public ElementState get(long id) {
        return byId.get(id);
    }

    /** A new state for identity id, made in {@code frame}. */
    public ElementState create(long id, long frame) {
        ElementState s = freeCount > 0 ? free[--freeCount] : new ElementState();
        s.reset();
        s.id = id;
        s.born = frame;
        byId.put(id, s);
        if (liveCount == live.length) {
            live = Arrays.copyOf(live, live.length * 2);
        }
        live[liveCount++] = s;
        return s;
    }

    /** Lets go of the states that the last pass of {@code frame} did not see. */
    public void prune(long frame, int lastPass) {
        int kept = 0;
        for (int i = 0; i < liveCount; i++) {
            ElementState s = live[i];
            if (s.seenFrame == frame && s.seenPass == lastPass) {
                live[kept++] = s;
            } else {
                byId.remove(s.id);
                s.reset();
                if (freeCount < MAX_FREE) {
                    free[freeCount++] = s;
                }
            }
        }
        Arrays.fill(live, kept, liveCount, null);
        liveCount = kept;
    }

    public int size() {
        return liveCount;
    }
}
