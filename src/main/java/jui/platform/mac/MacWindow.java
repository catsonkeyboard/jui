package jui.platform.mac;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongConsumer;

import jui.ffi.Blocks;
import jui.ffi.Libc;
import jui.ffi.ObjC;
import jui.ffi.ObjCClass;
import jui.render.metal.MetalRenderer;
import jui.render.Scene;
import jui.text.TextEngine;

import static jui.ffi.ObjC.send;

/**
 * A window whose content a {@link Painter} draws with Metal.
 *
 * <p>As MyGO's surface does, the content is a layer-backed NSView with
 * {@code wantsUpdateLayer}: AppKit calls {@code updateLayer} when it displays
 * the view, after a resize too, and a frame is drawn then. Nothing draws
 * while nothing changes.
 */
public final class MacWindow {

    /** Paints the boxes of a frame of {@code width} by {@code height} points. */
    public interface Painter {
        void paint(Scene scene, double width, double height);
    }

    private static final long STYLE_TITLED = 1, STYLE_CLOSABLE = 2, STYLE_MINIATURIZABLE = 4, STYLE_RESIZABLE = 8;
    private static final long BACKING_BUFFERED = 2;
    private static final long REDRAW_ON_SET_NEEDS_DISPLAY = 2;
    private static final long VIEW_WIDTH_HEIGHT_SIZABLE = 2 | 16;

    private static final Map<Long, MacWindow> BY_VIEW = new HashMap<>();
    private static long viewClass, viewSuperclass;

    /** The app's text engine, shared by its windows. */
    private final TextEngine text;
    private final long window;
    private final long view;
    private final MetalRenderer renderer;
    private final Painter painter;
    private final int background;
    private final Scene scene = new Scene();

    private LongConsumer onFirstFrame;
    private int polls;
    /** Frames drawn so far. */
    public int frames;
    /** How long the painter took over the last frame, with its text laid out and rasterized. */
    public long lastPaintNanos;
    private double lastWidth, lastHeight, lastScale;
    /** Set by Metal's completed handler, on a thread of its own. */
    private volatile long firstFrameDoneMicros;
    /** Wall-clock microseconds at which the first frame was committed. */
    public long firstCommitMicros;

    private MacWindow(String title, double width, double height, int background, TextEngine text, Painter painter) {
        this.painter = painter;
        this.background = background;
        this.text = text;
        defineViewClass();
        long style = STYLE_TITLED | STYLE_CLOSABLE | STYLE_MINIATURIZABLE | STYLE_RESIZABLE;
        window = ObjC.initWindow(send(ObjC.cls("NSWindow"), "alloc"), 0, 0, width, height,
                style, BACKING_BUFFERED, false);
        send(window, "setTitle:", ObjC.nsString(title));
        send(window, "setReleasedWhenClosed:", 0);

        view = ObjC.initWithFrame(send(viewClass, "alloc"), 0, 0, width, height);
        send(view, "setWantsLayer:", 1);
        send(view, "setLayerContentsRedrawPolicy:", REDRAW_ON_SET_NEEDS_DISPLAY);
        send(view, "setAutoresizingMask:", VIEW_WIDTH_HEIGHT_SIZABLE);
        renderer = new MetalRenderer(send(view, "layer"));
        BY_VIEW.put(view, this);

        send(window, "setContentView:", view);
        send(window, "center");
        send(window, "makeKeyAndOrderFront:", 0);
        send(view, "setNeedsDisplay:", 1);
    }

    /**
     * Opens a window whose frames painter paints, on {@code background}
     * (0xRRGGBB), with text through the app's text engine; call it on the
     * main thread once the app is ready.
     */
    public static MacWindow open(String title, double width, double height, int background, TextEngine text,
                                 Painter painter) {
        long pool = ObjC.poolPush();
        try {
            return new MacWindow(title, width, height, background, text, painter);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    public MetalRenderer renderer() {
        return renderer;
    }

    /** Asks for a frame: AppKit displays the view, and the painter paints, in its next pass. */
    public void requestFrame() {
        send(view, "setNeedsDisplay:", 1);
    }

    /** The window server's number for the window, as screencapture -l takes. */
    public long windowNumber() {
        return send(window, "windowNumber");
    }

    /**
     * Calls {@code then}, on the main thread, with the wall-clock
     * microseconds at which the GPU finished the first frame (0 if it never
     * did). The frame shows at the display's next refresh.
     */
    public void onFirstFrame(LongConsumer then) {
        onFirstFrame = then;
    }

    private void draw() {
        double[] bounds = ObjC.sendRect(view, "bounds");
        double scale = ObjC.sendDouble(window, "backingScaleFactor");
        lastWidth = bounds[2];
        lastHeight = bounds[3];
        lastScale = scale;
        lastPaintNanos = paint();
        boolean first = frames == 0;
        boolean resizing = ObjC.sendBool(view, "inLiveResize");
        long handler = 0;
        if (first && onFirstFrame != null) {
            // Metal calls it on a thread of its own: an upcall from a thread
            // Java never started.
            handler = Blocks.block1(commandBuffer -> firstFrameDoneMicros = Libc.nowMicros());
        }
        boolean drew = renderer.render(scene, bounds[2], bounds[3], scale, background, resizing, handler);
        if (drew) {
            frames++;
        }
        if (first) {
            firstCommitMicros = Libc.nowMicros();
            if (handler != 0) {
                ObjC.performAfter(view, "juiPollFrame:", 0, 0.001);
            }
        }
    }

    /** Paints the scene of the last frame's size again, without drawing it, and returns how long it took. */
    public long paint() {
        long started = System.nanoTime();
        scene.begin((float) lastScale, text, renderer.atlas());
        painter.paint(scene, lastWidth, lastHeight);
        return System.nanoTime() - started;
    }

    /** Instances in the last scene painted. */
    public int instances() {
        return scene.count();
    }

    /** Waits on the run loop, never blocking it, for the first frame. */
    private void pollFrame() {
        if (firstFrameDoneMicros != 0 || ++polls > 5000) {
            onFirstFrame.accept(firstFrameDoneMicros);
        } else {
            ObjC.performAfter(view, "juiPollFrame:", 0, 0.001);
        }
    }

    private static void defineViewClass() {
        if (viewClass != 0) {
            return;
        }
        viewClass = ObjCClass.define("JUIView", "NSView")
                .boolMethod("wantsUpdateLayer", (self, cmd) -> true)
                .boolMethod("isFlipped", (self, cmd) -> true)
                .method("updateLayer", (self, cmd) -> {
                    MacWindow w = BY_VIEW.get(self);
                    if (w != null) {
                        w.draw();
                    }
                })
                .sizeMethod("setFrameSize:", (self, cmd, width, height) -> {
                    ObjC.superSetSize(self, viewSuperclass, cmd, width, height);
                    send(self, "setNeedsDisplay:", 1);
                })
                .method("viewDidChangeBackingProperties", (self, cmd) -> send(self, "setNeedsDisplay:", 1))
                .method1("juiPollFrame:", (self, cmd, arg) -> {
                    MacWindow w = BY_VIEW.get(self);
                    if (w != null) {
                        w.pollFrame();
                    }
                })
                .register();
        viewSuperclass = ObjC.superclass(viewClass);
    }
}
