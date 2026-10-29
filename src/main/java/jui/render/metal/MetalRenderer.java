package jui.render.metal;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.nio.charset.StandardCharsets;

import jui.ffi.Native;
import jui.ffi.ObjC;
import jui.render.Scene;
import jui.text.GlyphAtlas;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static jui.ffi.ObjC.cls;
import static jui.ffi.ObjC.send;

/**
 * Draws a {@link Scene} with Metal into a CAMetalLayer that it adds to a
 * view's layer, as MyGO's internal/gpu/metal does: one pipeline, one
 * instanced draw of quads, and a precompiled shader library.
 */
public final class MetalRenderer {
    private static final long PIXEL_FORMAT_R8_UNORM = 10;
    private static final long PIXEL_FORMAT_BGRA8_UNORM = 80;
    private static final int ATLAS_SIZE = 1024;
    private static final long PRIMITIVE_TRIANGLE_STRIP = 4;
    private static final long LOAD_ACTION_CLEAR = 2;
    private static final long STORE_ACTION_STORE = 1;
    private static final long BLEND_ONE = 1;
    private static final long BLEND_ONE_MINUS_SOURCE_ALPHA = 5;
    private static final long LAYER_WIDTH_SIZABLE = 1 << 1;
    private static final long LAYER_HEIGHT_SIZABLE = 1 << 4;

    /** Instances a buffer first holds, and buffers in flight. */
    private static final int CAPACITY = 1024;
    private static final int BUFFERS = 3;

    private static final SymbolLookup METAL = ObjC.framework("Metal");
    /** Loaded for CAMetalLayer. */
    private static final SymbolLookup QUARTZ_CORE = ObjC.framework("QuartzCore");
    private static final SymbolLookup CORE_GRAPHICS = ObjC.framework("CoreGraphics");
    private static final MemorySegment CREATE_SYSTEM_DEFAULT_DEVICE = ObjC.symbol(METAL, "MTLCreateSystemDefaultDevice");
    private static final MemorySegment COLOR_SPACE_CREATE_WITH_NAME =
            ObjC.symbol(CORE_GRAPHICS, "CGColorSpaceCreateWithName");
    private static final MemorySegment COLOR_SPACE_RELEASE = ObjC.symbol(CORE_GRAPHICS, "CGColorSpaceRelease");
    private static final MemorySegment DISPATCH_DATA_CREATE = ObjC.symbol(ObjC.LIBC, "dispatch_data_create");
    private static final MemorySegment DISPATCH_RELEASE = ObjC.symbol(ObjC.LIBC, "dispatch_release");

    private final long device;
    private final long queue;
    private final long pipeline;
    private final long layer;
    private final long[] buffers = new long[BUFFERS];
    private final MemorySegment[] contents = new MemorySegment[BUFFERS];
    private final int[] capacities = new int[BUFFERS];
    private final MemorySegment globals = Arena.global().allocate(16, 16);
    private final GlyphAtlas atlas;
    private long atlasTexture;
    private int atlasGeneration = -1;
    private final int[] dirty = new int[4];
    private int frame;
    private int drawn;
    private final int[] scissor = new int[4];
    private long drawableWidth, drawableHeight;
    private double drawableScale;
    private boolean presentsWithTransaction;
    /** How the library was made: "metallib" (precompiled) or "source". */
    public final String libraryKind;

    public MetalRenderer(long superlayer) {
        this(superlayer, new GlyphAtlas(ATLAS_SIZE, ATLAS_SIZE, 4096));
    }

    private MetalRenderer(long superlayer, GlyphAtlas atlas) {
        this.atlas = atlas;
        long pool = ObjC.poolPush();
        try {
            device = Native.call(CREATE_SYSTEM_DEFAULT_DEVICE);
            if (device == 0) {
                throw new IllegalStateException("no Metal device");
            }
            queue = send(device, "newCommandQueue");

            layer = ObjC.create("CAMetalLayer");
            send(layer, "setDevice:", device);
            send(layer, "setPixelFormat:", PIXEL_FORMAT_BGRA8_UNORM);
            send(layer, "setFramebufferOnly:", 1);
            send(layer, "setMaximumDrawableCount:", 3);
            send(layer, "setOpaque:", 1);
            long srgb = colorSpaceSRGB();
            send(layer, "setColorspace:", srgb);
            Native.call(COLOR_SPACE_RELEASE, srgb);
            send(layer, "setAutoresizingMask:", LAYER_WIDTH_SIZABLE | LAYER_HEIGHT_SIZABLE);
            send(superlayer, "addSublayer:", layer);

            long[] kind = new long[1];
            long library = library(kind);
            libraryKind = kind[0] == 0 ? "metallib" : "source";
            pipeline = pipeline(library);
            ObjC.release(library);

            for (int i = 0; i < BUFFERS; i++) {
                allocate(i, CAPACITY);
            }

            uploadAtlas();
        } catch (Throwable t) {
            throw t instanceof RuntimeException r ? r : new RuntimeException(t);
        } finally {
            ObjC.poolPop(pool);
        }
    }

