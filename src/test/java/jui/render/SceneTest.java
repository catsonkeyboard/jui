package jui.render;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import jui.testing.Check;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

public final class SceneTest {

    /** The rect, in pixels, of instance i as written for the GPU. */
    private static float[] written(Scene scene, int i) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment dst = arena.allocate((long) Scene.BYTES * scene.count());
            scene.writeTo(dst, scene.count());
            long o = (long) i * Scene.FLOATS;
            return new float[] {
                dst.getAtIndex(JAVA_FLOAT, o), dst.getAtIndex(JAVA_FLOAT, o + 1),
                dst.getAtIndex(JAVA_FLOAT, o + 2), dst.getAtIndex(JAVA_FLOAT, o + 3)
            };
        }
    }

    public void testBoxEdgesSnapToPixels() { // deferred minor: spec §1.3, lengths snap to physical pixels
        Scene scene = new Scene();
        scene.begin(1.5f, null, null);
        scene.box(10.2f, 3, 20.1f, 1, 0, 0xFF0000FF);
        // Edges 15.3 → 15, 45.45 → 45 across; 4.5 → 5, 6 → 6 down.
        float[] r = written(scene, 0);
        Check.equal(15f, r[0]);
        Check.equal(5f, r[1]);
        Check.equal(30f, r[2]);
        Check.equal(1f, r[3]);
    }

    public void testBoxThinnerThanAPixelKeepsOne() {
        Scene scene = new Scene();
        scene.begin(1, null, null);
        scene.box(0, 0.1f, 10, 0.3f, 0, 0xFF0000FF); // both edges round to 0
        Check.equal(1f, written(scene, 0)[3]);
    }

    public void testShadowsAreNotSnapped() {
        Scene scene = new Scene();
        scene.begin(1.5f, null, null);
        scene.shadow(10.2f, 3, 20.1f, 1, 0, 0x00000040, 4);
        float[] r = written(scene, 0);
        Check.equal(10.2f * 1.5f, r[0]);
        Check.equal(20.1f * 1.5f, r[2]);
    }

    public void testClipsSplitTheSceneIntoBatchesWithPixelScissors() {
        Scene s = new Scene();
        s.begin(2, null, null);
        s.box(0, 0, 10, 10, 0, 0xFF0000FF);
        s.pushClip(10.2f, 20, 50, 30);
        s.box(0, 0, 10, 10, 0, 0xFF0000FF);
        s.pushClip(0, 0, 40, 40);                    // nested: intersected
        s.box(0, 0, 10, 10, 0, 0xFF0000FF);
        s.popClip();
        s.popClip();
        s.box(0, 0, 10, 10, 0, 0xFF0000FF);
        Check.equal(4, s.batches());
        int[] r = new int[4];
        Check.isTrue(!s.batchClipped(0), "unclipped first");
        s.batchScissor(1, r); Check.equal(java.util.List.of(20, 40, 100, 60), java.util.List.of(r[0], r[1], r[2], r[3]));
        s.batchScissor(2, r); Check.equal(java.util.List.of(20, 40, 60, 40), java.util.List.of(r[0], r[1], r[2], r[3]));
        Check.isTrue(!s.batchClipped(3), "unclipped after the pops");
        Check.equal(1, s.batchStart(1)); Check.equal(2, s.batchEnd(1));
    }
}
