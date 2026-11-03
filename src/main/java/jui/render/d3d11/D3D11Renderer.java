package jui.render.d3d11;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import jui.ffi.Native;
import jui.render.Scene;
import jui.text.GlyphAtlas;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

/**
 * Draws a {@link Scene} with Direct3D 11 into the window {@code hwnd} it is
 * given, as the Metal renderer draws into a CAMetalLayer: one pipeline, one
 * instanced draw of quads (a batch a clip), and one shader, the HLSL port
 * of shader.metal. All FFM: no JNI, no C.
 */
public final class D3D11Renderer {
    private static final int ATLAS_SIZE = 1024;

    // DXGI formats and constants.
    private static final int DXGI_FORMAT_UNKNOWN = 0;
    private static final int DXGI_FORMAT_R8_UNORM = 61;
    private static final int DXGI_FORMAT_B8G8R8A8_UNORM = 87;
    private static final int DXGI_SWAP_EFFECT_DISCARD = 0;
    private static final int DXGI_SWAP_EFFECT_FLIP_SEQUENTIAL = 3;
    private static final int DXGI_USAGE_RENDER_TARGET_OUTPUT = 0x20;

    // D3D11 enums.
    private static final int D3D11_SDK_VERSION = 7;
    private static final int D3D11_DRIVER_TYPE_HARDWARE = 1;
    private static final int USAGE_DEFAULT = 0;
    private static final int USAGE_DYNAMIC = 2;
    private static final int CPU_ACCESS_WRITE = 0x10000;
    private static final int BIND_SHADER_RESOURCE = 8;
    private static final int BIND_RENDER_TARGET = 0x20;
    private static final int BIND_CONSTANT_BUFFER = 4;
    private static final int MISC_BUFFER_STRUCTURED = 0x40;
    private static final int D3D11_MAP_WRITE_DISCARD = 4;
    private static final int D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP = 5;
    private static final int D3D11_BLEND_ONE = 2;
    private static final int D3D11_BLEND_INV_SRC_ALPHA = 6;
    private static final int D3D11_BLEND_OP_ADD = 1;
    private static final int D3D11_SRV_DIMENSION_TEXTURE2D = 4;
    private static final int D3D11_SRV_DIMENSION_BUFFER = 1;

    // Struct sizes in bytes (all fields 4-byte-aligned in x64 ABI packing).
    private static final int SIZEOF_DESC_BUFFER = 24;        // D3D11_BUFFER_DESC: 6 UINT
    private static final int SIZEOF_DESC_TEXTURE2D = 44;     // D3D11_TEXTURE2D_DESC: 11 UINTs
    private static final int SIZEOF_DESC_SRV = 24;           // Format, dimension, 16-byte union
    private static final int SIZEOF_DESC_BLEND = 264;        // 2 UINT + 8 targets of 8 UINT
    private static final int SIZEOF_MAPPED_SUBRESOURCE = 24; // 3 pointers
    private static final int SIZEOF_BOX = 24;                // 6 UINT
    private static final int SIZEOF_DESC_SWAPCHAIN = 72;     // DXGI_SWAP_CHAIN_DESC: 68 bytes, 8-aligned

