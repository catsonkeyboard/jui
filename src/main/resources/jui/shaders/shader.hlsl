// The Windows port of shader.metal: every box is an instanced quad, and the
// fragment shader computes the coverage of its rounded rectangle, border and
// shadow from the signed distance to its edge, so they stay sharp at any
// scale. Glyphs are quads on the pixel grid that read their coverage from
// the glyph atlas that DirectWrite drew.
//
// sdRoundRect is from MyGO's internal/gpu/metal/shader.metal (MIT,
// Copyright (c) egoist).

// One instance per box, as jui.render.Scene writes them (112 bytes).
struct Inst {
    float4 rect;      // x, y, width, height in pixels
    float4 radii;     // top-left, top-right, bottom-right, bottom-left; a glyph's x, y in the atlas;
                      // a stroke's first two points
    float4 color;     // fill, straight sRGB
    float4 border;    // border color; a stroke's last point
    float4 params;    // border width (a stroke's half width), blur, kind (0 box, 1 shadow, 2 glyph, 3 stroke),
                      // unused
    float4 clip;      // the innermost clip's rect in pixels, when it rounds its corners
    float4 clipRadii; // its corners; all 0 when it has square ones
};

// The instance array: bound at t0 in the vertex stage, t1 in the pixel stage
// (t0 is the atlas there). D3D11 has no Metal baseInstance, so the batch's
// first instance travels in the globals constant buffer instead.
StructuredBuffer<Inst> insts : register(t0);

cbuffer globals : register(b0) {
    float2 targetSize;   // the target's width and height in pixels
    uint instanceOffset; // this batch's first instance in insts
    uint pad;
};

struct VSOut {
    float4 pos : SV_Position;
    float2 p : TEXCOORD0;
    uint inst : TEXCOORD1;
};

VSOut vs_main(uint vid : SV_VertexID, uint iid : SV_InstanceID) {
    Inst i = insts[iid + instanceOffset];
    float2 corner = float2(vid & 1u, vid >> 1u);
    // Room for antialiasing, and for the blur of a shadow; glyphs need none.
    float kind = i.params.z;
    float grow = kind > 2.5f ? 1.0f : kind > 1.5f ? 0.0f : kind > 0.5f ? 3.0f * i.params.y + 1.0f : 1.0f;
    float4 r = float4(i.rect.xy - grow, i.rect.zw + 2.0f * grow);
    float2 p = r.xy + corner * r.zw;
    VSOut o;
    o.pos = float4(p / targetSize * float2(2.0f, -2.0f) + float2(-1.0f, 1.0f), 0.0f, 1.0f);
    o.p = p;
    o.inst = iid + instanceOffset;
    return o;
}

float sdRoundRect(float2 p, float4 rect, float4 radii) {
    float2 h = rect.zw * 0.5f;
    float2 q = p - rect.xy - h;
    float r = q.x < 0.0f ? (q.y < 0.0f ? radii.x : radii.w) : (q.y < 0.0f ? radii.y : radii.z);
    float2 a = abs(q) - h + r;
    return length(max(a, float2(0.0f, 0.0f))) + min(max(a.x, a.y), 0.0f) - r;
}

// The distance from p to the segment from a to b.
float sdSegment(float2 p, float2 a, float2 b) {
    float2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-6f), 0.0f, 1.0f);
    return length(pa - ba * h);
}

// What of the pixel at p the innermost clip keeps: all of it unless it rounds its corners.
float clipCoverage(float2 p, Inst i) {
    if (all(i.clipRadii == float4(0.0f, 0.0f, 0.0f, 0.0f))) {
        return 1.0f;
    }
    return clamp(0.5f - sdRoundRect(p, i.clip, i.clipRadii), 0.0f, 1.0f);
}

StructuredBuffer<Inst> insts_ps : register(t1);
Texture2D<float> atlas : register(t0); // R8_UNORM, read texel by texel as Metal's access::read did

// Colors come out premultiplied, for the blend: one, inverse source alpha.
float4 fs_main(VSOut pin) : SV_Target {
    Inst i = insts_ps[pin.inst];
    float kept = clipCoverage(pin.p, i);
    if (i.params.z > 2.5f) {
        // A stroke: the nearer of its two segments, so the join is covered once.
        float d = min(sdSegment(pin.p, i.radii.xy, i.radii.zw), sdSegment(pin.p, i.radii.zw, i.border.xy)) - i.params.x;
        float a = i.color.a * clamp(0.5f - d, 0.0f, 1.0f) * kept;
        return float4(i.color.rgb * a, a);
    }
    if (i.params.z > 1.5f) {
        // A glyph: its quad is on the pixel grid, so each pixel reads its texel.
        uint2 t = uint2(i.radii.xy + floor(pin.p - i.rect.xy));
        float a = atlas.Load(int3(t, 0)).r * i.color.a * kept;
        return float4(i.color.rgb * a, a);
    }
    float d = sdRoundRect(pin.p, i.rect, i.radii);
    if (i.params.z > 0.5f) {
        // A soft shadow: coverage falls off across the blur radius.
        float s = max(i.params.y, 0.5f);
        float a = i.color.a * (1.0f - smoothstep(-s, s, d)) * kept;
        return float4(i.color.rgb * a, a);
    }
    float coverage = clamp(0.5f - d, 0.0f, 1.0f);
    float4 c = i.color;
    float bw = i.params.x;
    if (bw > 0.0f) {
        float inside = clamp(0.5f - (d + bw), 0.0f, 1.0f);
        c = lerp(i.border, i.color, inside);
    }
    float a = c.a * coverage * kept;
    return float4(c.rgb * a, a);
}
