package jui.platform.win;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

import jui.ffi.Win32FFI;
import jui.perf.FrameStats;
import jui.platform.MenuModel;
import jui.platform.PlatformWindow;
import jui.render.Scene;
import jui.text.TextEngine;

/**
 * A window whose content a {@link Painter} draws with Direct3D 11. As
 * MacWindow draws only when AppKit displays the view, this draws when the
 * message loop asks for the frame it was woken for: nothing draws while
 * nothing changes. Input goes to {@link Input} in client points, with
 * Windows' Ctrl as the engine's Ctrl and its Windows key as CMD.
 */
public final class WinWindow implements PlatformWindow {
    private static final Arena GLOBAL = Arena.ofAuto();
    private static final Map<Long, WinWindow> BY_HWND = new HashMap<>();

    private static final MemorySegment REGISTER_CLASS_W = Win32FFI.symbol("user32", "RegisterClassExW");
    private static final MemorySegment CREATE_WINDOW_EX_W = Win32FFI.symbol("user32", "CreateWindowExW");
    private static final MemorySegment DEF_WINDOW_PROC_W = Win32FFI.symbol("user32", "DefWindowProcW");
    private static final MemorySegment SHOW_WINDOW = Win32FFI.symbol("user32", "ShowWindow");
    private static final MemorySegment UPDATE_WINDOW = Win32FFI.symbol("user32", "UpdateWindow");
    private static final MemorySegment DESTROY_WINDOW = Win32FFI.symbol("user32", "DestroyWindow");
    private static final MemorySegment GET_CLIENT_RECT = Win32FFI.symbol("user32", "GetClientRect");
    private static final MemorySegment GET_DPI_FOR_WINDOW = Win32FFI.symbol("user32", "GetDpiForWindow");
    private static final MemorySegment SET_TIMER = Win32FFI.symbol("user32", "SetTimer");
    private static final MemorySegment KILL_TIMER = Win32FFI.symbol("user32", "KillTimer");
    private static final MemorySegment BEGIN_PAINT = Win32FFI.symbol("user32", "BeginPaint");
    private static final MemorySegment END_PAINT = Win32FFI.symbol("user32", "EndPaint");
    private static final MemorySegment CREATE_POPUP_MENU = Win32FFI.symbol("user32", "CreatePopupMenu");
    private static final MemorySegment APPEND_MENU_W = Win32FFI.symbol("user32", "AppendMenuW");
    private static final MemorySegment TRACK_POPUP_MENU_EX = Win32FFI.symbol("user32", "TrackPopupMenuEx");
    private static final MemorySegment DESTROY_MENU = Win32FFI.symbol("user32", "DestroyMenu");
    private static final MemorySegment CLIENT_TO_SCREEN = Win32FFI.symbol("user32", "ClientToScreen");
    private static final MemorySegment SET_FOREGROUND_WINDOW = Win32FFI.symbol("user32", "SetForegroundWindow");
    private static final MemorySegment GET_CURSOR_POS_SYM = Win32FFI.symbol("user32", "GetCursorPos");
    private static final MemorySegment SCREEN_TO_CLIENT_SYM = Win32FFI.symbol("user32", "ScreenToClient");

