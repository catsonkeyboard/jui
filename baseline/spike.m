// A control for the spike's startup: the same app, window, view, Metal layer,
// shader and first frame as jui.spike.Main, written in Objective-C, timed
// the same way. ./build.sh bench builds it and compares.
//
//   objc-spike <shader.metallib> --bench

#import <AppKit/AppKit.h>
#import <Metal/Metal.h>
#import <QuartzCore/QuartzCore.h>
#include <sys/sysctl.h>
#include <sys/time.h>
#include <unistd.h>

static double nowMs(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1e3 + tv.tv_usec / 1e3;
}

static double processStartMs(void) {
    int mib[4] = {CTL_KERN, KERN_PROC, KERN_PROC_PID, getpid()};
    struct kinfo_proc info;
    size_t len = sizeof info;
    sysctl(mib, 4, &info, &len, NULL, 0);
    return info.kp_proc.p_starttime.tv_sec * 1e3 + info.kp_proc.p_starttime.tv_usec / 1e3;
}

static double tStart, tMain, tLaunched, tReady, tCommitted;
static volatile double tFrame;
static NSString *libraryPath;
static BOOL bench;

// The shader's instance, as jui.metal.Scene writes it.
typedef struct {
    float rect[4], radii[4], color[4], border[4], params[4];
} Inst;

static int paint(Inst *out, float w, float h, float s) {
    // As many boxes as Main.paint draws (12), laid out on a grid.
    int n = 12;
    for (int i = 0; i < n; i++) {
        float x = 40 + (i % 5) * 130, y = 40 + (i / 5) * 130;
        Inst in = {
            {x * s, y * s, 110 * s, 110 * s}, {12 * s, 12 * s, 12 * s, 12 * s},
            {0.15f, 0.39f, 0.92f, 1}, {0.82f, 0.84f, 0.86f, 1}, {1 * s, 0, 0, 0},
        };
        out[i] = in;
    }
    return n;
}

static void report(void) {
    printf("BENCH mode=objc main=%.1f launched=%.1f ready=%.1f committed=%.1f frame=%.1f\n",
           tMain - tStart, tLaunched - tStart, tReady - tStart, tCommitted - tStart, tFrame - tStart);
    exit(0);
}

@interface View : NSView
@end

@implementation View {
    id<MTLDevice> _device;
    id<MTLCommandQueue> _queue;
    id<MTLRenderPipelineState> _pipeline;
    CAMetalLayer *_metal;
    id<MTLBuffer> _buffer;
    BOOL _drawn;
}

- (instancetype)initWithFrame:(NSRect)frame {
    self = [super initWithFrame:frame];
    self.wantsLayer = YES;
    self.layerContentsRedrawPolicy = NSViewLayerContentsRedrawOnSetNeedsDisplay;
    self.autoresizingMask = NSViewWidthSizable | NSViewHeightSizable;

    _device = MTLCreateSystemDefaultDevice();
    _queue = [_device newCommandQueue];
    _metal = [CAMetalLayer layer];
    _metal.device = _device;
    _metal.pixelFormat = MTLPixelFormatBGRA8Unorm;
    _metal.framebufferOnly = YES;
    _metal.maximumDrawableCount = 3;
    _metal.opaque = YES;
    CGColorSpaceRef srgb = CGColorSpaceCreateWithName(kCGColorSpaceSRGB);
    _metal.colorspace = srgb;
    CGColorSpaceRelease(srgb);
    _metal.autoresizingMask = kCALayerWidthSizable | kCALayerHeightSizable;
    [self.layer addSublayer:_metal];

    NSError *err = nil;
    NSData *code = [NSData dataWithContentsOfFile:libraryPath];
    dispatch_data_t data = dispatch_data_create(code.bytes, code.length, NULL, DISPATCH_DATA_DESTRUCTOR_DEFAULT);
    id<MTLLibrary> lib = [_device newLibraryWithData:data error:&err];
    if (!lib) {
        NSLog(@"cannot load %@: %@", libraryPath, err);
        exit(1);
    }
    MTLRenderPipelineDescriptor *desc = [MTLRenderPipelineDescriptor new];
    desc.vertexFunction = [lib newFunctionWithName:@"vs"];
    desc.fragmentFunction = [lib newFunctionWithName:@"fs"];
    MTLRenderPipelineColorAttachmentDescriptor *color = desc.colorAttachments[0];
    color.pixelFormat = MTLPixelFormatBGRA8Unorm;
    color.blendingEnabled = YES;
    color.sourceRGBBlendFactor = color.sourceAlphaBlendFactor = MTLBlendFactorOne;
    color.destinationRGBBlendFactor = color.destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    _pipeline = [_device newRenderPipelineStateWithDescriptor:desc error:&err];
    _buffer = [_device newBufferWithLength:1024 * sizeof(Inst) options:MTLResourceStorageModeShared];
    return self;
}

