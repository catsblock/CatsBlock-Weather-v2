package com.catsblock.weather.ui.atmosphere

internal object DropLib {
    const val SOURCE = """
float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float slideCurve(float pr, float n) {
    float v = smoothstep(0.0, 1.0, pr);
    v += 0.05 * sin(pr * 50.0 + n * 30.0) * (1.0 - abs(2.0 * pr - 1.0));
    return clamp(v, 0.0, 1.0);
}

float4 dropLayer(float2 uv, float scale, float lay) {
    float cellH = 1.7;
    float2 st = float2(uv.x * scale, uv.y * scale / cellH + lay * 0.37);
    float2 id = floor(st);
    float2 f = fract(st) - 0.5;
    float2 p = float2(f.x, f.y * cellH);
    float n = hash21(id + lay * 17.0);
    float on = step(hash21(id * 1.7 + 3.1 + lay), 0.25 + 0.7 * rain);
    float sl = step(0.45, n);
    float r = (0.075 + 0.07 * fract(n * 91.7)) * (1.0 - 0.45 * lay);
    float viscosity = 0.6 + 4.0 * r;
    float speed = (0.03 + 0.07 * fract(n * 37.3)) * viscosity;
    float pr = fract(time * speed + n * 13.0);
    float v = slideCurve(pr, n);
    float v2 = slideCurve(min(pr + 0.01, 1.0), n);
    float vel = clamp(((v2 - v) / 0.01) * 0.25, 0.0, 1.0) * sl;
    float ys = -0.6;
    float ye = 0.6;
    float baseX = (fract(n * 53.1) - 0.5) * 0.35;
    float cy = mix((n - 0.5) * 0.5, mix(ys, ye, v), sl);
    float cx = baseX + 0.11 * sin(v * 9.0 + n * 6.2831) * sl;
    float life = mix(1.0, smoothstep(0.0, 0.06, pr) * (1.0 - smoothstep(0.92, 1.0, pr)), sl);
    float2 q = p - float2(cx, cy);
    float stretch = 1.0 + 1.5 * vel;
    float2 qe = float2(q.x, q.y / stretch);
    float d = length(qe) - r;
    float2 tailC = float2(0.0, -r * (1.3 + 0.9 * vel));
    float d2 = length(q - tailC) - r * 0.5 * (0.4 + vel);
    d = smoothMin(d, d2, r * 0.6);
    float pv = clamp((p.y - ys) / (ye - ys), 0.0, 1.0);
    float px = baseX + 0.11 * sin(pv * 9.0 + n * 6.2831);
    float inTrail = step(ys, p.y) * step(p.y, cy - r * 0.5) * sl;
    float width = r * mix(0.18, 0.32, pv);
    float trail = (1.0 - smoothstep(0.0, width, abs(p.x - px))) * inTrail * life;
    float ripple = sin(p.y * 70.0 - time * 5.0 * (0.4 + vel)) * 0.5 + 0.5;
    float2 trailOff = float2((ripple - 0.5) * r * 0.35, 0.0) * trail;
    float inside = 1.0 - smoothstep(-0.004, 0.004, d);
    float depth = clamp(-d / r, 0.0, 1.0);
    float lensRipple = 1.0 + 0.08 * sin(d * 60.0 - time * 12.0) * vel;
    float2 off = -q * (0.9 + 0.5 * depth) * lensRipple * inside;
    float3 nrm = normalize(float3(qe / r, sqrt(max(1.0 - dot(qe, qe) / (r * r), 0.02))));
    float3 lightDir = normalize(float3(-0.45, -0.55, 0.7));
    float3 halfVec = normalize(lightDir + float3(0.0, 0.0, 1.0));
    float specular = pow(max(dot(nrm, halfVec), 0.0), 40.0) * inside;
    float rimShade = (1.0 - depth) * (1.0 - depth) * 0.5 * inside;
    float caustic = pow(1.0 - depth, 3.0) * smoothstep(-0.2, 0.6, qe.y / r) * 0.4 * inside;
    float gate = on * life;
    float2 total = (off + trailOff) * gate / scale;
    return float4(total, (specular + caustic) * gate, (rimShade + trail * 0.12) * gate);
}
"""
}
