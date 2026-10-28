package jui.ui.engine;

import jui.testing.Check;

public final class MenuNodePoolTest {
    public void testResetReleasesCallbacksEvenForNodesNotReused() {
        NodePool pool = new NodePool();
        Node node = pool.node(pool.open());
        node.contextMenu = menu -> menu.item("captured view");
        pool.reset();
        Check.isTrue(node.contextMenu == null, "retired nodes must not retain view callbacks");
    }
}