    /** How many instances the last frame drew: for tools. */
    public int instancesDrawn() {
        return drawn;
    }

    /** The atlas that scenes drawn by this renderer put their glyphs in. */
    public GlyphAtlas atlas() {
        return atlas;
    }

    /**
     * Draws a frame of {@code width} by {@code height} points at
     * {@code scale} pixels a point, on {@code clear} (0xRRGGBB), and reports
     * whether it drew. {@code onCompleted}, a block
     * {@code void (^)(id<MTLCommandBuffer>)} or 0, is called on a thread of
     * Metal's once the GPU has drawn the frame.
     *
     * <p>A {@code synced} frame shows with the window's own changes, in
     * Core Animation's transaction, as a window being resized needs; others
     * present as soon as the GPU is done, as MyGO decides for each frame.
     */
    public boolean render(Scene scene, double width, double height, double scale, int clear,
                          boolean synced, long onCompleted) {
        long pool = ObjC.poolPush();
        try {
            if (synced != presentsWithTransaction) {
                send(layer, "setPresentsWithTransaction:", synced ? 1 : 0);
                presentsWithTransaction = synced;
            }
            long pw = Math.max(1, Math.round(width * scale));
            long ph = Math.max(1, Math.round(height * scale));
            if (pw != drawableWidth || ph != drawableHeight || scale != drawableScale) {
                long tx = cls("CATransaction");
                send(tx, "begin");
                send(tx, "setDisableActions:", 1);
                ObjC.sendRect(layer, "setFrame:", 0, 0, width, height);
                ObjC.sendDouble(layer, "setContentsScale:", scale);
                ObjC.sendSize(layer, "setDrawableSize:", pw, ph);
                send(tx, "commit");
                drawableWidth = pw;
                drawableHeight = ph;
                drawableScale = scale;
            }
            long drawable = send(layer, "nextDrawable");
            if (drawable == 0) {
                return false;
            }

            long commands = encode(scene, send(drawable, "texture"), pw, ph, clear);
            if (onCompleted != 0) send(commands, "addCompletedHandler:", onCompleted);
            if (synced) {
                send(commands, "commit");
                send(commands, "waitUntilScheduled");
                send(drawable, "present");
            } else {
                send(commands, "presentDrawable:", drawable);
                send(commands, "commit");
            }
            return true;
        } finally { ObjC.poolPop(pool); }
    }

    private void uploadAtlas() {
        if (atlasGeneration != atlas.generation()) {
            long desc = send(cls("MTLTextureDescriptor"), "texture2DDescriptorWithPixelFormat:width:height:mipmapped:",
                    PIXEL_FORMAT_R8_UNORM, atlas.width, atlas.height, 0);
            long replacement = send(device, "newTextureWithDescriptor:", desc);
            if (replacement == 0) throw new IllegalStateException("cannot grow atlas texture");
            ObjC.replaceRegion(replacement, 0, 0, atlas.width, atlas.height, atlas.pixels().address(), atlas.width);
            long previous = atlasTexture;
            atlasTexture = replacement;
            atlasGeneration = atlas.generation();
            ObjC.release(previous);
            atlas.takeDirty(dirty);
        } else if (atlas.takeDirty(dirty)) {
            long at = atlas.pixels().address() + (long) dirty[1] * atlas.width + dirty[0];
            ObjC.replaceRegion(atlasTexture, dirty[0], dirty[1], dirty[2], dirty[3], at, atlas.width);
        }
    }