    private static final int WM_DESTROY = 0x0002, WM_SIZE = 0x0005, WM_PAINT = 0x000F, WM_ERASEBKGND = 0x0014,
            WM_KEYDOWN = 0x0100, WM_KEYUP = 0x0101, WM_SYSKEYDOWN = 0x0104, WM_SYSKEYUP = 0x0105,
            WM_MOUSEMOVE = 0x0200, WM_LBUTTONDOWN = 0x0201, WM_LBUTTONUP = 0x0202, WM_RBUTTONDOWN = 0x0204,
            WM_RBUTTONUP = 0x0205, WM_MBUTTONDOWN = 0x0207, WM_MBUTTONUP = 0x0208, WM_MOUSEWHEEL = 0x020A,
            WM_MOUSEHWHEEL = 0x020E, WM_KILLFOCUS = 0x0008, WM_SETFOCUS = 0x0007, WM_TIMER = 0x0113,
            WM_DPICHANGED = 0x02E0, WM_SETCURSOR = 0x0020, WM_CONTEXTMENU = 0x007B,
            WM_UNDO = 0x0304, WM_REDO = 0x0305, WM_CUT = 0x0300, WM_COPY = 0x0301, WM_PASTE = 0x0302,
            WM_CLEAR = 0x0303, WM_APP_FRAME = 0x8001;
    private static final int MF_STRING = 0, MF_SEPARATOR = 0x800, MF_DISABLED = 2, MF_CHECKED = 8, MF_POPUP = 0x10,
            TPM_RETURNCMD = 0x0100, TPM_RIGHTBUTTON = 0x0002, TPM_NONOTIFY = 0x0080;
    private static final int SW_SHOW = 5;
    private static final long CS_VREDRAW = 1, CS_HREDRAW = 2, COLOR_WINDOW = 5;
    private static final int WS_OVERLAPPEDWINDOW = 0x00CF0000;
    private static final long CW_USEDEFAULT = 0x8000000L;
    private static final long TIMER_ID = 1;

    private final long hwnd;
    private final jui.render.d3d11.D3D11Renderer renderer;
    private final PlatformWindow.Painter painter;
    private final PlatformWindow.Input input;
    private final TextEngine text;
    private final Scene scene = new Scene();
    private int background = 0xFFFFFF;
    private double lastWidth, lastHeight, lastScale = 1;
    private long frames;
    private long firstCommitMicros;
    private boolean closed;
    private long firstFrameDoneMicros;
    private LongConsumer onFirstFrame;
    private boolean framePending;
    private long cursor = WinCursors.of(jui.ui.Cursor.DEFAULT);
    private boolean pointerInside;
    private boolean ctrlClick;
    private IntConsumer popupResult;

    private static MemorySegment windowProc;
    private static boolean classRegistered;

    public static WinWindow open(String title, double width, double height, int background, TextEngine text,
                                 PlatformWindow.Painter painter) {
        return open(title, width, height, background, text, painter, null);
    }

    public static WinWindow open(String title, double width, double height, int background, TextEngine text,
                                 PlatformWindow.Painter painter, PlatformWindow.Input input) {
        return new WinWindow(title, width, height, background, text, painter, input);
    }

    private WinWindow(String title, double width, double height, int background, TextEngine text,
                      PlatformWindow.Painter painter, PlatformWindow.Input input) {
        this.text = text;
        this.painter = painter;
        this.input = input;
        this.background = background;
        registerClass();
        MemorySegment titleZ = GLOBAL.allocateFrom(title + "\0", java.nio.charset.StandardCharsets.UTF_16LE);
        // A point is 1/96 inch of DPI; the asked size is in points on the
        // primary monitor's DPI, corrected by WM_DPICHANGED once shown.
        long w = Math.round(width), h = Math.round(height);
        hwnd = createWindow(WS_OVERLAPPEDWINDOW, titleZ, w, h);
        if (hwnd == 0) {
            throw new IllegalStateException("CreateWindowExW failed: " + getLastErrorMessage());
        }
        BY_HWND.put(hwnd, this);
        lastScale = scale();
        renderer = new jui.render.d3d11.D3D11Renderer(hwnd);
        show();
        requestFrame();
    }

    // The controls, from PlatformWindow.

    @Override
    public void requestFrame() {
        if (closed) return;
        framePending = true;
        WinApp.postMessage(hwnd, WM_APP_FRAME);
    }

    @Override
    public void armTimer(long delayNanos) {
        cancelTimer();
        if (closed) return;
        long ms = Math.max(1, delayNanos / 1_000_000);
        setTimer(hwnd, TIMER_ID, ms);
    }

