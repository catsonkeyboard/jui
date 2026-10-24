package jui.ui.engine;

import java.util.Arrays;

/** Callbacks registered during a pass, each with its node, run once the pass's view is built. */
final class Actions {
    private int[] nodes = new int[8];
    private Runnable[] runs = new Runnable[8];
    private int count;

    void add(int index, Runnable action) {
        if (count == nodes.length) {
            nodes = Arrays.copyOf(nodes, count * 2);
            runs = Arrays.copyOf(runs, count * 2);
        }
        nodes[count] = index;
        runs[count] = action;
        count++;
    }

    /** Runs the callbacks of nodes still enabled, in order, and returns whether any ran. */
    boolean run(NodePool pool) {
        boolean ran = false;
        for (int i = 0; i < count; i++) {
            Runnable r = runs[i];
            runs[i] = null;
            if (!pool.node(nodes[i]).disabled) {
                r.run();
                ran = true;
            }
        }
        count = 0;
        return ran;
    }

    void clear() {
        Arrays.fill(runs, 0, count, null);
        count = 0;
    }
}