    private long encode(Scene scene, long target, long pw, long ph, int clear) {
            long pass = send(cls("MTLRenderPassDescriptor"), "renderPassDescriptor");
            long color = send(send(pass, "colorAttachments"), "objectAtIndexedSubscript:", 0);
            send(color, "setTexture:", target);
            send(color, "setLoadAction:", LOAD_ACTION_CLEAR);
            send(color, "setStoreAction:", STORE_ACTION_STORE);
            ObjC.setClearColor(color, ((clear >> 16) & 0xFF) / 255.0, ((clear >> 8) & 0xFF) / 255.0,
                    (clear & 0xFF) / 255.0, 1);

            int slot = frame++ % BUFFERS;
            if (scene.count() > capacities[slot]) {
                // The command buffers still drawing from the old one retain it.
                ObjC.release(buffers[slot]);
                allocate(slot, Math.max(scene.count(), capacities[slot] * 2));
            }
            int n = scene.writeTo(contents[slot], capacities[slot]);
            globals.set(JAVA_FLOAT, 0, pw);
            globals.set(JAVA_FLOAT, 4, ph);
            uploadAtlas();

            long commands = send(queue, "commandBuffer");
            long encoder = send(commands, "renderCommandEncoderWithDescriptor:", pass);
            send(encoder, "setRenderPipelineState:", pipeline);
            send(encoder, "setVertexBuffer:offset:atIndex:", buffers[slot], 0, 0);
            send(encoder, "setFragmentBuffer:offset:atIndex:", buffers[slot], 0, 0);
            send(encoder, "setVertexBytes:length:atIndex:", globals.address(), 16, 1);
            send(encoder, "setFragmentTexture:atIndex:", atlasTexture, 0);
            // A batch a clip: its scissor rectangle within the drawable, then its instances.
            drawn = 0;
            for (int b = 0; b < scene.batches() && n > 0; b++) {
                int start = scene.batchStart(b), end = Math.min(scene.batchEnd(b), n);
                if (end <= start) {
                    continue;
                }
                long sx = 0, sy = 0, sw = pw, sh = ph;
                if (scene.batchClipped(b)) {
                    scene.batchScissor(b, scissor);
                    sx = Math.max(0, Math.min(pw, scissor[0]));
                    sy = Math.max(0, Math.min(ph, scissor[1]));
                    sw = Math.max(0, Math.min(pw, (long) scissor[0] + scissor[2]) - sx);
                    sh = Math.max(0, Math.min(ph, (long) scissor[1] + scissor[3]) - sy);
                    if (sw == 0 || sh == 0) {
                        continue;
                    }
                }
                ObjC.setScissor(encoder, sx, sy, sw, sh);
                send(encoder, "drawPrimitives:vertexStart:vertexCount:instanceCount:baseInstance:",
                        PRIMITIVE_TRIANGLE_STRIP, 0, 4, end - start, start);
                drawn += end - start;
            }
            send(encoder, "endEncoding");
            return commands;
    }

    public static void checkAtlasGrowth() {
        long pool = ObjC.poolPush();
        long superlayer = ObjC.create("CALayer");
        MetalRenderer renderer = null;
        long firstTarget = 0, secondTarget = 0;
        try (Arena arena = Arena.ofConfined()) {
            GlyphAtlas atlas = new GlyphAtlas(32, 32, 512);
            renderer = new MetalRenderer(superlayer, atlas);
            jui.text.TextEngine text = new jui.text.TextEngine("zh-Hans");
            Scene original = new Scene();
            original.begin(2, text, atlas);
            original.text("W", 8, 8, 13, 400, 0xFFFFFFFF, 0);
            firstTarget = renderer.readbackTarget(128, 96);
            secondTarget = renderer.readbackTarget(128, 96);
            long before = renderer.encode(original, firstTarget, 128, 96, 0);
            if (send(before, "retainedReferences") == 0) throw new IllegalStateException("command buffers must retain resources");
            long oldTexture = renderer.atlasTexture;
            jui.text.TextLayout more = text.layout("abcdefghijklmnopqrstuvwxyz0123456789中文字体图集扩容", 24, 700, 0);
            for (int index = 0; index < more.glyphCount(); index++) atlas.get(text, more.font(index), more.glyph(index), 2, 0, 1, 0);
            long after = renderer.encode(original, secondTarget, 128, 96, 0);
            if (atlas.generation() < 2 || oldTexture == renderer.atlasTexture || atlas.isFull())
                throw new IllegalStateException("GPU growth probe did not grow twice");
            send(before, "commit"); send(after, "commit");
            send(before, "waitUntilCompleted"); send(after, "waitUntilCompleted");
            if (send(before, "status") != 4 || send(after, "status") != 4 || send(before, "error") != 0 || send(after, "error") != 0)
                throw new IllegalStateException("GPU atlas probe failed");
            MemorySegment first = arena.allocate(128 * 96 * 4), second = arena.allocate(128 * 96 * 4);
            ObjC.textureBytes(firstTarget, first.address(), 128 * 4, 128, 96);
            ObjC.textureBytes(secondTarget, second.address(), 128 * 4, 128, 96);
            boolean ink = false;
            for (long offset = 0; offset < first.byteSize(); offset += 4) {
                if (first.get(JAVA_BYTE, offset) != 0) ink = true;
            }
            if (!ink || first.mismatch(second) != -1) throw new IllegalStateException("old glyph changed after GPU atlas growth");
            System.out.printf("GPU atlas growth ok generations=%d retainedReferences=true pixels=equal nonzero=true%n", atlas.generation());
        } finally {
            ObjC.release(firstTarget); ObjC.release(secondTarget);
            if (renderer != null) {
                for (long buffer : renderer.buffers) ObjC.release(buffer);
                ObjC.release(renderer.atlasTexture); ObjC.release(renderer.pipeline);
                ObjC.release(renderer.queue); ObjC.release(renderer.layer); ObjC.release(renderer.device);
            }
            ObjC.release(superlayer); ObjC.poolPop(pool);
        }
    }

