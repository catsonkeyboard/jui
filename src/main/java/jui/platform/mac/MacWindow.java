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

        /** A scroll at (x, y): dy above 0 moves the content up; precise for a trackpad, else a wheel's DIPs. */
        void scroll(double x, double y, double dx, double dy, boolean precise, int mods);

        // Text input, for the view's NSTextInputClient: ranges count UTF-16 units of the shown text
        // (the committed text with the marked text in it); -1 is no range. Without a text input
        // that takes text they do nothing.

        /** Whether a text input takes text now: keys then go through the input method. */
        default boolean textActive() {
            return false;
        }

        /** Whether keys go through the input method; with it off, a key types its characters (as MyGO's NoInputMethod). */
        default boolean textInputMethod() {
            return true;
        }

        default boolean textComposing() {
            return false;
        }

        default void textInsert(String s, int replStart, int replLength) {}

        default void textMark(String s, int selStart, int selLength, int replStart, int replLength) {}

        default void textUnmark() {}

        default boolean textMarkedRange(int[] out) {
            return false;
        }

        default boolean textSelectedRange(int[] out) {
            return false;
        }

        default int textLength() {
            return -1;
        }

        default String textIn(int start, int end) {
            return null;
        }

        /** The rect of [start, end) in view points: x, y, width, height, and the end of the range it covers in out[4]. */
        default boolean textRect(int start, int end, float[] out) {
            return false;
        }

        /** The index nearest a point in view points, or -1. */
        default int textIndexAt(double x, double y) {
            return -1;
        }

        /** A command of the Edit menu. */
        default void editCommand(jui.ui.EditCommand c) {}
    }

    private static final long STYLE_TITLED = 1, STYLE_CLOSABLE = 2, STYLE_MINIATURIZABLE = 4, STYLE_RESIZABLE = 8;
    private static final long BACKING_BUFFERED = 2;
    private static final long REDRAW_ON_SET_NEEDS_DISPLAY = 2;
    private static final long VIEW_WIDTH_HEIGHT_SIZABLE = 2 | 16;

    private static final Map<Long, MacWindow> BY_VIEW = new HashMap<>();
    private static final Map<Long, MacWindow> BY_DELEGATE = new HashMap<>();
    private static long viewClass, viewSuperclass, delegateClass;
    // Tracking area options: EnteredAndExited, MouseMoved, CursorUpdate, ActiveInActiveApp, InVisibleRect.
    private static final long TRACKING = 0x01 | 0x02 | 0x04 | 0x40 | 0x200;
    private static final int MOVE = 0, DOWN = 1, UP = 2;
    /** NSRunLoopCommonModes, a Foundation NSString constant: its value is read from the symbol. */
    private static final long COMMON_MODES = ObjC.symbol(ObjC.framework("Foundation"), "NSRunLoopCommonModes")
            .reinterpret(8).get(java.lang.foreign.ValueLayout.JAVA_LONG, 0);

    /** The app's text engine, shared by its windows. */
    private final TextEngine text;
    private final long window;
    private final long view;
    private final MetalRenderer renderer;
    private final Painter painter;
    private final Input input;
    private final long delegate;
    /** The view's display link (macOS 14): runs while frames come one after another, paused otherwise. */
    private final long displayLink;
    private boolean linkRunning;
    /** A frame is asked for; and one was owed while the window was hidden. */
    private boolean due, owed;
    /**
     * Whether the window shows: assumed until AppKit says otherwise in
     * windowDidChangeOcclusionState:, as occlusionState can read as hidden
     * for a window that is displayed (seen for a test process's windows).
     */
    private boolean shown = true;
    /** When the last frame was drawn, and the display's refresh period, in nanoseconds. */
    private long lastFrameNanos;
    long refresh = 1_000_000_000L / 60;
    private int background;
    /** A control-click: the primary button acts as the secondary until it is released (as MyGO). */
    private boolean ctrlClick;
    private final Scene scene = new Scene();

    // A key in interpretKeyEvents:, sent to the input only if the input method hands it back
    // (doCommandBySelector:) while it held it as a composition's.
    private boolean suppressed;
    private jui.ui.Key owedKey;
    private int owedMods;
    private boolean owedRepeat;
    /** Tests: in place of interpretKeyEvents:, whose result depends on the input source in use. */
    java.util.function.LongConsumer interpretHook;
    private final int[] rangeOut = new int[2];
    private final float[] rectOut = new float[5];
    /** The pointer's shape (an NSCursor), and whether the pointer is over the view. */
    private long cursor = MacCursors.of(jui.ui.Cursor.DEFAULT);
    private boolean pointerInside;

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
        // Frames that follow one another come from the display link, as MyGO's surface.go does.
        displayLink = ObjC.retain(send(view, "displayLinkWithTarget:selector:", view, ObjC.sel("juiTick:")));
        send(displayLink, "setPaused:", 1);
        send(displayLink, "addToRunLoop:forMode:", send(ObjC.cls("NSRunLoop"), "mainRunLoop"), COMMON_MODES);

        send(window, "setContentView:", view);
        send(window, "center");
        send(window, "makeKeyAndOrderFront:", 0);
        send(window, "makeFirstResponder:", view);
        readRefresh();
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
        cancelTimer();
        send(displayLink, "invalidate");
        ObjC.release(displayLink);
        send(window, "setDelegate:", 0);
        send(window, "close");
        BY_VIEW.remove(view);
        BY_DELEGATE.remove(delegate);
        ObjC.release(delegate); // the window does not retain its delegate: ours was the only reference
    }

    /** The content view: for tests. */
    long view() {
        return view;
    }

    /** The window's delegate: for tests. */
    long delegate() {
        return delegate;
    }

    /** The window: for tests. */
    long window() {
        return window;
    }

    /** The pointer's shape over the view: set now if the pointer is there, and again as it comes back. */
    public void setCursor(jui.ui.Cursor c) {
        cursor = MacCursors.of(c);
        if (pointerInside) {
            send(cursor, "set");
        }
    }

    /** The input method forgets its marked text: the app committed or dropped it. */
    public void discardMarkedText() {
        long context = send(view, "inputContext");
        if (context != 0) {
            send(context, "discardMarkedText");
        }
    }

    /** The input method asks where the caret is again. */
    public void invalidateCharacterCoordinates() {
        long context = send(view, "inputContext");
        if (context != 0) {
            send(context, "invalidateCharacterCoordinates");
        }
    }

    public MetalRenderer renderer() {
        return renderer;
    }

    /**
     * Asks for a frame, as MyGO's surface does: after a pause, AppKit
     * displays the view in this pass of the run loop; while frames follow
     * one another, the display link draws one a refresh.
     */
    public void requestFrame() {
        if (linkRunning) {
            due = true;
            return;
        }
        if (System.nanoTime() - lastFrameNanos > 2 * refresh && visible()) {
            send(view, "setNeedsDisplay:", 1);
            return;
        }
        send(displayLink, "setPaused:", 0);
        linkRunning = true;
        due = true;
    }

    /** Draws a frame after delayNanos, in place of any timer armed before. */
    public void armTimer(long delayNanos) {
        cancelTimer();
        ObjC.performAfter(view, "juiTimer:", 0, Math.max(0, delayNanos) / 1e9);
    }

    public void cancelTimer() {
        send(ObjC.cls("NSObject"), "cancelPreviousPerformRequestsWithTarget:selector:object:", view,
                ObjC.sel("juiTimer:"), 0);
    }

    /** Whether the display link is running: for tests. */
    boolean displayLinkRunning() {
        return linkRunning;
    }

    /** Whether any of the window shows on screen, as the last occlusion change said. */
    private boolean visible() {
        return shown;
    }

    /** A display link tick: draws the frame asked for, else pauses; a hidden window owes it. */
    private void tick() {
        if (!due) {
            pauseLink();
            return;
        }
        if (!visible()) {
            pauseLink();
            owed = true;
            return;
        }
        due = false;
        if (ObjC.sendBool(send(view, "layer"), "needsDisplay")) {
            return; // AppKit displays the view in this pass
        }
        draw();
    }

    private void pauseLink() {
        send(displayLink, "setPaused:", 1);
        linkRunning = false;
    }

    private void occlusionChanged() {
        shown = (send(window, "occlusionState") & 2) != 0; // NSWindowOcclusionStateVisible
        if (owed && shown) {
            owed = false;
            requestFrame();
        }
    }

    /** The refresh period of the window's screen: read as it opens, and again when it moves to another screen. */
    private void readRefresh() {
        long screen = send(window, "screen");
        if (screen != 0) {
            long fps = send(screen, "maximumFramesPerSecond");
            refresh = 1_000_000_000L / Math.max(fps, 30);
        }
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
        lastFrameNanos = System.nanoTime();
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
        pointerInside = true;
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

    /** A scroll: a wheel's notches are 40 DIPs, and the deltas turn so that up shows what is above (as MyGO). */
    private void wheel(long event) {
        if (input == null) {
            return;
        }
        double[] p = ObjC.locationInView(view, event);
        boolean precise = ObjC.sendBool(event, "hasPreciseScrollingDeltas");
        double k = precise ? -1 : -40;
        input.scroll(p[0], p[1], k * ObjC.sendDouble(event, "scrollingDeltaX"), k * ObjC.sendDouble(event, "scrollingDeltaY"),
                precise, MacKeys.mods(send(event, "modifierFlags")));
    }

    /**
     * A key, as MyGO's surface takes it: the input gets it first, unless the
     * input method is composing; then, with a text input that takes text
     * and no Command or Control held, the input method sees it and answers
     * with insertText: or setMarkedText:.
     */
    private void key(long event) {
        if (input == null) {
            return;
        }
        int code = (int) (send(event, "keyCode") & 0xFFFF);
        long chars = send(event, "charactersIgnoringModifiers");
        jui.ui.Key key = MacKeys.key(code, chars == 0 ? "" : ObjC.string(chars));
        int mods = MacKeys.mods(send(event, "modifierFlags"));
        boolean repeat = ObjC.sendBool(event, "isARepeat");
        boolean text = input.textActive() && (mods & (jui.ui.Mod.CMD | jui.ui.Mod.CTRL)) == 0;
        boolean composing = text && input.textComposing();
        if (!composing) {
            input.keyDown(key, mods, repeat);
        }
        if (text && !input.textInputMethod()) {
            // Without the input method a key types the characters it makes, as a code editor's normal mode.
            long typed = send(event, "characters");
            String s = typed(typed == 0 ? "" : ObjC.string(typed));
            if (!s.isEmpty()) {
                input.textInsert(s, -1, 0);
            }
            return;
        }
        if (text) {
            suppressed = composing;
            owedKey = key;
            owedMods = mods;
            owedRepeat = repeat;
            try {
                if (interpretHook != null) {
                    interpretHook.accept(event);
                } else {
                    send(view, "interpretKeyEvents:", ObjC.array(event));
                }
            } finally {
                suppressed = false;
            }
        }
    }

    /**
     * The text a key's characters type, without the input method: control
     * characters, DEL and the function keys' private-use characters
     * (U+F700–U+F8FF) type nothing (as MyGO's typedChars).
     */
    static String typed(String chars) {
        StringBuilder b = null;
        for (int i = 0; i < chars.length(); i++) {
            char c = chars.charAt(i);
            boolean drop = c < 0x20 || c == 0x7F || c >= 0xF700 && c <= 0xF8FF;
            if (drop && b == null) {
                b = new StringBuilder(chars.substring(0, i));
            } else if (!drop && b != null) {
                b.append(c);
            }
        }
        return b == null ? chars : b.toString();
    }

    private void edit(jui.ui.EditCommand c) {
        if (input != null) {
            input.editCommand(c);
        }
    }

    /** The input method handed back the key it held for a composition, as a command: the input gets it, once. */
    private void command() {
        if (suppressed && input != null) {
            suppressed = false;
            input.keyDown(owedKey, owedMods, owedRepeat);
        }
    }

    /** A Java string of an NSString or NSAttributedString. */
    private static String stringOf(long obj) {
        if (obj != 0 && ObjC.sendBool(obj, "isKindOfClass:", ObjC.cls("NSAttributedString"))) {
            return ObjC.string(send(obj, "string"));
        }
        return ObjC.string(obj);
    }

    /** A location from AppKit: NSNotFound (or one past int) is -1. */
    private static int location(long l) {
        return l < 0 || l > Integer.MAX_VALUE ? -1 : (int) l;
    }

    private static int length(long n) {
        return (int) Math.max(0, Math.min(n, Integer.MAX_VALUE));
    }

    private boolean range(boolean marked, long[] out) {
        boolean ok = input != null && (marked ? input.textMarkedRange(rangeOut) : input.textSelectedRange(rangeOut));
        out[0] = ok ? rangeOut[0] : ObjC.NOT_FOUND;
        out[1] = ok ? rangeOut[1] : 0;
        return ok;
    }

    /** attributedSubstringForProposedRange:actualRange:: an autoreleased NSAttributedString of the shown text's part. */
    private long substring(long location, long length, long actual) {
        int n = input == null ? -1 : input.textLength();
        int a = location(location);
        if (n < 0 || a < 0) {
            return 0;
        }
        a = Math.min(a, n);
        int z = (int) Math.min((long) a + length(length), n);
        String s = input.textIn(a, z);
        if (s == null) {
            return 0;
        }
        ObjC.writeRange(actual, a, z - a);
        long attributed = send(send(ObjC.cls("NSAttributedString"), "alloc"), "initWithString:", ObjC.nsString(s));
        return send(attributed, "autorelease");
    }

    /** characterIndexForPoint:: a screen point to the view, then the input's index, NSNotFound for none. */
    private long indexAt(double x, double y) {
        if (input == null) {
            return ObjC.NOT_FOUND;
        }
        double[] r = ObjC.convertRect(window, "convertRectFromScreen:", x, y, 0, 0);
        double[] v = ObjC.convertRect(view, "convertRect:fromView:", r[0], r[1], 0, 0, 0);
        int i = input.textIndexAt(v[0], v[1]);
        return i < 0 ? ObjC.NOT_FOUND : i;
    }

    /** firstRectForCharacterRange:actualRange:: the input's rect, in screen coordinates; a zero rect for none. */
    private void firstRect(long location, long length, long actual, double[] out) {
        int a = location(location);
        if (input == null || a < 0 || !input.textRect(a, (int) Math.min((long) a + length(length), Integer.MAX_VALUE), rectOut)) {
            return;
        }
        double[] r = ObjC.convertRect(view, "convertRect:toView:", rectOut[0], rectOut[1], rectOut[2], rectOut[3], 0);
        double[] screen = ObjC.convertRect(window, "convertRectToScreen:", r[0], r[1], r[2], r[3]);
        System.arraycopy(screen, 0, out, 0, 4);
        ObjC.writeRange(actual, a, Math.max(0, (int) rectOut[4] - a));
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
                    w.pointerInside = false;
                    if (w.input != null) {
                        w.input.pointerLeave();
                    }
                }))
                // The pointer's shape, set again as AppKit resets it (as MyGO's surface).
                .method1("mouseEntered:", (self, cmd, e) -> withView(self, w -> {
                    w.pointerInside = true;
                    send(w.cursor, "set");
                }))
                .method1("cursorUpdate:", (self, cmd, e) -> withView(self, w -> send(w.cursor, "set")))
                // The Edit menu's commands, through the responder chain.
                .method1("undo:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.UNDO)))
                .method1("redo:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.REDO)))
                .method1("cut:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.CUT)))
                .method1("copy:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.COPY)))
                .method1("paste:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.PASTE)))
                .method1("selectAll:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.SELECT_ALL)))
                .method1("pasteAsPlainText:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.PASTE_AND_MATCH_STYLE)))
                .method1("delete:", (self, cmd, sender) -> withView(self, w -> w.edit(jui.ui.EditCommand.DELETE)))
                .method1("keyDown:", (self, cmd, e) -> withView(self, w -> w.key(e)))
                // NSTextInputClient, as MyGO's surface implements it.
                .protocol("NSTextInputClient")
                .boolMethod("hasMarkedText", (self, cmd) -> {
                    MacWindow w = BY_VIEW.get(self);
                    return w != null && w.range(true, new long[2]);
                })
                .rangeMethod("markedRange", (self, cmd, out) -> {
                    MacWindow w = BY_VIEW.get(self);
                    if (w == null) {
                        out[0] = ObjC.NOT_FOUND;
                    } else {
                        w.range(true, out);
                    }
                })
                .rangeMethod("selectedRange", (self, cmd, out) -> {
                    MacWindow w = BY_VIEW.get(self);
                    if (w == null) {
                        out[0] = ObjC.NOT_FOUND;
                    } else {
                        w.range(false, out);
                    }
                })
                .textRangesMethod("setMarkedText:selectedRange:replacementRange:",
                        (self, cmd, text, selLocation, selLength, replLocation, replLength) -> withView(self, w -> {
                            if (w.input != null) {
                                w.input.textMark(stringOf(text), Math.max(0, location(selLocation)), length(selLength),
                                        location(replLocation), length(replLength));
                            }
                        }))
                .method("unmarkText", (self, cmd) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.textUnmark();
                    }
                }))
                .textRangeMethod("insertText:replacementRange:", (self, cmd, text, location, length) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.textInsert(stringOf(text), location(location), length(length));
                    }
                }))
                .method1("insertText:", (self, cmd, text) -> withView(self, w -> {
                    if (w.input != null) {
                        w.input.textInsert(stringOf(text), -1, 0);
                    }
                }))
                .wordMethod("validAttributesForMarkedText", (self, cmd) -> send(ObjC.cls("NSArray"), "array"))
                .wordRangeMethod("attributedSubstringForProposedRange:actualRange:", (self, cmd, location, length, actual) -> {
                    MacWindow w = BY_VIEW.get(self);
                    return w == null ? 0 : w.substring(location, length, actual);
                })
                .wordPointMethod("characterIndexForPoint:", (self, cmd, x, y) -> {
                    MacWindow w = BY_VIEW.get(self);
                    return w == null ? ObjC.NOT_FOUND : w.indexAt(x, y);
                })
                .rectRangeMethod("firstRectForCharacterRange:actualRange:", (self, cmd, location, length, actual, out) -> {
                    MacWindow w = BY_VIEW.get(self);
                    if (w != null) {
                        w.firstRect(location, length, actual, out);
                    }
                })
                .method1("doCommandBySelector:", (self, cmd, selector) -> withView(self, MacWindow::command))
                .method1("scrollWheel:", (self, cmd, e) -> withView(self, w -> w.wheel(e)))
                .method1("juiTick:", (self, cmd, link) -> withView(self, MacWindow::tick))
                .method1("juiTimer:", (self, cmd, arg) -> withView(self, MacWindow::requestFrame))
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
                .method1("windowDidChangeScreen:", (self, cmd, note) -> {
                    MacWindow w = BY_DELEGATE.get(self);
                    if (w != null) {
                        w.readRefresh();
                    }
                })
                .method1("windowDidChangeOcclusionState:", (self, cmd, note) -> {
                    MacWindow w = BY_DELEGATE.get(self);
                    if (w != null) {
                        w.occlusionChanged();
                    }
                })
                .register();
    }
}