    @Override
    public void cancelTimer() {
        if (!closed) killTimer(hwnd, TIMER_ID);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        BY_HWND.remove(hwnd);
        destroyWindow(hwnd);
        if (BY_HWND.isEmpty()) {
            WinApp.quit();
        }
    }

    @Override
    public void setCursor(jui.ui.Cursor c) {
        cursor = WinCursors.of(c);
        if (pointerInside) {
            WinCursors.set(cursor);
        }
    }

    @Override
    public void discardMarkedText() {
        // Windows' IMM posts its own messages; the engine's own composition
        // bookkeeping drops its marked text without a platform call.
    }

    @Override
    public void invalidateCharacterCoordinates() {
        // The composition window follows the caret by itself in this phase.
    }

    @Override
    public void popupMenu(MenuModel model, float x, float y, IntConsumer result) {
        cancelMenu();
        long menu = createPopupMenu();
        popupResult = result;
        int chosen = 0;
        try {
            appendMenu(menu, model);
            long[] screen = {Math.round(x), Math.round(y)};
            clientToScreen(hwnd, screen);
            setForegroundWindow(hwnd);
            chosen = trackPopupMenu(menu, screen[0], screen[1]);
        } finally {
            destroyMenu(menu);
            IntConsumer done = popupResult;
            popupResult = null;
            if (done != null) done.accept(chosen);
        }
    }

    @Override
    public void cancelMenu() {
        if (popupResult != null) {
            IntConsumer done = popupResult;
            popupResult = null;
            done.accept(0);
        }
    }

    @Override
    public void setBackground(int rgb) {
        background = rgb;
        requestFrame();
    }

    @Override
    public boolean dark() {
        return false; // this phase draws the light theme
    }

    @Override
    public long windowNumber() {
        return hwnd;
    }

    @Override
    public long refreshNanos() {
        return 16_666_667L; // a 60 Hz display, as well as Windows tells it
    }

    @Override
    public long firstCommitMicros() {
        return firstCommitMicros;
    }

    @Override
    public void onFirstFrame(LongConsumer then) {
        onFirstFrame = then;
        if (frames > 0 && then != null) {
            then.accept(firstFrameDoneMicros);
        }
    }

    /** Draws a frame, as the message loop asks for WM_APP_FRAME. */
    private void draw() {
        if (closed || renderer == null) return;
        if (FrameStats.ENABLED) FrameStats.beginDraw();
        long[] rect = clientRect();
        double width = rect[2], height = rect[3];
        double scale = scale();
        lastWidth = width;
        lastHeight = height;
        lastScale = scale;
        scene.begin((float) scale, text, renderer.atlas());
        painter.paint(scene, width, height);
        boolean first = frames == 0;
        if (first) {
            firstCommitMicros = jui.ffi.Libc.nowMicros();
        }
        long presentStarted = FrameStats.ENABLED ? System.nanoTime() : 0;
        boolean drew = renderer.render(scene, width, height, scale, background, false, first ? () -> {
            firstFrameDoneMicros = jui.ffi.Libc.nowMicros();
            if (onFirstFrame != null) onFirstFrame.accept(firstFrameDoneMicros);
        } : null);
        if (FrameStats.ENABLED) FrameStats.present(presentStarted);
        if (drew) {
            frames++;
        }
        framePending = false;
    }