    private long readbackTarget(int width, int height) {
        long descriptor = send(cls("MTLTextureDescriptor"), "texture2DDescriptorWithPixelFormat:width:height:mipmapped:",
                PIXEL_FORMAT_BGRA8_UNORM, width, height, 0);
        send(descriptor, "setStorageMode:", 0);
        send(descriptor, "setUsage:", 5);
        long texture = send(device, "newTextureWithDescriptor:", descriptor);
        if (texture == 0) throw new IllegalStateException("cannot create offscreen target");
        return texture;
    }

    /** Makes buffer slot a buffer of capacity instances, in memory the CPU and GPU share. */
    private void allocate(int slot, int capacity) {
        long bytes = (long) capacity * Scene.BYTES;
        buffers[slot] = send(device, "newBufferWithLength:options:", bytes, 0); // shared storage
        contents[slot] = MemorySegment.ofAddress(send(buffers[slot], "contents")).reinterpret(bytes);
        capacities[slot] = capacity;
    }

    /** The shader library: precompiled when the build made one, else from source. */
    private long library(long[] kind) throws Throwable {
        byte[] metallib = resource("/jui/shaders/shader.metallib");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment err = arena.allocate(JAVA_LONG);
            if (metallib != null) {
                MemorySegment bytes = arena.allocate(metallib.length);
                MemorySegment.copy(metallib, 0, bytes, JAVA_BYTE, 0, metallib.length);
                // Without a destructor, dispatch_data_create copies the bytes.
                long data = Native.call(DISPATCH_DATA_CREATE, bytes.address(), metallib.length, 0L, 0L);
                long lib = send(device, "newLibraryWithData:error:", data, err.address());
                Native.call(DISPATCH_RELEASE, data);
                if (lib != 0) {
                    kind[0] = 0;
                    return lib;
                }
                System.err.println("jui: cannot load shader.metallib: " + ObjC.describe(err.get(JAVA_LONG, 0)));
            }
            byte[] source = resource("/jui/shaders/shader.metal");
            if (source == null) {
                throw new IllegalStateException("no shader in the resources");
            }
            long lib = send(device, "newLibraryWithSource:options:error:",
                    ObjC.nsString(new String(source, StandardCharsets.UTF_8)), 0, err.address());
            if (lib == 0) {
                throw new IllegalStateException("cannot compile the shader: " + ObjC.describe(err.get(JAVA_LONG, 0)));
            }
            kind[0] = 1;
            return lib;
        }
    }

    private long pipeline(long library) {
        long vs = send(library, "newFunctionWithName:", ObjC.nsString("vs"));
        long fs = send(library, "newFunctionWithName:", ObjC.nsString("fs"));
        long desc = ObjC.create("MTLRenderPipelineDescriptor");
        try (Arena arena = Arena.ofConfined()) {
            send(desc, "setVertexFunction:", vs);
            send(desc, "setFragmentFunction:", fs);
            long color = send(send(desc, "colorAttachments"), "objectAtIndexedSubscript:", 0);
            send(color, "setPixelFormat:", PIXEL_FORMAT_BGRA8_UNORM);
            send(color, "setBlendingEnabled:", 1);
            send(color, "setSourceRGBBlendFactor:", BLEND_ONE);
            send(color, "setDestinationRGBBlendFactor:", BLEND_ONE_MINUS_SOURCE_ALPHA);
            send(color, "setSourceAlphaBlendFactor:", BLEND_ONE);
            send(color, "setDestinationAlphaBlendFactor:", BLEND_ONE_MINUS_SOURCE_ALPHA);
            MemorySegment err = arena.allocate(JAVA_LONG);
            long state = send(device, "newRenderPipelineStateWithDescriptor:error:", desc, err.address());
            if (state == 0) {
                throw new IllegalStateException("cannot create the pipeline: " + ObjC.describe(err.get(JAVA_LONG, 0)));
            }
            return state;
        } finally {
            ObjC.release(vs);
            ObjC.release(fs);
            ObjC.release(desc);
        }
    }

    /** CGColorSpaceCreateWithName(kCGColorSpaceSRGB), owned by the caller. */
    private static long colorSpaceSRGB() throws Throwable {
        // kCGColorSpaceSRGB is a CFStringRef variable: read its value.
        long name = ObjC.symbol(CORE_GRAPHICS, "kCGColorSpaceSRGB").reinterpret(8).get(JAVA_LONG, 0);
        return Native.call(COLOR_SPACE_CREATE_WITH_NAME, name);
    }

    private static byte[] resource(String path) {
        try (InputStream in = MetalRenderer.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }
}
