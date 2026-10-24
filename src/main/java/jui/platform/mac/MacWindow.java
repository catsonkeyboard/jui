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

    /**
     * What the window's view receives, as MyGO's surface sends it: points
     * from the view's top left, buttons 0 (primary) and 1 (secondary),
     * modifiers as {@link jui.ui.Mod} bits. On the main thread.
     */
    public interface Input {
        void pointerMove(double x, double y, int mods);

        void pointerDown(double x, double y, int button, int clicks, int mods);

        void pointerUp(double x, double y, int button, int mods);

        void pointerLeave();

        void keyDown(jui.ui.Key key, int mods, boolean repeat);

        void modifiersChanged(int mods);

        void windowFocusChanged(boolean focused);

        void appearanceChanged(boolean dark);
    }

    private static final long STYLE_TITLED = 1, STYLE_CLOSABLE = 2, STYLE_MINIATURIZABLE = 4, STYLE_RESIZABLE = 8;
    private static final long BACKING_BUFFERED = 2;
    private static final long REDRAW_ON_SET_NEEDS_DISPLAY = 2;
    private static final long VIEW_WIDTH_HEIGHT_SIZABLE = 2 | 16;

    private static final Map<Long, MacWindow> BY_VIEW = new HashMap<>();
    private static final Map<Long, MacWindow> BY_DELEGATE = new HashMap<>();
    private static long viewClass, viewSuperclass, delegateClass;
    // Tracking area options: EnteredAndExited, MouseMoved, ActiveInActiveApp, InVisibleRect.
    private static final long TRACKING = 0x01 | 0x02 | 0x40 | 0x200;
    private static final int MOVE = 0, DOWN = 1, UP = 2;

    /** The app's text engine, shared by its windows. */
    private final TextEngine text;
    private final long window;
    private final long view;
    private final MetalRenderer renderer;
    private final Painter painter;
    private final Input input;
    private final long delegate;
    private int background;
    /** A control-click: the primary button acts as the secondary until it is released (as MyGO). */
    private boolean ctrlClick;
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

    private MacWindow(String title, double width, double height, int background, TextEngine text, Painter painter,
                      Input input) {
        this.painter = painter;
        this.input = input;
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
        long area = ObjC.initTrackingArea(send(ObjC.cls("NSTrackingArea"), "alloc"), TRACKING, view);
        send(view, "addTrackingArea:", area);
        ObjC.release(area);
        delegate = send(send(delegateClass, "alloc"), "init");
        BY_DELEGATE.put(delegate, this);
        send(window, "setDelegate:", delegate);

        send(window, "setContentView:", view);
        send(window, "center");
        send(window, "makeKeyAndOrderFront:", 0);
        send(window, "makeFirstResponder:", view);
        send(view, "setNeedsDisplay:", 1);
    }

    /**
     * Opens a window whose frames painter paints, on {@code background}
     * (0xRRGGBB), with text through the app's text engine; call it on the
     * main thread once the app is ready.
     */
    public static MacWindow open(String title, double width, double height, int background, TextEngine text,
                                 Painter painter) {
        return open(title, width, height, background, text, painter, null);
    }

    /** As {@link #open(String, double, double, int, TextEngine, Painter)}, with input sent to {@code input}. */
    public static MacWindow open(String title, double width, double height, int background, TextEngine text,
                                 Painter painter, Input input) {
        long pool = ObjC.poolPush();
        try {
            return new MacWindow(title, width, height, background, text, painter, input);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** The color frames clear to, 0xRRGGBB; asks for a frame. */
    public void setBackground(int rgb) {
        background = rgb;
        requestFrame();
    }

    /** Whether the view's appearance is dark (as MyGO's system.go decides). */
    public boolean dark() {
        long pool = ObjC.poolPush();
        try {
            long names = ObjC.array(ObjC.nsString("NSAppearanceNameAqua"), ObjC.nsString("NSAppearanceNameDarkAqua"));
            long best = send(send(view, "effectiveAppearance"), "bestMatchFromAppearancesWithNames:", names);
            return best != 0 && "NSAppearanceNameDarkAqua".equals(ObjC.string(best));
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** Closes the window. */
    public void close() {
        send(window, "setDelegate:", 0);
        send(window, "close");
        BY_VIEW.remove(view);
        BY_DELEGATE.remove(delegate);
    }

    /** The content view: for tests. */
    long view() {
        return view;
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

    /** A mouse event: kind MOVE, DOWN or UP, of button 0 or 1. */
    private void mouse(long event, int kind, int button) {
        if (input == null) {
            return;
        }
        double[] p = ObjC.locationInView(view, event);
        int mods = MacKeys.mods(send(event, "modifierFlags"));
        switch (kind) {
            case DOWN -> {
                if (button == 0 && (mods & jui.ui.Mod.CTRL) != 0) {
                    ctrlClick = true;
                }
                send(window, "makeFirstResponder:", view);
                input.pointerDown(p[0], p[1], ctrlClick && button == 0 ? 1 : button, (int) send(event, "clickCount"), mods);
            }
            case UP -> {
                int b = ctrlClick && button == 0 ? 1 : button;
                if (button == 0) {
                    ctrlClick = false;
                }
                input.pointerUp(p[0], p[1], b, mods);
            }
            default -> input.pointerMove(p[0], p[1], mods);
        }
    }

    private void key(long event) {
        if (input == null) {
            return;
        }
        int code = (int) (send(event, "keyCode") & 0xFFFF);
        long chars = send(event, "charactersIgnoringModifiers");
        input.keyDown(MacKeys.key(code, chars == 0 ? "" : ObjC.string(chars)), MacKeys.mods(send(event, "modifierFlags")),
                ObjC.sendBool(event, "isARepeat"));
    }

    /** Runs what to do with the window of a view, if it has one. */
    private static void withView(long view, java.util.function.Consumer<MacWindow> action) {
        MacWindow w = BY_VIEW.get(view);
        if (w != null) {
            action.accept(w);
        }
    }

    /** Waits on the run loop, never blocking it, for the first frame. */
    private void pollFrame() {
        if (firstFrameDoneMicros != 0 || ++polls > 5000) {
            onFirstFrame.accept(firstFrameDoneMicros);
        } else {
            ObjC.performAfter(view, "juiPollFrame:", 0, 0.001);
        }
    }

    private static void focusChanged(long delegate, boolean focused) {
        MacWindow w = BY_DELEGATE.get(delegate);
        if (w != null && w.input != null) {
            w.input.windowFocusChanged(focused);
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
                // Input, as MyGO's internal/darwin/surface.go takes it.
                .boolMethod("acceptsFirstResponder", (self, cmd) -> true)
                .boolMethod1("acceptsFirstMouse:", (self, cmd, event) -> true)
                .method1("mouseDown:", (self, cmd, e) -> withView(self, w -> w.mouse(e, DOWN, 0)))
                .method1("mouseUp:", (self, cmd, e) -> withView(self, w -> w.mouse(e, UP, 0)))
                .method1("mouseDragged:", (self, cmd, e) -> withView(self, w -> w.mouse(e, MOVE, 0)))
                .method1("rightMouseDown:", (self, cmd, e) -> withView(self, w -> w.mouse(e, DOWN, 1)))
                .method1("rightMouseUp:", (self, cmd, e) -> withView(self, w -> w.mouse(e, UP, 1)))
                .method1("rightMouseDragged:", (self, cmd, e) -> withView(self, w -> w.mouse(e, MOVE, 1)))
                .method1("mouseMoved:", (self, cmd, e) -> withView(self, w -> w.mouse(e, MOVE, 0)))
                .method1("mouseExited:", (self, cmd, e) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.pointerLeave();
                    }
                }))
                .method1("keyDown:", (self, cmd, e) -> withView(self, w -> w.key(e)))
                .method1("flagsChanged:", (self, cmd, e) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.modifiersChanged(MacKeys.mods(send(e, "modifierFlags")));
                    }
                }))
                .method("viewDidChangeEffectiveAppearance", (self, cmd) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.appearanceChanged(w.dark());
                    }
                }))
                .register();
        viewSuperclass = ObjC.superclass(viewClass);
        delegateClass = ObjCClass.define("JUIWindowDelegate", "NSObject")
                .method1("windowDidBecomeKey:", (self, cmd, note) -> focusChanged(self, true))
                .method1("windowDidResignKey:", (self, cmd, note) -> focusChanged(self, false))
                .register();
    }
}