    /** The client size, in points. */
    private long[] clientRect() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment r = arena.allocate(16);
            getClientRect(hwnd, r);
            return new long[] {0, 0, r.get(ValueLayout.JAVA_LONG, 0), r.get(ValueLayout.JAVA_LONG, 8)};
        }
    }

    private double scale() {
        int dpi = getDpiForWindow(hwnd);
        return dpi <= 0 ? 1 : dpi / 96.0;
    }

    // Input events, from the window proc.

    private void mouse(int kind, int button, long lParam) {
        if (input == null) return;
        double x = (short) (lParam & 0xFFFF), y = (short) ((lParam >>> 16) & 0xFFFF);
        int mods = WinKeys.mods();
        pointerInside = true;
        switch (kind) {
            case 0 -> input.pointerMove(x, y, mods);
            case 1 -> {
                if (button == 0) ctrlClick = (mods & jui.ui.Mod.CTRL) != 0;
                input.pointerDown(x, y, ctrlClick && button == 0 ? 1 : button, 1, mods);
            }
            default -> {
                int b = ctrlClick && button == 0 ? 1 : button;
                if (button == 0) ctrlClick = false;
                input.pointerUp(x, y, b, mods);
            }
        }
    }

    private void wheel(long wParam, int horizontal) {
        if (input == null) return;
        double delta = ((short) ((wParam >>> 16) & 0xFFFF)) / 120.0;
        double[] at = pointerInClient();
        // A wheel notch is 40 DIPs, and up shows what is above (as MyGO).
        if (horizontal != 0) input.scroll(at[0], at[1], delta * 40, 0, false, WinKeys.mods());
        else input.scroll(at[0], at[1], 0, -delta * 40, false, WinKeys.mods());
    }

    /** The pointer's place in client points, as wheel messages carry screen ones. */
    private double[] pointerInClient() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment p = arena.allocate(16);
            GET_CURSOR_POS_H.invoke(GET_CURSOR_POS_SYM, p);
            long x = p.get(ValueLayout.JAVA_LONG, 0), y = p.get(ValueLayout.JAVA_LONG, 8);
            SCREEN_TO_CLIENT_H.invoke(SCREEN_TO_CLIENT_SYM, hwnd, p);
            double scale = scale();
            return new double[] {p.get(ValueLayout.JAVA_LONG, 0) / scale, p.get(ValueLayout.JAVA_LONG, 8) / scale, x, y};
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void keyDown(long wParam, long lParam) {
        if (input == null) return;
        int vk = (int) (wParam & 0xFFFF);
        boolean repeat = (lParam & (1L << 30)) != 0;
        jui.ui.Key key = WinKeys.key(vk, mapChar(vk));
        int mods = WinKeys.mods();
        boolean text = input.textActive() && (mods & (jui.ui.Mod.CMD | jui.ui.Mod.CTRL)) == 0;
        if (text) {
            // Without an input method wired (this phase), a key types the
            // character it makes, as the engine's plain input mode.
            String typed = typedOf(vk, mods);
            if (!typed.isEmpty()) {
                input.textInsert(typed, -1, 0);
                return;
            }
        }
        input.keyDown(key, mods, repeat);
    }

    private void keyUp(long wParam) {
        if (input == null) return;
        int vk = (int) (wParam & 0xFFFF);
        input.keyUp(WinKeys.key(vk, mapChar(vk)), WinKeys.mods());
    }

    /** The unmodified character of a virtual key, for the OEM punctuation. */
    private static char mapChar(int vk) {
        return switch (vk) {
            case 0xBD -> '-';
            case 0xBB -> '=';
            case 0xBC -> ',';
            case 0xBE -> '.';
            case 0xBF -> '/';
            case 0xBA -> ';';
            case 0xDE -> '\'';
            case 0xDB -> '[';
            case 0xDD -> ']';
            case 0xDC -> '\\';
            case 0xC0 -> '`';
            default -> 0;
        };
    }

    /** What a key types without the input method, as MacWindow.typed drops control characters. */
    private static String typedOf(int vk, int mods) {
        if (vk >= 'A' && vk <= 'Z') {
            return String.valueOf((mods & jui.ui.Mod.SHIFT) == 0 ? (char) (vk - 'A' + 'a') : (char) vk);
        }
        if (vk >= '0' && vk <= '9') {
            return String.valueOf((char) vk);
        }
        if (vk == 0x20) {
            return " ";
        }
        char c = mapChar(vk);
        return c == 0 ? "" : String.valueOf(c);
    }

    // The window class and proc.

    private static void registerClass() {
        if (classRegistered) return;
        windowProc = Linker.nativeLinker().upcallStub(
                WINDOW_PROC_HANDLE, // bound once, below
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG), GLOBAL);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom("JuiWindow\0", java.nio.charset.StandardCharsets.UTF_16LE);
            // struct WNDCLASSEXW { UINT cbSize; UINT style; WNDPROC lpfnWndProc; int cbClsExtra;
            // int cbWndExtra; HINSTANCE hInstance; HICON hIcon; HCURSOR hCursor; HBRUSH hbrBackground;
            // LPCWSTR lpszMenuName; LPCWSTR lpszClassName; HICON hIconSm; } — 80 bytes, 8-aligned.
            MemorySegment wc = arena.allocate(80, 8);
            wc.set(ValueLayout.JAVA_INT, 0, 80);
            wc.set(ValueLayout.JAVA_INT, 4, (int) (CS_HREDRAW | CS_VREDRAW));
            wc.set(ValueLayout.JAVA_LONG, 8, windowProc.address());
            wc.set(ValueLayout.JAVA_LONG, 24, moduleHandle());
            wc.set(ValueLayout.JAVA_LONG, 48, COLOR_WINDOW + 1); // hbrBackground
            wc.set(ValueLayout.JAVA_LONG, 64, name.address());   // lpszClassName
            // The call state carries GetLastError past anything the runtime
            // calls between this failing and the message being built.
            MemorySegment state = arena.allocate(Linker.Option.captureStateLayout());
            if (registerClass(state, wc) == 0) {
                throw new IllegalStateException("RegisterClassExW failed: "
                        + registerError(state));
            }
        }
        classRegistered = true;
    }

    /** The process's own module: windows' class and creation need it. */
    private static long moduleHandle() {
        try {
            return (long) MODULE_HANDLE.invokeExact(GET_MODULE_HANDLE_W, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static final MemorySegment GET_MODULE_HANDLE_W = Win32FFI.symbol("kernel32", "GetModuleHandleW");
    // GetModuleHandleW(lpModuleName), NULL for the process's own module.
    private static final java.lang.invoke.MethodHandle MODULE_HANDLE = h(
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    private static final java.lang.invoke.MethodHandle WINDOW_PROC_HANDLE = bindWindowProc();

    private static java.lang.invoke.MethodHandle bindWindowProc() {
        try {
            return java.lang.invoke.MethodHandles.lookup().findStatic(WinWindow.class, "windowProc",
                    java.lang.invoke.MethodType.methodType(long.class, long.class, long.class, long.class, long.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** The window proc: paints, sizes, input and menus, as AppKit's view callbacks do. */
    private static long windowProc(long hwnd, long message, long wParam, long lParam) {
        WinWindow w = BY_HWND.get(hwnd);
        switch ((int) message) {
            case WM_APP_FRAME:
                if (w != null) w.draw();
                return 0;
            case WM_PAINT:
                if (w != null && w.framePending) w.draw();
                validatePaint(hwnd);
                return 0;
            case WM_ERASEBKGND:
                return 1; // D3D clears the whole window
            case WM_SIZE, WM_DPICHANGED:
                if (w != null) w.requestFrame();
                return 0;
            case WM_SETCURSOR:
                if (w != null && (lParam & 0xFFFF) == 1) { // HTCLIENT
                    WinCursors.set(w.cursor);
                    return 1;
                }
                break;
            case WM_MOUSEMOVE:
                if (w != null) w.mouse(0, 0, lParam);
                return 0;
            case WM_LBUTTONDOWN: if (w != null) w.mouse(1, 0, lParam); return 0;
            case WM_LBUTTONUP: if (w != null) w.mouse(2, 0, lParam); return 0;
            case WM_RBUTTONDOWN: if (w != null) w.mouse(1, 1, lParam); return 0;
            case WM_RBUTTONUP: if (w != null) w.mouse(2, 1, lParam); return 0;
            case WM_MBUTTONDOWN: if (w != null) w.mouse(1, 2, lParam); return 0;
            case WM_MBUTTONUP: if (w != null) w.mouse(2, 2, lParam); return 0;
            case WM_MOUSEWHEEL: if (w != null) w.wheel(wParam, 0); return 0;
            case WM_MOUSEHWHEEL: if (w != null) w.wheel(wParam, 1); return 0;
            case WM_KEYDOWN, WM_SYSKEYDOWN:
                if (w != null) w.keyDown(wParam, lParam);
                return 0;
            case WM_KEYUP, WM_SYSKEYUP:
                if (w != null) w.keyUp(wParam);
                return 0;
            case WM_SETFOCUS: if (w != null && w.input != null) w.input.windowFocusChanged(true); return 0;
            case WM_KILLFOCUS: if (w != null && w.input != null) w.input.windowFocusChanged(false); return 0;
            case WM_TIMER:
                if (w != null && wParam == TIMER_ID) {
                    w.cancelTimer();
                    w.requestFrame();
                }
                return 0;
            case WM_CONTEXTMENU:
                return 0; // the engine pops its own menus through popupMenu
            case WM_UNDO: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.UNDO); return 0;
            case WM_REDO: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.REDO); return 0;
            case WM_CUT: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.CUT); return 0;
            case WM_COPY: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.COPY); return 0;
            case WM_PASTE: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.PASTE); return 0;
            case WM_CLEAR: if (w != null && w.input != null) w.input.editCommand(jui.ui.EditCommand.DELETE); return 0;
            case WM_DESTROY:
                if (w != null) {
                    w.closed = true;
                    BY_HWND.remove(hwnd);
                    if (w.input != null) w.input.windowClosed();
                }
                return 0;
            default:
                break;
        }
        return defWindowProc(hwnd, message, wParam, lParam);
    }

    // FFI call wrappers, one signature each.

    private static final java.lang.invoke.MethodHandle REGISTER = Linker.nativeLinker().downcallHandle(
            FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.ADDRESS),
            Linker.Option.captureCallState("GetLastError"));
    // CreateWindowExW(exStyle, className, windowName, style, x, y, width, height, parent, menu, instance, param)
    private static final java.lang.invoke.MethodHandle CREATE = h(FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle DEFWPROC = h(FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle SHOW = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final java.lang.invoke.MethodHandle UPDATE = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle DESTROY = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle GETRECT = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle GETDPI = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle SETTIMER = h(FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle KILLTIMER = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle BEGINPAINT = h(FunctionDescriptor.of(ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle ENDPAINT = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle CREATEPOPUP = h(FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle APPENDMENU = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle TRACKPOPUP = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle DESTROYMENU = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle CLIENTTOSCREEN = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle SETFOREGROUND = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG));
    private static final java.lang.invoke.MethodHandle GET_CURSOR_POS_H = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.ADDRESS));
    private static final java.lang.invoke.MethodHandle SCREEN_TO_CLIENT_H = h(FunctionDescriptor.of(ValueLayout.JAVA_INT,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));

    private static java.lang.invoke.MethodHandle h(FunctionDescriptor d) {
        return Linker.nativeLinker().downcallHandle(d);
    }

    private static MemorySegment GetModuleHandleW() {
        return GET_MODULE_HANDLE_W;
    }

    /** RegisterClassExW with GetLastError captured into {@code state}. */
    private static short registerClass(MemorySegment state, MemorySegment wc) {
        try {
            return (short) REGISTER.invokeExact(REGISTER_CLASS_W, state, wc);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** The GetLastError a captured call state holds, formatted; for its message. */
    private static String registerError(MemorySegment state) {
        int code = state.get(ValueLayout.JAVA_INT, Linker.Option.captureStateLayout().byteOffset(
                MemoryLayout.PathElement.groupElement("GetLastError")));
        return code == 0 ? "no error recorded" : lastErrorMessage(code);
    }

    private long createWindow(int style, MemorySegment title, long w, long h) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cls = arena.allocateFrom("JuiWindow\0", java.nio.charset.StandardCharsets.UTF_16LE);
            return (long) CREATE.invokeExact(CREATE_WINDOW_EX_W, 0L, cls, title, (long) style,
                    CW_USEDEFAULT, CW_USEDEFAULT, w, h, 0L, 0L, moduleHandle(), 0L);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void show() {
        try {
            int shown = (int) SHOW.invokeExact(SHOW_WINDOW, hwnd, SW_SHOW);
            boolean updated = (int) UPDATE.invokeExact(UPDATE_WINDOW, hwnd) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void validatePaint(long hwnd) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ps = arena.allocate(72, 8); // PAINTSTRUCT
            long hdc = (long) BEGINPAINT.invoke(BEGIN_PAINT, hwnd, ps);
            int painted = (int) ENDPAINT.invoke(END_PAINT, hwnd, ps);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void getClientRect(long hwnd, MemorySegment rect) {
        try {
            boolean got = (int) GETRECT.invokeExact(GET_CLIENT_RECT, hwnd, rect) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static int getDpiForWindow(long hwnd) {
        try {
            return (int) GETDPI.invokeExact(GET_DPI_FOR_WINDOW, hwnd);
        } catch (Throwable t) {
            return 96;
        }
    }

    private static void setTimer(long hwnd, long id, long ms) {
        try {
            long set = (long) SETTIMER.invokeExact(SET_TIMER, hwnd, id, ms, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void killTimer(long hwnd, long id) {
        try {
            int killed = (int) KILLTIMER.invokeExact(KILL_TIMER, hwnd, id);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void destroyWindow(long hwnd) {
        try {
            int destroyed = (int) DESTROY.invokeExact(DESTROY_WINDOW, hwnd);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long defWindowProc(long hwnd, long message, long wParam, long lParam) {
        try {
            return (long) DEFWPROC.invokeExact(DEF_WINDOW_PROC_W, hwnd, message, wParam, lParam);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static long createPopupMenu() {
        try {
            return (long) CREATEPOPUP.invokeExact(CREATE_POPUP_MENU);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** The model's items, with separators and submenus; TrackPopupMenu returns the item's own id. */
    private static void appendMenu(long menu, MenuModel model) {
        try (Arena arena = Arena.ofConfined()) {
            for (MenuModel.Item item : model.items()) {
                if (item.kind() == MenuModel.Kind.SEPARATOR) {
                    APPENDMENU.invoke(APPEND_MENU_W, menu, (long) MF_SEPARATOR, 0L, MemorySegment.NULL);
                    continue;
                }
                long flags = MF_STRING;
                if (!item.enabled()) flags |= MF_DISABLED;
                if (item.checked()) flags |= MF_CHECKED;
                if (item.kind() == MenuModel.Kind.SUBMENU) {
                    long sub = createPopupMenu();
                    appendMenu(sub, new MenuModel(item.children()));
                    flags |= MF_POPUP;
                    MemorySegment label = arena.allocateFrom(item.label() + "\0", java.nio.charset.StandardCharsets.UTF_16LE);
                    APPENDMENU.invoke(APPEND_MENU_W, menu, flags, sub, label);
                    continue;
                }
                String text = item.label() + (item.shortcut() == null ? "" : "\t" + shortcutLabel(item.shortcut()));
                MemorySegment label = arena.allocateFrom(text + "\0", java.nio.charset.StandardCharsets.UTF_16LE);
                APPENDMENU.invoke(APPEND_MENU_W, menu, flags, (long) item.id(), label);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** A shortcut as a menu label, Ctrl+Shift+P: the engine's CMD is Windows' Ctrl. */
    private static String shortcutLabel(jui.ui.Shortcut s) {
        StringBuilder b = new StringBuilder();
        if ((s.mods() & jui.ui.Mod.CTRL) != 0) b.append("Ctrl+");
        if ((s.mods() & jui.ui.Mod.SHIFT) != 0) b.append("Shift+");
        if ((s.mods() & jui.ui.Mod.ALT) != 0) b.append("Alt+");
        if ((s.mods() & jui.ui.Mod.CMD) != 0) b.append("Ctrl+");
        return b.append(s.key().name().toLowerCase(java.util.Locale.ROOT)).toString();
    }


    private int trackPopupMenu(long menu, long x, long y) {
        try {
            return (int) TRACKPOPUP.invokeExact(TRACK_POPUP_MENU_EX, menu,
                    (long) (TPM_RETURNCMD | TPM_RIGHTBUTTON | TPM_NONOTIFY), 0L, 0L, hwnd, MemorySegment.NULL);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void destroyMenu(long menu) {
        try {
            boolean destroyed = (int) DESTROYMENU.invokeExact(DESTROY_MENU, menu) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void clientToScreen(long hwnd, long[] point) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment p = arena.allocate(8); // POINT: two 32-bit LONGs
            p.set(ValueLayout.JAVA_INT, 0, (int) point[0]);
            p.set(ValueLayout.JAVA_INT, 4, (int) point[1]);
            boolean converted = (int) CLIENTTOSCREEN.invokeExact(CLIENT_TO_SCREEN, hwnd, p) != 0;
            point[0] = p.get(ValueLayout.JAVA_INT, 0);
            point[1] = p.get(ValueLayout.JAVA_INT, 4);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setForegroundWindow(long hwnd) {
        try {
            boolean foreground = (int) SETFOREGROUND.invokeExact(SET_FOREGROUND_WINDOW, hwnd) != 0;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    /** GetLastError right after the failed call, as a message; the code alone
     * when FormatMessage has nothing. Called before anything else can reset it. */
    private static String getLastErrorMessage() {
        try {
            int code = (int) GET_LAST_ERROR.invokeExact(GET_LAST_ERROR_SYM);
            return code == 0 ? "no error recorded" : lastErrorMessage(code);
        } catch (Throwable t) {
            return "GetLastError failed: " + t;
        }
    }

    /** A Win32 error code spelled out, FormatMessageW's text when it has one. */
    private static String lastErrorMessage(int code) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment text = arena.allocate(1024 * 2L);
            int chars = (int) FORMAT_MESSAGE.invokeExact(FORMAT_MESSAGE_SYM,
                    (long) (FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS),
                    MemorySegment.NULL, code & 0xFFFFFFFFL, text, 1024, MemorySegment.NULL);
            if (chars > 0) {
                StringBuilder message = new StringBuilder();
                for (int i = 0; i < chars; i++) {
                    message.append((char) text.get(ValueLayout.JAVA_SHORT, 2L * i));
                }
                return code + " (" + message.toString().trim() + ")";
            }
        } catch (Throwable t) {
            // fall through to the code alone
        }
        return "error " + code;
    }

    private static final int FORMAT_MESSAGE_FROM_SYSTEM = 0x1000, FORMAT_MESSAGE_IGNORE_INSERTS = 0x200;
    private static final MemorySegment GET_LAST_ERROR_SYM = Win32FFI.symbol("kernel32", "GetLastError");
    private static final java.lang.invoke.MethodHandle GET_LAST_ERROR = h(
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
    private static final MemorySegment FORMAT_MESSAGE_SYM = Win32FFI.symbol("kernel32", "FormatMessageW");
    // FormatMessageW(flags, source, code, language, buffer, size, inserts)
    private static final java.lang.invoke.MethodHandle FORMAT_MESSAGE = h(FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
}