    /** COM vtable slots, the method each index calls, as d3d11.h orders them. */
    private static final int COM_QUERY_INTERFACE = 0, COM_ADD_REF = 1, COM_RELEASE = 2;
    private static final int DEV_CREATE_BUFFER = 3;                       // ID3D11Device::CreateBuffer
    private static final int DEV_CREATE_TEXTURE_2D = 5;                    // ID3D11Device::CreateTexture2D
    private static final int DEV_CREATE_SHADER_RESOURCE_VIEW = 7;          // ID3D11Device::CreateShaderResourceView
    private static final int DEV_CREATE_RENDER_TARGET_VIEW = 9;            // ID3D11Device::CreateRenderTargetView
    private static final int DEV_CREATE_VERTEX_SHADER = 12;                // ID3D11Device::CreateVertexShader
    private static final int DEV_CREATE_PIXEL_SHADER = 15;                 // ID3D11Device::CreatePixelShader
    private static final int DEV_CREATE_BLEND_STATE = 20;                  // ID3D11Device::CreateBlendState
    // The context's slots sit 4 above the raw method order: it inherits
    // ID3D11DeviceChild's GetDevice and the private-data setters first.
    private static final int CTX_VS_SET_CONSTANT_BUFFERS = 7;              // ID3D11DeviceContext::VSSetConstantBuffers
    private static final int CTX_VS_SET_SHADER_RESOURCES = 8;              // ID3D11DeviceContext::VSSetShaderResources
    private static final int CTX_PS_SET_SHADER_RESOURCES = 8;              // ID3D11DeviceContext::PSSetShaderResources
    private static final int CTX_PS_SET_SHADER = 9;                        // ID3D11DeviceContext::PSSetShader
    private static final int CTX_VS_SET_SHADER = 11;                       // ID3D11DeviceContext::VSSetShader
    private static final int CTX_MAP = 14;                                 // ID3D11DeviceContext::Map
    private static final int CTX_UNMAP = 15;                               // ID3D11DeviceContext::Unmap
    private static final int CTX_PS_SET_CONSTANT_BUFFERS = 16;             // ID3D11DeviceContext::PSSetConstantBuffers
    private static final int CTX_IA_SET_INPUT_LAYOUT = 17;                 // ID3D11DeviceContext::IASetInputLayout
    private static final int CTX_DRAW_INSTANCED = 21;                      // ID3D11DeviceContext::DrawInstanced
    private static final int CTX_IA_SET_PRIMITIVE_TOPOLOGY = 24;           // ID3D11DeviceContext::IASetPrimitiveTopology
    private static final int CTX_OM_SET_RENDER_TARGETS = 33;               // ID3D11DeviceContext::OMSetRenderTargets
    private static final int CTX_OM_SET_BLEND_STATE = 35;                  // ID3D11DeviceContext::OMSetBlendState
    private static final int CTX_RS_SET_VIEWPORTS = 44;                    // ID3D11DeviceContext::RSSetViewports
    private static final int CTX_RS_SET_SCISSOR_RECTS = 45;                // ID3D11DeviceContext::RSSetScissorRects
    private static final int CTX_UPDATE_SUBRESOURCE = 48;                  // ID3D11DeviceContext::UpdateSubresource
    private static final int CTX_CLEAR_RENDER_TARGET_VIEW = 50;            // ID3D11DeviceContext::ClearRenderTargetView
    private static final int SWAP_CHAIN_PRESENT = 8;                       // IDXGISwapChain::Present, after IDXGIObject's and IDXGIDeviceSubObject's
    private static final int SWAP_CHAIN_GET_BUFFER = 9;                    // IDXGISwapChain::GetBuffer
    private static final int SWAP_CHAIN_RESIZE_BUFFERS = 13;               // IDXGISwapChain::ResizeBuffers
    private static final int FACTORY_CREATE_SWAP_CHAIN = 10;               // IDXGIFactory::CreateSwapChain, after IDXGIObject's slots
    private static final int BLOB_GET_BUFFER_POINTER = 3;                  // ID3DBlob::GetBufferPointer
    private static final int BLOB_GET_BUFFER_SIZE = 4;                     // ID3DBlob::GetBufferSize

    /** Instances the buffer first holds. */
    private static final int CAPACITY = 1024;

    private final long hwnd;
    private final long device;
    private final long context;
    private final long factory;
    private final long swapChain;
    private long backBuffer;   // ID3D11Texture2D
    private long rtv;          // ID3D11RenderTargetView
    private final long vertexShader;
    private final long pixelShader;
    private final long blendState;
    private final long constantBuffer;
    private final MemorySegment globals; // width, height, instanceOffset, pad
    private long instanceBuffer;         // one dynamic buffer only: MAP_WRITE_DISCARD already keeps frames apart
    private long instanceSrv;
    private int capacity = CAPACITY;
    private final GlyphAtlas atlas;
    private long atlasTexture;
    private long atlasSrv;
    private int atlasGeneration = -1;
    private final int[] dirty = new int[4];
    private int drawn;
    private final int[] scissor = new int[4];
    private long targetWidth, targetHeight; // pixels, 0 until the first resize

