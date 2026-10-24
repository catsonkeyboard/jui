package jui.ui.engine;

/**
 * A window's nodes, in chunks of 32 reused from frame to frame (as MyGO's
 * arena): a frame that builds what the last one built allocates nothing.
 * Chunks beyond what the last frame used, and one spare, are let go.
 */
public final class NodePool {
    private static final int CHUNK = 32;

    private Node[][] chunks = new Node[8][];
    private int chunkCount;
    private int used;
    /** Generations count every use of every node, so none repeats. */
    private long generation;

    /** Starts a frame: every node is free again. */
    public void reset() {
        int keep = (used + CHUNK - 1) / CHUNK + 1;
        for (int i = keep; i < chunkCount; i++) {
            chunks[i] = null;
        }
        chunkCount = Math.min(chunkCount, keep);
        used = 0;
    }

    /** Takes a node, back to its defaults, and returns its index. */
    public int open() {
        if (used == chunkCount * CHUNK) {
            if (chunkCount == chunks.length) {
                chunks = java.util.Arrays.copyOf(chunks, chunks.length * 2);
            }
            Node[] chunk = new Node[CHUNK];
            for (int i = 0; i < CHUNK; i++) {
                chunk[i] = new Node();
            }
            chunks[chunkCount++] = chunk;
        }
        Node n = node(used);
        n.reset();
        n.gen = ++generation;
        return used++;
    }

    public Node node(int index) {
        return chunks[index >>> 5][index & (CHUNK - 1)];
    }

    /** Nodes taken this frame. */
    public int size() {
        return used;
    }

    public int chunks() {
        return chunkCount;
    }
}
