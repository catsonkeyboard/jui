// The spike's one shader: every box is an instanced quad, and the fragment
// shader computes the coverage of its rounded rectangle, border and shadow
// from the signed distance to its edge, so they stay sharp at any scale.
// Glyphs are quads on the pixel grid that read their coverage from the
// glyph atlas, which Core Graphics drew.
//
// A cut-down version of MyGO's internal/gpu/metal/shader.metal (MIT,
// Copyright (c) egoist); sdRoundRect is taken from it as is.

#include <metal_stdlib>
using namespace metal;

// One instance per box, as jui.metal.Scene writes them (80 bytes).
struct Inst {
    float4 rect;   // x, y, width, height in pixels
    float4 radii;  // top-left, top-right, bottom-right, bottom-left; a glyph's x, y in the atlas
    float4 color;  // fill, straight sRGB
    float4 border; // border color
    float4 params; // border width, blur, kind (0 box, 1 shadow, 2 glyph), unused
};

struct VSOut {
    float4 pos [[position]];
    float2 p;
    uint inst [[flat]];
};

// globals holds the target's width and height in pixels.
vertex VSOut vs(uint vid [[vertex_id]], uint iid [[instance_id]],
                const device Inst *insts [[buffer(0)]],
                constant float4 &globals [[buffer(1)]]) {
    Inst i = insts[iid];
    float2 corner = float2(float(vid & 1u), float(vid >> 1u));
    // Room for antialiasing, and for the blur of a shadow; glyphs need none.
    float kind = i.params.z;
    float grow = kind > 1.5f ? 0.0f : kind > 0.5f ? 3.0f * i.params.y + 1.0f : 1.0f;
    float4 r = float4(i.rect.xy - grow, i.rect.zw + 2.0f * grow);
    float2 p = r.xy + corner * r.zw;
    VSOut o;
    o.pos = float4(p / globals.xy * float2(2.0f, -2.0f) + float2(-1.0f, 1.0f), 0.0f, 1.0f);
    o.p = p;
    o.inst = iid;
    return o;
}

float sdRoundRect(float2 p, float4 rect, float4 radii) {
    float2 h = rect.zw * 0.5f;
    float2 q = p - rect.xy - h;
    float r = q.x < 0.0f ? (q.y < 0.0f ? radii.x : radii.w) : (q.y < 0.0f ? radii.y : radii.z);
    float2 a = abs(q) - h + r;
    return length(max(a, float2(0.0f))) + min(max(a.x, a.y), 0.0f) - r;
}

// Colors come out premultiplied, for the blend: one, one minus source alpha.
fragment float4 fs(VSOut in [[stage_in]], const device Inst *insts [[buffer(0)]],
                   texture2d<float, access::read> atlas [[texture(0)]]) {
    Inst i = insts[in.inst];
    if (i.params.z > 1.5f) {
        // A glyph: its quad is on the pixel grid, so each pixel reads its texel.
        uint2 t = uint2(i.radii.xy + floor(in.p - i.rect.xy));
        float a = atlas.read(t).r * i.color.a;
        return float4(i.color.rgb * a, a);
    }
    float d = sdRoundRect(in.p, i.rect, i.radii);
    if (i.params.z > 0.5f) {
        // A soft shadow: coverage falls off across the blur radius.
        float s = max(i.params.y, 0.5f);
        float a = i.color.a * (1.0f - smoothstep(-s, s, d));
        return float4(i.color.rgb * a, a);
    }
    float coverage = clamp(0.5f - d, 0.0f, 1.0f);
    float4 c = i.color;
    float bw = i.params.x;
    if (bw > 0.0f) {
        float inside = clamp(0.5f - (d + bw), 0.0f, 1.0f);
        c = mix(i.border, i.color, inside);
    }
    float a = c.a * coverage;
    return float4(c.rgb * a, a);
}
