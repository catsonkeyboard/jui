package jui.render.d3d11;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

import jui.ffi.Native;
import jui.ffi.Win32;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * The FFM plumbing of the D3D11 renderer: DLL symbols, COM vtable calls,
 * and the typed downcalls whose signatures are not plain words.
 * {@link jui.ffi.Sigs} stays macOS's.
 */
final class D3D {
    private D3D() {}

    static final AddressLayout P = ValueLayout.ADDRESS.withTargetLayout(ValueLayout.JAVA_LONG);

    static final MemorySegment CREATE_DEVICE_FN = Win32.symbol("d3d11", "D3D11CreateDevice");
    static final MemorySegment CREATE_FACTORY_FN = Win32.symbol("dxgi", "CreateDXGIFactory1");
    static final MemorySegment COMPILE_FN = Win32.symbol("d3dcompiler_47", "D3DCompile");

    // D3D11CreateDevice(IDXGIAdapter*, D3D_DRIVER_TYPE, HMODULE, UINT, D3D_FEATURE_LEVEL*, UINT,
    //                   UINT, ID3D11Device**, D3D_FEATURE_LEVEL*, ID3D11DeviceContext**)
    private static final MethodHandle CREATE_DEVICE_H = Linker.nativeLinker().downcallHandle(
            CREATE_DEVICE_FN,
            FunctionDescriptor.of(JAVA_INT, P, JAVA_INT, P, JAVA_INT, P, JAVA_INT, JAVA_INT, P, P, P));

    // CreateDXGIFactory1(REFIID, void**)
    private static final MethodHandle CREATE_FACTORY_H = Linker.nativeLinker().downcallHandle(
            CREATE_FACTORY_FN, FunctionDescriptor.of(JAVA_INT, P, P));

    // D3DCompile(LPCVOID, SIZE_T, LPCSTR, const D3D_SHADER_MACRO*, ID3DInclude*, LPCSTR, LPCSTR,
    //            UINT Flags1, UINT Flags2, ID3DBlob**, ID3DBlob**)
    private static final MethodHandle COMPILE_H = Linker.nativeLinker().downcallHandle(
            COMPILE_FN,
            FunctionDescriptor.of(JAVA_INT, P, ValueLayout.JAVA_LONG, P, P, P, P, P,
                    JAVA_INT, JAVA_INT, P, P));

    static int createDevice(MemorySegment adapter, int driverType, int flags,
            MemorySegment featureLevels, int featureLevelsCount, int sdkVersion,
            MemorySegment outDevice, MemorySegment outFeatureLevel, MemorySegment outContext) {
        try {
            return (int) CREATE_DEVICE_H.invokeExact(adapter, driverType, MemorySegment.NULL, flags,
                    featureLevels, featureLevelsCount, sdkVersion, outDevice, outFeatureLevel, outContext);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static int createFactory(MemorySegment riid, MemorySegment outFactory) {
        try {
            return (int) CREATE_FACTORY_H.invokeExact(riid, outFactory);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    static int compile(MemorySegment source, long sourceLength, MemorySegment sourceName,
            MemorySegment entryPoint, MemorySegment target, MemorySegment outCode, MemorySegment outErrors) {
        try {
            return (int) COMPILE_H.invokeExact(source, sourceLength, sourceName, MemorySegment.NULL,
                    MemorySegment.NULL, entryPoint, target, 1, 0, outCode, outErrors);
        } catch (Throwable t) {
            throw Native.rethrow(t);
        }
    }

    /**
     * Calls slot {@code index} of the COM object's vtable: the object
     * pointer is this — the first word the method receives — and every
     * parameter is a word (all of D3D11's are: pointers, enums and flags).
     */
    static long com(long object, int index, long... args) {
        if (object == 0) {
            throw new IllegalStateException("null COM object, vtable slot " + index);
        }
        long vtable = MemorySegment.ofAddress(object).reinterpret(8).get(P, 0).address();
        MemorySegment fn = MemorySegment.ofAddress(vtable).reinterpret(8L * (index + 1)).getAtIndex(P, index);
        return switch (args.length) {
            case 0 -> Native.call(fn, object);
            case 1 -> Native.call(fn, object, args[0]);
            case 2 -> Native.call(fn, object, args[0], args[1]);
            case 3 -> Native.call(fn, object, args[0], args[1], args[2]);
            case 4 -> Native.call(fn, object, args[0], args[1], args[2], args[3]);
            case 5 -> Native.call(fn, object, args[0], args[1], args[2], args[3], args[4]);
            case 6 -> Native.call(fn, object, args[0], args[1], args[2], args[3], args[4], args[5]);
            default -> throw new IllegalArgumentException("vtable call with " + args.length + " args");
        };
    }
}