    public D3D11Renderer(long hwnd) {
        this(hwnd, new GlyphAtlas(ATLAS_SIZE, ATLAS_SIZE, 4096));
    }

    private D3D11Renderer(long hwnd, GlyphAtlas atlas) {
        this.atlas = atlas;
        this.hwnd = hwnd;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outDevice = arena.allocate(JAVA_LONG), outContext = arena.allocate(JAVA_LONG),
                    outFactory = arena.allocate(JAVA_LONG);
            int hr = D3D.createDevice(MemorySegment.NULL, D3D11_DRIVER_TYPE_HARDWARE, 0,
                    MemorySegment.NULL, 0, D3D11_SDK_VERSION, outDevice, MemorySegment.NULL, outContext);
            if (failed(hr)) {
                throw new IllegalStateException("D3D11CreateDevice failed: 0x" + Integer.toHexString(hr));
            }
            device = outDevice.get(JAVA_LONG, 0);
            context = outContext.get(JAVA_LONG, 0);

            hr = D3D.createFactory(
                    guid(arena, 0x770aae78, (short) 0xf26f, (short) 0x4dba,
                            0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87), // IID_IDXGIFactory1
                    outFactory);
            if (failed(hr)) {
                throw new IllegalStateException("CreateDXGIFactory1 failed: 0x" + Integer.toHexString(hr));
            }
            factory = outFactory.get(JAVA_LONG, 0);

            swapChain = createSwapChain(arena);
            vertexShader = shader(arena, "vs_main", "vs_5_0", DEV_CREATE_VERTEX_SHADER);
            pixelShader = shader(arena, "fs_main", "ps_5_0", DEV_CREATE_PIXEL_SHADER);
            blendState = createBlendState(arena);

            // globals: the target's width and height in pixels, and the batch's
            // first instance (D3D11 has no baseInstance).
            globals = Arena.global().allocate(16, 16);
            constantBuffer = createBuffer(arena, 16, USAGE_DEFAULT, BIND_CONSTANT_BUFFER, 0, 0, 0,
                    MemorySegment.NULL);

            allocateInstances(CAPACITY);
            uploadAtlas(arena);
        }
    }

    /** The atlas that scenes drawn by this renderer put their glyphs in. */
    public GlyphAtlas atlas() {
        return atlas;
    }

    /** How many instances the last frame drew: for tools. */
    public int instancesDrawn() {
        return drawn;
    }

    /**
     * Draws a frame of {@code width} by {@code height} points at
     * {@code scale} pixels a point, on {@code clear} (0xRRGGBB), and
     * reports whether it drew. {@code onCompleted} runs after the frame is
     * presented: D3D11 has no Metal completed handler, so this is Present
     * followed by the callback on this thread, which measures a frame a
     * little long (the GPU may still be drawing).
     *
     * <p>{@code synced} is ignored: every frame presents as soon as the GPU
     * is done with it, Present(0, 0), Windows's equivalent of the unsynced
     * path. (Synced would be Present(1, 0) vsync, not Metal's transaction.)
     */
    public boolean render(Scene scene, double width, double height, double scale, int clear,
                          boolean synced, Runnable onCompleted) {
        long pw = Math.max(1, Math.round(width * scale));
        long ph = Math.max(1, Math.round(height * scale));
        try (Arena arena = Arena.ofConfined()) {
            resize(arena, pw, ph);

            // The instance buffer: one dynamic buffer, rewritten each frame;
            // MAP_WRITE_DISCARD hands back memory no earlier frame reads.
            if (scene.count() > capacity) {
                allocateInstances(Math.max(scene.count(), capacity * 2));
            }
            MemorySegment mapped = arena.allocate(SIZEOF_MAPPED_SUBRESOURCE);
            int hr = (int) D3D.com(context, CTX_MAP, instanceBuffer, 0, D3D11_MAP_WRITE_DISCARD, 0,
                    mapped.address());
            if (failed(hr)) {
                throw new IllegalStateException("Map(instance buffer) failed: 0x" + Integer.toHexString(hr));
            }
            int n;
            try {
                MemorySegment data = MemorySegment.ofAddress(mapped.get(JAVA_LONG, 0))
                        .reinterpret((long) capacity * Scene.BYTES);
                n = scene.writeTo(data, capacity);
            } finally {
                D3D.com(context, CTX_UNMAP, instanceBuffer, 0);
            }
            uploadAtlas(arena);

            MemorySegment color = arena.allocateFrom(JAVA_FLOAT,
                    ((clear >> 16) & 0xFF) / 255f, ((clear >> 8) & 0xFF) / 255f, (clear & 0xFF) / 255f, 1f);
            // OMSetRenderTargets takes the ADDRESS of the view-pointer array.
            MemorySegment targets = arena.allocateFrom(JAVA_LONG, rtv);
            D3D.com(context, CTX_OM_SET_RENDER_TARGETS, 1, targets.address(), 0);
            D3D.com(context, CTX_CLEAR_RENDER_TARGET_VIEW, rtv, color.address());

            D3D.com(context, CTX_IA_SET_INPUT_LAYOUT, 0); // SV_VertexID generates the quad
            D3D.com(context, CTX_IA_SET_PRIMITIVE_TOPOLOGY, D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP);
            D3D.com(context, CTX_VS_SET_SHADER, vertexShader, 0, 0);
            D3D.com(context, CTX_PS_SET_SHADER, pixelShader, 0, 0);
            D3D.com(context, CTX_OM_SET_BLEND_STATE, blendState, 0, 0xFFFFFFFFL);

            // The vertex stage reads the instances at t0 and globals at b0.
            MemorySegment vsViews = arena.allocateFrom(JAVA_LONG, instanceSrv);
            D3D.com(context, CTX_VS_SET_SHADER_RESOURCES, 0, 1, vsViews.address());
            // The pixel stage reads the atlas at t0 and the instances at t1.
            MemorySegment psViews = arena.allocate(2 * 8);
            psViews.set(JAVA_LONG, 0, atlasSrv);
            psViews.set(JAVA_LONG, 8, instanceSrv);
            D3D.com(context, CTX_PS_SET_SHADER_RESOURCES, 0, 2, psViews.address());
            MemorySegment consts = arena.allocateFrom(JAVA_LONG, constantBuffer);
            D3D.com(context, CTX_VS_SET_CONSTANT_BUFFERS, 0, 1, consts.address());
            D3D.com(context, CTX_PS_SET_CONSTANT_BUFFERS, 0, 1, consts.address());

            MemorySegment viewport = arena.allocate(6 * (long) JAVA_FLOAT.byteSize());
            viewport.set(JAVA_FLOAT, 0, 0f);
            viewport.set(JAVA_FLOAT, 4, 0f);
            viewport.set(JAVA_FLOAT, 8, (float) pw);
            viewport.set(JAVA_FLOAT, 12, (float) ph);
            viewport.set(JAVA_FLOAT, 16, 0f);
            viewport.set(JAVA_FLOAT, 20, 1f);
            D3D.com(context, CTX_RS_SET_VIEWPORTS, 1, viewport.address());

            drawn = 0;
            for (int batch = 0; batch < scene.batches() && n > 0; batch++) {
                int start = scene.batchStart(batch), end = Math.min(scene.batchEnd(batch), n);
                if (end <= start) {
                    continue;
                }
                long sx = 0, sy = 0, sw = pw, sh = ph;
                if (scene.batchClipped(batch)) {
                    scene.batchScissor(batch, scissor);
                    sx = Math.max(0, Math.min(pw, scissor[0]));
                    sy = Math.max(0, Math.min(ph, scissor[1]));
                    sw = Math.max(0, Math.min(pw, (long) scissor[0] + scissor[2]) - sx);
                    sh = Math.max(0, Math.min(ph, (long) scissor[1] + scissor[3]) - sy);
                    if (sw == 0 || sh == 0) {
                        continue;
                    }
                }
                MemorySegment rects = arena.allocate(4 * (long) JAVA_INT.byteSize());
                rects.set(JAVA_INT, 0, (int) sx);
                rects.set(JAVA_INT, 4, (int) sy);
                rects.set(JAVA_INT, 8, (int) sw);
                rects.set(JAVA_INT, 12, (int) sh);
                D3D.com(context, CTX_RS_SET_SCISSOR_RECTS, 1, rects.address());

                // The batch's first instance, for the shader: SV_InstanceID
                // starts at 0 whatever StartInstanceLocation is, so the base
                // rides in the globals constant buffer.
                globals.set(JAVA_FLOAT, 0, (float) pw);
                globals.set(JAVA_FLOAT, 4, (float) ph);
                globals.set(JAVA_INT, 8, start);
                D3D.com(context, CTX_UPDATE_SUBRESOURCE, constantBuffer, 0, 0, globals.address(), 0, 0);

                D3D.com(context, CTX_DRAW_INSTANCED, 4, end - start, 0, 0);
                drawn += end - start;
            }

            if ((int) D3D.com(swapChain, SWAP_CHAIN_PRESENT, 0, 0) != 0) {
                return false;
            }
            if (onCompleted != null) {
                onCompleted.run();
            }
            return true;
        }
    }

    /** Resizes the swap chain's buffers and render target when the target changed. */
    private void resize(Arena arena, long pw, long ph) {
        if (pw == targetWidth && ph == targetHeight) {
            return;
        }
        if (rtv != 0) {
            D3D.com(rtv, COM_RELEASE); // the render target must let go of the back buffer first
            rtv = 0;
        }
        if (backBuffer != 0) {
            D3D.com(backBuffer, COM_RELEASE); // and the swap chain wants every buffer back
            backBuffer = 0;
        }
        int hr = (int) D3D.com(swapChain, SWAP_CHAIN_RESIZE_BUFFERS, 0, pw, ph, DXGI_FORMAT_UNKNOWN, 0);
        if (failed(hr)) {
            throw new IllegalStateException("ResizeBuffers failed: 0x" + Integer.toHexString(hr));
        }
        createRenderTarget(arena, swapChain);
        targetWidth = pw;
        targetHeight = ph;
    }

    private void createRenderTarget(Arena arena, long chain) {
        MemorySegment out = arena.allocate(JAVA_LONG);
        int hr = (int) D3D.com(chain, SWAP_CHAIN_GET_BUFFER, 0,
                guid(arena, 0x6f15aaf2, (short) 0xd208, (short) 0x4e89,
                        0x9a, 0xb4, 0x48, 0x95, 0x35, 0xd3, 0x4f, 0x9c).address(), // IID_ID3D11Texture2D
                out.address());
        if (failed(hr)) {
            throw new IllegalStateException("GetBuffer(back buffer) failed: 0x" + Integer.toHexString(hr));
        }
        backBuffer = out.get(JAVA_LONG, 0);
        MemorySegment view = arena.allocate(JAVA_LONG);
        hr = (int) D3D.com(device, DEV_CREATE_RENDER_TARGET_VIEW, backBuffer, 0, view.address());
        if (failed(hr)) {
            throw new IllegalStateException("CreateRenderTargetView failed: 0x" + Integer.toHexString(hr));
        }
        rtv = view.get(JAVA_LONG, 0);
    }

    /** FLIP_SEQUENTIAL first; DISCARD where the driver refuses it. */
    private long createSwapChain(Arena arena) {
        MemorySegment desc = swapChainDesc(arena, DXGI_SWAP_EFFECT_FLIP_SEQUENTIAL);
        MemorySegment out = arena.allocate(JAVA_LONG);
        int hr = (int) D3D.com(factory, FACTORY_CREATE_SWAP_CHAIN, device, desc.address(), out.address());
        if (failed(hr)) {
            desc = swapChainDesc(arena, DXGI_SWAP_EFFECT_DISCARD);
            hr = (int) D3D.com(factory, FACTORY_CREATE_SWAP_CHAIN, device, desc.address(), out.address());
        }
        if (failed(hr)) {
            throw new IllegalStateException("CreateSwapChain failed: 0x" + Integer.toHexString(hr));
        }
        targetWidth = desc.get(JAVA_INT, 0); // also sizes the first render target
        targetHeight = desc.get(JAVA_INT, 4);
        long chain = out.get(JAVA_LONG, 0);
        createRenderTarget(arena, chain);
        return chain;
    }

    private MemorySegment swapChainDesc(Arena arena, int swapEffect) {
        // DXGI_SWAP_CHAIN_DESC: DXGI_MODE_DESC (Width@0, Height@4, RefreshRate@8,
        // Format@16, ScanlineOrdering@20, Scaling@24), SampleDesc@28, BufferUsage@36,
        // BufferCount@40, OutputWindow@48 (8-aligned), Windowed@56, SwapEffect@60, Flags@64.
        MemorySegment desc = arena.allocate(SIZEOF_DESC_SWAPCHAIN);
        desc.set(JAVA_INT, 0, (int) targetWidth);        // DXGI_MODE_DESC.Width
        desc.set(JAVA_INT, 4, (int) targetHeight);       // .Height
        desc.set(JAVA_INT, 16, DXGI_FORMAT_B8G8R8A8_UNORM); // .Format
        desc.set(JAVA_INT, 28, 1);                       // SampleDesc.Count
        desc.set(JAVA_INT, 36, DXGI_USAGE_RENDER_TARGET_OUTPUT); // BufferUsage
        desc.set(JAVA_INT, 40, 2);                       // BufferCount
        desc.set(JAVA_LONG, 48, hwnd);                   // OutputWindow
        desc.set(JAVA_INT, 56, 1);                       // Windowed
        desc.set(JAVA_INT, 60, swapEffect);              // SwapEffect
        return desc;
    }

    /** Compiles the HLSL at runtime, from the resources: Windows has no xcrun metal. */
    private long shader(Arena arena, String entry, String target, int createSlot) {
        byte[] source = resource("/jui/shaders/shader.hlsl");
        if (source == null) {
            throw new IllegalStateException("no shader.hlsl in the resources");
        }
        MemorySegment bytes = arena.allocate(source.length);
        MemorySegment.copy(source, 0, bytes, JAVA_BYTE, 0, source.length);
        MemorySegment entryPoint = arena.allocateFrom(entry + "\0"), model = arena.allocateFrom(target + "\0");
        MemorySegment outCode = arena.allocate(JAVA_LONG), outErrors = arena.allocate(JAVA_LONG);
        int hr = D3D.compile(bytes, source.length, MemorySegment.NULL, entryPoint, model, outCode, outErrors);
        long code = outCode.get(JAVA_LONG, 0);
        if (failed(hr) || code == 0) {
            throw new IllegalStateException("D3DCompile(" + entry + ") failed: 0x" + Integer.toHexString(hr)
                    + " " + blobString(outErrors.get(JAVA_LONG, 0)));
        }
        try {
            long pointer = D3D.com(code, BLOB_GET_BUFFER_POINTER);
            long size = D3D.com(code, BLOB_GET_BUFFER_SIZE);
            MemorySegment outShader = arena.allocate(JAVA_LONG);
            hr = (int) D3D.com(device, createSlot, pointer, size, 0, outShader.address());
            if (failed(hr)) {
                throw new IllegalStateException("shader create (" + entry + ") failed: 0x"
                        + Integer.toHexString(hr));
            }
            return outShader.get(JAVA_LONG, 0);
        } finally {
            D3D.com(code, COM_RELEASE);
        }
    }

    /** Premultiplied alpha, as Metal's one, one minus source alpha. */
    private long createBlendState(Arena arena) {
        MemorySegment desc = arena.allocate(SIZEOF_DESC_BLEND);
        // AlphaToCoverageEnable, IndependentBlendEnable, then the first
        // target: BlendEnable, SrcBlend, DestBlend, BlendOp, SrcBlendAlpha,
        // DestBlendAlpha, BlendOpAlpha, RenderTargetWriteMask.
        desc.set(JAVA_INT, 0, 0);
        desc.set(JAVA_INT, 4, 0);
        desc.set(JAVA_INT, 8, 1);
        desc.set(JAVA_INT, 12, D3D11_BLEND_ONE);
        desc.set(JAVA_INT, 16, D3D11_BLEND_INV_SRC_ALPHA);
        desc.set(JAVA_INT, 20, D3D11_BLEND_OP_ADD);
        desc.set(JAVA_INT, 24, D3D11_BLEND_ONE);
        desc.set(JAVA_INT, 28, D3D11_BLEND_INV_SRC_ALPHA);
        desc.set(JAVA_INT, 32, D3D11_BLEND_OP_ADD);
        desc.set(JAVA_INT, 36, 0xF); // RGBA
        MemorySegment out = arena.allocate(JAVA_LONG);
        int hr = (int) D3D.com(device, DEV_CREATE_BLEND_STATE, desc.address(), out.address());
        if (failed(hr)) {
            throw new IllegalStateException("CreateBlendState failed: 0x" + Integer.toHexString(hr));
        }
        return out.get(JAVA_LONG, 0);
    }

    private long createBuffer(Arena arena, long bytes, int usage, int bind, int cpuAccess, int misc,
                              int stride, MemorySegment initial) {
        MemorySegment desc = arena.allocate(SIZEOF_DESC_BUFFER);
        desc.set(JAVA_INT, 0, (int) bytes);
        desc.set(JAVA_INT, 4, usage);
        desc.set(JAVA_INT, 8, bind);
        desc.set(JAVA_INT, 12, cpuAccess);
        desc.set(JAVA_INT, 16, misc);
        desc.set(JAVA_INT, 20, stride); // a structured buffer's element size, else 0
        MemorySegment out = arena.allocate(JAVA_LONG);
        int hr = (int) D3D.com(device, DEV_CREATE_BUFFER, desc.address(),
                initial == null ? 0 : initial.address(), out.address());
        if (failed(hr)) {
            throw new IllegalStateException("CreateBuffer failed: 0x" + Integer.toHexString(hr));
        }
        return out.get(JAVA_LONG, 0);
    }

    /** Makes the instance buffer of {@code newCapacity} instances, and its view. */
    private void allocateInstances(int newCapacity) {
        try (Arena arena = Arena.ofConfined()) {
            long bytes = (long) newCapacity * Scene.BYTES;
            long buffer = createBuffer(arena, bytes, USAGE_DYNAMIC,
                    BIND_SHADER_RESOURCE, CPU_ACCESS_WRITE, MISC_BUFFER_STRUCTURED, Scene.BYTES, null);
            long srv = createStructuredSrvOut(arena, buffer, bytes).get(JAVA_LONG, 0);
            if (instanceBuffer != 0) {
                D3D.com(instanceSrv, COM_RELEASE);
                D3D.com(instanceBuffer, COM_RELEASE);
            }
            instanceBuffer = buffer;
            instanceSrv = srv;
            capacity = newCapacity;
        }
    }

    private MemorySegment createStructuredSrvOut(Arena arena, long buffer, long bytes) {
        MemorySegment desc = arena.allocate(SIZEOF_DESC_SRV);
        desc.set(JAVA_INT, 0, DXGI_FORMAT_UNKNOWN);
        desc.set(JAVA_INT, 4, D3D11_SRV_DIMENSION_BUFFER);
        desc.set(JAVA_INT, 8, 0); // D3D11_BUFFER_SRV.FirstElement
        desc.set(JAVA_INT, 12, (int) (bytes / Scene.BYTES)); // NumElements
        MemorySegment out = arena.allocate(JAVA_LONG);
        int hr = (int) D3D.com(device, DEV_CREATE_SHADER_RESOURCE_VIEW, buffer, desc.address(), out.address());
        if (failed(hr)) {
            throw new IllegalStateException("CreateShaderResourceView(instances) failed: 0x"
                    + Integer.toHexString(hr));
        }
        return out;
    }

    /** Uploads the atlas: the dirty rectangle, or everything once it grows or repacks. */
    private void uploadAtlas(Arena arena) {
        if (atlasGeneration != atlas.generation()) {
            MemorySegment desc = arena.allocate(SIZEOF_DESC_TEXTURE2D);
            desc.set(JAVA_INT, 0, atlas.width);
            desc.set(JAVA_INT, 4, atlas.height);
            desc.set(JAVA_INT, 8, 1);
            desc.set(JAVA_INT, 12, 1);           // MipLevels, ArraySize
            desc.set(JAVA_INT, 16, DXGI_FORMAT_R8_UNORM);
            desc.set(JAVA_INT, 20, 1);           // SampleDesc.Count
            desc.set(JAVA_INT, 28, USAGE_DEFAULT);
            desc.set(JAVA_INT, 32, BIND_SHADER_RESOURCE);
            MemorySegment out = arena.allocate(JAVA_LONG);
            int hr = (int) D3D.com(device, DEV_CREATE_TEXTURE_2D, desc.address(), 0, out.address());
            if (failed(hr)) {
                throw new IllegalStateException("CreateTexture2D(atlas) failed: 0x" + Integer.toHexString(hr));
            }
            long texture = out.get(JAVA_LONG, 0);
            D3D.com(context, CTX_UPDATE_SUBRESOURCE, texture, 0, 0, atlas.pixels().address(), atlas.width, 0);
            MemorySegment srvDesc = arena.allocate(SIZEOF_DESC_SRV);
            srvDesc.set(JAVA_INT, 0, DXGI_FORMAT_R8_UNORM);
            srvDesc.set(JAVA_INT, 4, D3D11_SRV_DIMENSION_TEXTURE2D);
            srvDesc.set(JAVA_INT, 8, 0);
            srvDesc.set(JAVA_INT, 12, 1); // MostDetailedMip, MipLevels
            MemorySegment srvOut = arena.allocate(JAVA_LONG);
            hr = (int) D3D.com(device, DEV_CREATE_SHADER_RESOURCE_VIEW, texture, srvDesc.address(),
                    srvOut.address());
            if (failed(hr)) {
                throw new IllegalStateException("CreateShaderResourceView(atlas) failed: 0x"
                        + Integer.toHexString(hr));
            }
            if (atlasTexture != 0) {
                D3D.com(atlasSrv, COM_RELEASE);
                D3D.com(atlasTexture, COM_RELEASE);
            }
            atlasTexture = texture;
            atlasSrv = srvOut.get(JAVA_LONG, 0);
            atlasGeneration = atlas.generation();
            atlas.takeDirty(dirty);
        } else if (atlas.takeDirty(dirty)) {
            MemorySegment box = arena.allocate(SIZEOF_BOX);
            box.set(JAVA_INT, 0, dirty[0]);
        box.set(JAVA_INT, 4, dirty[1]);
        box.set(JAVA_INT, 8, 0);
        box.set(JAVA_INT, 12, dirty[0] + dirty[2]);
        box.set(JAVA_INT, 16, dirty[1] + dirty[3]);
        box.set(JAVA_INT, 20, 1);
            long at = atlas.pixels().address() + (long) dirty[1] * atlas.width + dirty[0];
            D3D.com(context, CTX_UPDATE_SUBRESOURCE, atlasTexture, 0, box.address(), at, atlas.width, 0);
        }
    }

    private static boolean failed(int hr) {
        return hr < 0;
    }

    /** A GUID from its parts, as 16 bytes of memory. */
    private static MemorySegment guid(Arena arena, long data1, int data2, int data3, int... rest) {
        MemorySegment g = arena.allocate(16, 4);
        g.set(JAVA_INT, 0, (int) data1);
        g.set(java.lang.foreign.ValueLayout.JAVA_SHORT, 4, (short) data2);
        g.set(java.lang.foreign.ValueLayout.JAVA_SHORT, 6, (short) data3);
        for (int i = 0; i < 8; i++) {
            g.set(JAVA_BYTE, 8 + i, (byte) rest[i]);
        }
        return g;
    }

    private static String blobString(long blob) {
        if (blob == 0) {
            return "";
        }
        long pointer = D3D.com(blob, BLOB_GET_BUFFER_POINTER);
        long size = D3D.com(blob, BLOB_GET_BUFFER_SIZE);
        return new String(MemorySegment.ofAddress(pointer).reinterpret(size).toArray(JAVA_BYTE),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] resource(String path) {
        try (InputStream in = D3D11Renderer.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