- (BOOL)wantsUpdateLayer { return YES; }
- (BOOL)isFlipped { return YES; }

- (void)updateLayer {
    NSRect b = self.bounds;
    CGFloat s = self.window.backingScaleFactor;
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    _metal.frame = b;
    _metal.contentsScale = s;
    _metal.drawableSize = CGSizeMake(round(b.size.width * s), round(b.size.height * s));
    [CATransaction commit];

    id<CAMetalDrawable> drawable = [_metal nextDrawable];
    if (!drawable) return;
    MTLRenderPassDescriptor *pass = [MTLRenderPassDescriptor renderPassDescriptor];
    pass.colorAttachments[0].texture = drawable.texture;
    pass.colorAttachments[0].loadAction = MTLLoadActionClear;
    pass.colorAttachments[0].storeAction = MTLStoreActionStore;
    pass.colorAttachments[0].clearColor = MTLClearColorMake(0.953, 0.957, 0.965, 1);
    int n = paint(_buffer.contents, b.size.width, b.size.height, s);
    float globals[4] = {(float)(b.size.width * s), (float)(b.size.height * s), 0, 0};

    id<MTLCommandBuffer> commands = [_queue commandBuffer];
    id<MTLRenderCommandEncoder> enc = [commands renderCommandEncoderWithDescriptor:pass];
    [enc setRenderPipelineState:_pipeline];
    [enc setVertexBuffer:_buffer offset:0 atIndex:0];
    [enc setFragmentBuffer:_buffer offset:0 atIndex:0];
    [enc setVertexBytes:globals length:sizeof globals atIndex:1];
    [enc drawPrimitives:MTLPrimitiveTypeTriangleStrip vertexStart:0 vertexCount:4 instanceCount:n];
    [enc endEncoding];
    BOOL first = !_drawn;
    _drawn = YES;
    if (first && bench) {
        [commands addCompletedHandler:^(id<MTLCommandBuffer> cb) { tFrame = nowMs(); }];
    }
    [commands presentDrawable:drawable];
    [commands commit];
    if (first && bench) {
        tCommitted = nowMs();
        [self performSelector:@selector(poll:) withObject:nil afterDelay:0.001];
    }
}

- (void)poll:(id)sender {
    if (tFrame > 0) {
        report();
    }
    [self performSelector:@selector(poll:) withObject:nil afterDelay:0.001];
}
@end

@interface Delegate : NSObject <NSApplicationDelegate>
@end

@implementation Delegate
- (void)applicationDidFinishLaunching:(NSNotification *)note {
    tLaunched = nowMs();
    [NSApp activate];
    NSWindow *window = [[NSWindow alloc] initWithContentRect:NSMakeRect(0, 0, 720, 480)
                                                   styleMask:NSWindowStyleMaskTitled | NSWindowStyleMaskClosable |
                                                             NSWindowStyleMaskMiniaturizable | NSWindowStyleMaskResizable
                                                     backing:NSBackingStoreBuffered
                                                       defer:NO];
    window.title = @"j-ui baseline";
    window.releasedWhenClosed = NO;
    View *view = [[View alloc] initWithFrame:NSMakeRect(0, 0, 720, 480)];
    window.contentView = view;
    [window center];
    [window makeKeyAndOrderFront:nil];
    [view setNeedsDisplay:YES];
    tReady = nowMs();
}

- (BOOL)applicationShouldTerminateAfterLastWindowClosed:(NSApplication *)sender {
    return YES;
}
@end

int main(int argc, char **argv) {
    tStart = processStartMs();
    tMain = nowMs();
    @autoreleasepool {
        libraryPath = argc > 1 ? @(argv[1]) : @"shader.metallib";
        bench = argc > 2 && strcmp(argv[2], "--bench") == 0;
        NSApplication *app = [NSApplication sharedApplication];
        app.activationPolicy = NSApplicationActivationPolicyRegular;
        static Delegate *delegate;
        delegate = [Delegate new];
        app.delegate = delegate;
        [app run];
    }
    return 0;
}
