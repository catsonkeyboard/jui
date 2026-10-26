package jui.render.metal;

import jui.ffi.ObjC;
import jui.render.Scene;
import jui.testing.Check;

public final class MetalRendererTest {

    public void testDrawsAllOfASceneLargerThanItsFirstBuffers() { // deferred minor: 1024-instance cap
        ObjC.framework("QuartzCore");
        long pool = ObjC.poolPush();
        long superlayer = ObjC.create("CALayer"); // offscreen: CAMetalLayer vends drawables without a window
        try {
            MetalRenderer renderer = new MetalRenderer(superlayer);
            Scene scene = new Scene();
            for (int frame = 0; frame < 4; frame++) { // every buffer in the ring, and one again
                scene.begin(1, null, renderer.atlas());
                for (int i = 0; i < 3000; i++) {
                    scene.box(i % 100, i / 100, 1, 1, 0, 0xFF0000FF);
                }
                Check.equal(true, renderer.render(scene, 100, 30, 1, 0xFFFFFF, false, 0));
                Check.equal(3000, renderer.instancesDrawn());
            }
        } finally {
            ObjC.release(superlayer);
            ObjC.poolPop(pool);
        }
    }

    public void testClippedBatchesDrawAndEmptyOnesAreSkipped() {
        ObjC.framework("QuartzCore");
        long pool = ObjC.poolPush();
        long superlayer = ObjC.create("CALayer");
        try {
            MetalRenderer renderer = new MetalRenderer(superlayer);
            Scene scene = new Scene();
            scene.begin(1, null, renderer.atlas());
            for (int i = 0; i < 2; i++) scene.box(i, 0, 1, 1, 0, 0xFF0000FF);
            scene.pushClip(0, 0, 50, 50);
            for (int i = 0; i < 5; i++) scene.box(i, 0, 1, 1, 0, 0xFF0000FF);
            scene.popClip();
            scene.pushClip(500, 500, 10, 10);            // outside the 100 by 30 drawable: nothing to draw
            for (int i = 0; i < 3; i++) scene.box(i, 0, 1, 1, 0, 0xFF0000FF);
            scene.popClip();
            Check.equal(true, renderer.render(scene, 100, 30, 1, 0xFFFFFF, false, 0));
            Check.equal(7, renderer.instancesDrawn());
        } finally {
            ObjC.release(superlayer);
            ObjC.poolPop(pool);
        }
    }
}
