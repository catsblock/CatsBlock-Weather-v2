package com.catsblock.weather.ui.atmosphere

internal object DropLib {

    const val BASE = """
float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float slipThreshold(float n) {
    return 0.66 + 0.16 * fract(n * 7.7);
}

float stickSlip(float pr, float n) {
    float k = pr * 6.0;
    float whole = floor(k);
    float jump = smoothstep(slipThreshold(n), 1.0, k - whole);
    return clamp((whole + jump) / 6.0, 0.0, 1.0);
}

float3 dropSdf(float2 q, float r, float vel) {
    float rt = r * (0.20 + 0.34 * vel);
    float2 tail = float2(0.0, -(r + rt) * (0.55 + 0.45 * vel));
    float away = 1.0 - smoothstep(0.0, 0.08, vel);
    float d1 = length(q) - r;
    float d2 = length(q - tail) - rt + r * 3.0 * away;
    float k = r * (0.15 + 0.45 * vel);
    float h = clamp(0.5 + 0.5 * (d2 - d1) / k, 0.0, 1.0);
    float d = mix(d2, d1, h) - k * h * (1.0 - h);
    float2 g1 = q / max(length(q), 0.0001);
    float2 g2 = (q - tail) / max(length(q - tail), 0.0001);
    float2 outDir = normalize(mix(g2, g1, h) + float2(0.0001, 0.0));
    float rho = 1.0 - clamp(-d / r, 0.0, 1.0);
    return float3(d, outDir * rho);
}

float4 lensDrop(float2 qn, float inside, float rr) {
    float rho2 = clamp(dot(qn, qn), 0.0, 1.0);
    float3 nrm = normalize(float3(qn * 1.1, sqrt(max(1.0 - rho2, 0.04))));
    float3 refr = refract(float3(0.0, 0.0, -1.0), nrm, 0.7502);
    float thick = rr * (0.35 + 1.45 * sqrt(1.0 - rho2));
    float2 disp = refr.xy / max(-refr.z, 0.3) * thick;
    float3 halfVec = normalize(normalize(float3(-0.45, -0.55, 0.7)) + float3(0.0, 0.0, 1.0));
    float spec = pow(max(dot(nrm, halfVec), 0.0), 64.0);
    float fres = 0.02 + 0.98 * pow(1.0 - nrm.z, 5.0);
    float caustic = smoothstep(0.5, 0.95, rho2) * smoothstep(-0.1, 0.7, qn.y) * 0.38;
    float rim = pow(rho2, 2.0) * 0.5;
    return float4(disp * inside, (spec + caustic + fres * 0.5) * inside, rim * inside);
}
"""

    const val LAYER = """
float4 dropLayer(float2 uv, float scale, float lay) {
    float cellH = 1.7;
    float2 st = float2(uv.x * scale, uv.y * scale / cellH + lay * 0.37);
    float2 id = floor(st);
    float2 f = fract(st) - 0.5;
    float2 p = float2(f.x, f.y * cellH);
    float n = hash21(id + lay * 17.0);
    float on = step(hash21(id * 1.7 + 3.1 + lay), 0.25 + 0.7 * rain);
    float r = (0.075 + 0.07 * fract(n * 91.7)) * (1.0 - 0.45 * lay);
    float sl = step(0.098 - 0.025 * lay, r) * step(0.3, n);
    float viscosity = 0.6 + 4.0 * r;
    float speed = (0.03 + 0.07 * fract(n * 37.3)) * viscosity;
    float pr = fract(time * speed + n * 13.0);
    float k = pr * 6.0;
    float v = stickSlip(pr, n);
    float v2 = stickSlip(min(pr + 0.004, 1.0), n);
    float vel = clamp((v2 - v) / 0.004 / 12.0, 0.0, 1.0) * sl;
    float ys = -0.6;
    float ye = 0.6;
    float baseX = (fract(n * 53.1) - 0.5) * 0.35;
    float cy = mix((n - 0.5) * 0.5, mix(ys, ye, v), sl);
    float cx = baseX + 0.11 * sin(v * 9.0 + n * 6.2831) * sl;
    float life = mix(1.0, smoothstep(0.0, 0.06, pr) * (1.0 - smoothstep(0.92, 1.0, pr)), sl);
    float2 q = p - float2(cx, cy);
    float3 s = dropSdf(q, r, vel);
    float inside = 1.0 - smoothstep(-0.004, 0.004, s.x);
    float4 body = lensDrop(s.yz, inside, r);
    float2 beadDisp = float2(0.0);
    float beadSpec = 0.0;
    float beadRim = 0.0;
    if (sl > 0.5) {
        float th = slipThreshold(n);
        float fade = 1.0 - smoothstep(0.88, 1.0, pr);
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            float born = smoothstep(fi + th, fi + th + 0.12, k) * fade;
            float2 bc = float2(baseX + 0.11 * sin(fi / 6.0 * 9.0 + n * 6.2831), mix(ys, ye, fi / 6.0));
            float rb = r * (0.30 - 0.04 * fi) * (0.8 + 0.4 * fract(n * 17.0 + fi * 3.7));
            float2 qb = (p - bc) / rb;
            float db = (length(qb) - 1.0) * rb;
            float ib = (1.0 - smoothstep(-0.004, 0.004, db)) * born;
            float4 lb = lensDrop(qb, ib, rb);
            beadDisp += lb.xy;
            beadSpec += lb.z;
            beadRim += lb.w;
        }
    }
    float pv = clamp((p.y - ys) / (ye - ys), 0.0, 1.0);
    float px = baseX + 0.11 * sin(pv * 9.0 + n * 6.2831);
    float inTrail = step(ys, p.y) * step(p.y, cy - r * 0.6) * sl;
    float neck = 0.35 + 0.65 * pow(abs(cos(pv * 18.8496)), 2.0);
    float width = r * 0.20 * neck;
    float trail = (1.0 - smoothstep(0.0, width, abs(p.x - px))) * inTrail * life;
    float2 trailOff = float2(-(p.x - px), 0.0) * 0.5 * trail;
    float gate = on * life;
    float2 total = (body.xy + beadDisp + trailOff) * gate / scale;
    return float4(total, (body.z + beadSpec) * gate, (body.w + beadRim + trail * 0.12) * gate);
}
"""

    const val SOURCE = BASE + LAYER
}
