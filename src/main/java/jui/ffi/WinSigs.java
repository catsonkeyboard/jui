package jui.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.util.ArrayList;
import java.util.List;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

/**
 * Every native signature the Windows side calls, or is called back with:
 * Win32, DirectWrite and Direct3D, as {@link Sigs} holds the macOS ones.
 * Each entry is the exact shape a handle in the platform layer builds, so
 * native-image's registrations cover every call the image will make.
 */
public final class WinSigs {
    private WinSigs() {}

    public static final List<FunctionDescriptor> DOWNCALLS = new ArrayList<>();
    public static final List<FunctionDescriptor> UPCALLS = new ArrayList<>();

    private static FunctionDescriptor down(FunctionDescriptor fd) {
        DOWNCALLS.add(fd);
        return fd;
    }

    private static FunctionDescriptor up(FunctionDescriptor fd) {
        UPCALLS.add(fd);
        return fd;
    }

    // ---- WinApp: the message loop ----

    /** GetMessageW(msg, hwnd, min, max). */
    public static final FunctionDescriptor GET_MESSAGE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** PostMessageW(hwnd, message, wParam, lParam). */
    public static final FunctionDescriptor POST_MESSAGE = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, JAVA_INT, JAVA_LONG, JAVA_LONG));
    /** PostThreadMessageW(thread, message, wParam, lParam). */
    public static final FunctionDescriptor POST_THREAD_MESSAGE = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG));
    /** DispatchMessageW(msg). */
    public static final FunctionDescriptor DISPATCH_MESSAGE = down(FunctionDescriptor.of(
            JAVA_LONG, ADDRESS));
    /** TranslateMessage(msg). */
    public static final FunctionDescriptor TRANSLATE_MESSAGE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS));
    /** GetCurrentThreadId(). */
    public static final FunctionDescriptor THREAD_ID = down(FunctionDescriptor.of(
            JAVA_INT));

    // ---- WinWindow: the window, its class, menus and diagnostics ----

    /** GetModuleHandleW(name). */
    public static final FunctionDescriptor GET_MODULE_HANDLE = down(FunctionDescriptor.of(
            JAVA_LONG, ADDRESS));
    /** RegisterClassExW(wc), its handle capturing GetLastError. */
    public static final FunctionDescriptor REGISTER_CLASS = down(FunctionDescriptor.of(
            JAVA_SHORT, ADDRESS));
    /** CreateWindowExW(exStyle, class, name, style, x, y, w, h, parent, menu, instance, param). */
    public static final FunctionDescriptor CREATE_WINDOW = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG,
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** DefWindowProcW(hwnd, message, wParam, lParam). */
    public static final FunctionDescriptor DEF_WINDOW_PROC = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** ShowWindow(hwnd, cmd). */
    public static final FunctionDescriptor SHOW_WINDOW = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, JAVA_INT));
    /** UpdateWindow(hwnd); also DestroyWindow, GetDpiForWindow, DestroyMenu, SetForegroundWindow. */
    public static final FunctionDescriptor INT_LONG = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG));
    /** GetClientRect(hwnd, rect); also ScreenToClient, EndPaint. */
    public static final FunctionDescriptor INT_LONG_ADDRESS = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, ADDRESS));
    /** SetTimer(hwnd, id, ms, proc). */
    public static final FunctionDescriptor SET_TIMER = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS));
    /** KillTimer(hwnd, id); also GetKeyState-like (short, int) shapes live below. */
    public static final FunctionDescriptor INT_LONG_LONG = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, JAVA_LONG));
    /** BeginPaint(hwnd, ps). */
    public static final FunctionDescriptor BEGIN_PAINT = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, ADDRESS));
    /** CreatePopupMenu(). */
    public static final FunctionDescriptor CREATE_POPUP_MENU = down(FunctionDescriptor.of(
            JAVA_LONG));
    /** AppendMenuW(menu, flags, id, label). */
    public static final FunctionDescriptor APPEND_MENU = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS));
    /** TrackPopupMenu(menu, flags, x, y, reserved, hwnd, rect). */
    public static final FunctionDescriptor TRACK_POPUP_MENU = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS));
    /** GetCursorPos(point). */
    public static final FunctionDescriptor GET_CURSOR_POS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS));
    /** GetLastError(). */
    public static final FunctionDescriptor GET_LAST_ERROR = down(FunctionDescriptor.of(
            JAVA_INT));
    /** FormatMessageW(flags, source, code, language, buffer, size, inserts). */
    public static final FunctionDescriptor FORMAT_MESSAGE = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_LONG, ADDRESS, JAVA_LONG, JAVA_LONG, ADDRESS, JAVA_LONG, ADDRESS));
    /** WinWindow's windowProc(hwnd, message, wParam, lParam). */
    public static final FunctionDescriptor WINDOW_PROC = up(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG, JAVA_LONG));

    // Shared shapes, named by what they carry.

    /** SetCursor(cursor). */
    public static final FunctionDescriptor LONG_FROM_LONG = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG));
    /** TranslateMessage(msg); GetCursorPos(point). */
    public static final FunctionDescriptor INT_FROM_ADDRESS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS));
    /** BeginPaint(hwnd, ps). */
    public static final FunctionDescriptor LONG_FROM_LONG_LONG_ADDRESS = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, JAVA_LONG, ADDRESS));
    /** CreatePopupMenu(). */
    public static final FunctionDescriptor LONG_FROM_VOID = down(FunctionDescriptor.of(
            JAVA_LONG));

    // ---- WinCursors, WinKeys, WinClipboard ----

    /** LoadCursorW(instance, name); also SetCursor(cursor), OpenClipboard/CloseClipboard shapes. */
    public static final FunctionDescriptor LONG_LONG = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG));
    /** GetClipboardData/EmptyClipboard-adjacent three-word shapes: GetClipboardData(uFormat). */
    public static final FunctionDescriptor LONG_LONG_LONG = down(FunctionDescriptor.of(
            JAVA_LONG, JAVA_LONG, JAVA_LONG));
    /** MapVirtualKeyW(code, type). */
    public static final FunctionDescriptor SHORT_INT = down(FunctionDescriptor.of(
            JAVA_SHORT, JAVA_INT));

    // ---- DirectWrite: text layout and rasterization ----

    /** DWriteCreateFactory(kind, iid, out). */
    public static final FunctionDescriptor DWRITE_CREATE_FACTORY = down(FunctionDescriptor.of(
            JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
    /** IDWriteFactory::GetSystemFontCollection(out, check). */
    public static final FunctionDescriptor CALL_OUT_WORD = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    /** IDWriteTextFormat::SetWordWrapping(mode). */
    public static final FunctionDescriptor CALL_WORD = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT));
    /** IDWriteFactory::CreateTextFormat(family, collection, weight, style, stretch, size, locale, out). */
    public static final FunctionDescriptor CREATE_TEXT_FORMAT = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_FLOAT, ADDRESS, ADDRESS));
    /** IDWriteFactory::CreateTextLayout(text, length, format, maxWidth, maxHeight, out). */
    public static final FunctionDescriptor CREATE_TEXT_LAYOUT = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_FLOAT, ADDRESS));
    /** IDWriteTextLayout::GetLineMetrics(lines, capacity, actual). */
    public static final FunctionDescriptor GET_LINE_METRICS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
    /** IDWriteTextLayout::HitTestTextPosition(index, trailing, x, y, inside). */
    public static final FunctionDescriptor HIT_TEST_TEXT_POSITION = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    /** IDWriteFactory::CreateGlyphRunAnalysis(run, ppdip, transform, rendering, measuring, x, y, out). */
    public static final FunctionDescriptor CREATE_GLYPH_RUN_ANALYSIS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_FLOAT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_FLOAT,
            JAVA_FLOAT, ADDRESS));
    /** IDWriteTextAnalyzer::GetGlyphs, its long parameter row. */
    public static final FunctionDescriptor GET_GLYPHS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS,
            ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
    /** IDWriteTextAnalyzer::GetGlyphPlacements. */
    public static final FunctionDescriptor GET_GLYPH_PLACEMENTS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT,
            ADDRESS, JAVA_FLOAT, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT,
            ADDRESS, ADDRESS));
    /** IDWriteFontFace::GetDesignGlyphMetrics(indices, count, metrics, sideways). */
    public static final FunctionDescriptor GET_DESIGN_GLYPH_METRICS = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
    /** IDWriteGlyphRunAnalysis::CreateAlphaTexture(type, rect, buffer, size). */
    public static final FunctionDescriptor CREATE_ALPHA_TEXTURE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
    /** IDWriteTextLayout::SetFontFamilyName(family, range as value). */
    public static final FunctionDescriptor SET_FONT_FAMILY_NAME = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG));
    /** IDWriteTextLayout::SetFontWeight(weight, range as value). */
    public static final FunctionDescriptor SET_FONT_WEIGHT = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    /** IDWriteTextLayout::SetFontStyle(style, range as value). */
    public static final FunctionDescriptor SET_FONT_STYLE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, JAVA_LONG));
    /** IDWriteTextLayout::SetFontSize(size, range as value). */
    public static final FunctionDescriptor SET_FONT_SIZE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_FLOAT, JAVA_LONG));
    /** IDWriteFontFace::GetMetrics(metrics), void. */
    public static final FunctionDescriptor GET_FONT_METRICS = down(FunctionDescriptor.ofVoid(
            ADDRESS, ADDRESS));
    /** IDWriteFactory::CreateFontFace(type, files, count, indices, simulations, out). */
    public static final FunctionDescriptor CREATE_FONT_FACE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    /** IDWriteFactory::CreateTextAnalyzer(out). */
    public static final FunctionDescriptor CREATE_TEXT_ANALYZER = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS));
    /** IUnknown::Release(). */
    public static final FunctionDescriptor RELEASE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS));

    // ---- Direct3D 11: device, compile, factory ----

    /** D3D11CreateDevice(adapter, driver, software, flags, levels, count, sdk, device, level, context). */
    public static final FunctionDescriptor D3D_CREATE_DEVICE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT,
            ADDRESS, ADDRESS, ADDRESS));
    /** CreateDXGIFactory1(iid, out). */
    public static final FunctionDescriptor CREATE_DXGI_FACTORY = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, ADDRESS));
    /** D3DCompile(source, size, name, defines, include, entry, target, flags1, flags2, code, errors). */
    public static final FunctionDescriptor D3D_COMPILE = down(FunctionDescriptor.of(
            JAVA_INT, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS,
            JAVA_INT, JAVA_INT, ADDRESS, ADDRESS));
}
