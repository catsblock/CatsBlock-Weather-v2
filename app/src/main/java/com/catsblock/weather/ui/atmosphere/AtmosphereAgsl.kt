package com.catsblock.weather.ui.atmosphere

internal object AtmosphereAgsl {

    private const val HEADER = """
uniform float2 resolution;
uniform float time;
uniform float tod;
uniform float cover;
uniform float rain;
uniform float storm;
uniform float wind;
uniform float altoChance;
uniform float seed;
layout(color) uniform half4 sunTint;
"""

    private const val NOISE = """
float hash13(float3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise3(float3 x) {
    float3 i = floor(x);
    float3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(i), hash13(i + float3(1.0, 0.0, 0.0)), f.x);
    float b = mix(hash13(i + float3(0.0, 1.0, 0.0)), hash13(i + float3(1.0, 1.0, 0.0)), f.x);
    float c = mix(hash13(i + float3(0.0, 0.0, 1.0)), hash13(i + float3(1.0, 0.0, 1.0)), f.x);
    float d = mix(hash13(i + float3(0.0, 1.0, 1.0)), hash13(i + float3(1.0, 1.0, 1.0)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

float fbm3(float3 p) {
    float amp = 0.5;
    float sum = 0.0;
    for (int i = 0; i < 2; i++) {
        sum += amp * noise3(p);
        p = p * 2.03 + float3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return sum / 0.75;
}

float3 toLinear(float3 c) {
    return pow(c, float3(2.2));
}

float3 toSrgb(float3 c) {
    return pow(max(c, float3(0.0)), float3(1.0 / 2.2));
}
"""

    private const val SKY = """
float3 ramp5(float3 c0, float3 c1, float3 c2, float3 c3, float3 c4, float h) {
    float3 c = mix(c0, c1, smoothstep(0.0, 0.12, h));
    c = mix(c, c2, smoothstep(0.12, 0.34, h));
    c = mix(c, c3, smoothstep(0.34, 0.66, h));
    c = mix(c, c4, smoothstep(0.66, 1.0, h));
    return c;
}

float3 skyColor(float h) {
    float3 m = ramp5(
        toLinear(float3(235.0, 204.0, 130.0) / 255.0),
        toLinear(float3(214.0, 186.0, 180.0) / 255.0),
        toLinear(float3(176.0, 176.0, 186.0) / 255.0),
        toLinear(float3(140.0, 163.0, 191.0) / 255.0),
        toLinear(float3(108.0, 141.0, 180.0) / 255.0), h);
    float3 n = ramp5(
        toLinear(float3(190.0, 212.0, 228.0) / 255.0),
        toLinear(float3(160.0, 194.0, 222.0) / 255.0),
        toLinear(float3(120.0, 168.0, 210.0) / 255.0),
        toLinear(float3(96.0, 152.0, 204.0) / 255.0),
        toLinear(float3(74.0, 126.0, 186.0) / 255.0), h);
    float3 a = ramp5(
        toLinear(float3(168.0, 84.0, 50.0) / 255.0),
        toLinear(float3(199.0, 112.0, 52.0) / 255.0),
        toLinear(float3(176.0, 138.0, 92.0) / 255.0),
        toLinear(float3(104.0, 118.0, 134.0) / 255.0),
        toLinear(float3(58.0, 76.0, 96.0) / 255.0), h);
    float3 first = mix(m, n, smoothstep(0.0, 0.5, tod));
    float3 second = mix(n, a, smoothstep(0.5, 1.0, tod));
    return mix(first, second, step(0.5, tod));
}
"""

    private const val CLOUDS = """
float dens(float3 p, float hgt, float dist, float cirA, float altA, float genA) {
    float3 q = p * 0.6 + float3(time * 0.010 * (0.6 + wind), 0.0, time * 0.003 + seed);
    float2 w = float2(noise3(q * 0.5), noise3(q * 0.5 + float3(17.3, 3.1, 9.7))) - 0.5;
    q.xz += w * 1.3;
    float sw = 0.16 + 0.34 * dist;
    float d = 0.0;
    if (cirA > 0.01) {
        float3 c = float3(q.x * 0.22, q.y * 1.2, q.z * 1.9);
        float n = fbm3(c * 2.0);
        float fib = noise3(float3(q.x * 0.06, q.y, q.z * 7.0));
        float th = mix(0.74, 0.52, cover);
        d += cirA * smoothstep(th, th + sw + 0.12, n) * (0.45 + 0.75 * fib) * 0.6;
    }
    if (altA > 0.01) {
        float b = fbm3(q * 1.1);
        float bill = 1.0 - abs(noise3(q * 3.3 + float3(5.0, 1.0, 2.0)) * 2.0 - 1.0);
        float rows = 0.55 + 0.45 * sin(q.z * 2.4 + b * 3.5);
        float th = mix(0.80, 0.5, cover);
        d += altA * smoothstep(th, th + sw, b * 0.72 + bill * 0.30 * rows);
    }
    if (genA > 0.01) {
        float g = fbm3(q * 0.85);
        float th = mix(0.55, 0.2, genA);
        d += genA * smoothstep(th, th + sw + 0.2, g) * 1.2;
    }
    float shape = smoothstep(0.0, 0.28, hgt) * (1.0 - smoothstep(0.6, 1.0, hgt));
    return clamp(d * shape, 0.0, 1.0);
}

float3 scene(float2 uv, float2 fragCoord) {
    float aspect = resolution.x / resolution.y;
    float3 rd = normalize(float3((uv.x - 0.5) * aspect * 1.25, (0.9 - uv.y) * 1.3, 1.0));
    float h = clamp((0.9 - uv.y) / 0.9, 0.0, 1.0);
    float2 sunUV = float2(mix(0.2, 0.8, tod), 0.84 - 0.62 * sin(3.14159 * tod));
    float3 sd = normalize(float3((sunUV.x - 0.5) * aspect * 1.25, (0.9 - sunUV.y) * 1.3, 1.0));
    float3 sunL = toLinear(float3(sunTint.rgb));
    float3 sky = skyColor(h);
    float3 zenith = skyColor(1.0);
    float3 horizon = skyColor(0.0);
    float r = length((uv - sunUV) * float2(aspect, 1.0));
    float genA = clamp(smoothstep(0.55, 0.95, cover) + rain * 0.8, 0.0, 1.0);
    float cirA = 1.0 - smoothstep(0.25, 0.6, tod);
    float altA = smoothstep(0.45, 0.8, tod) * altoChance * (1.0 - genA);
    float glow = exp(-r * 3.0) * 0.55 + exp(-r * 12.0) * 0.8;
    sky += sunL * glow * 0.35 * (1.0 - 0.7 * genA);
    sky += horizon * pow(1.0 - h, 3.0) * 0.18;
    float disc = (1.0 - smoothstep(0.02, 0.035, r)) * 6.0 * (1.0 - genA);
    float transmit = 1.0;
    float3 acc = float3(0.0);
    float horizonFade = smoothstep(0.02, 0.18, rd.y);
    if (rd.y > 0.02) {
        float hb = 1.0;
        float ht = 1.7;
        float t0 = hb / rd.y;
        float t1 = ht / rd.y;
        float dt = (t1 - t0) / 6.0;
        float jit = hash21(fragCoord);
        float dist = clamp((t0 - 1.0) / 20.0, 0.0, 1.0);
        float3 amb = zenith * 0.55 + horizon * 0.25;
        float3 gold = toLinear(float3(255.0, 196.0, 110.0) / 255.0);
        float3 rimC = toLinear(float3(255.0, 140.0, 50.0) / 255.0);
        float sunPow = 1.1 + 0.3 * sin(3.14159 * tod);
        for (int i = 0; i < 6; i++) {
            if (transmit < 0.03) {
                break;
            }
            float t = t0 + dt * (float(i) + jit);
            float3 p = rd * t;
            float hgt = (p.y - hb) / (ht - hb);
            float d = dens(p, hgt, dist, cirA, altA, genA);
            if (d > 0.005) {
                float ld = clamp(d * 1.5 * (1.0 - 0.5 * hgt) + 0.15 * (1.0 - tod), 0.0, 1.0);
                float beer = exp(-ld * 2.4);
                float powder = 1.0 - exp(-d * 4.0);
                float silver = pow(max(dot(rd, sd), 0.0), 6.0) * exp(-ld * 5.0);
                float rim = exp(-ld * 6.0) * (1.0 - beer);
                float3 lit = sunL * sunPow * (1.0 - 0.6 * genA) * (beer * (0.55 + 0.6 * powder) + silver * 1.6) + amb;
                lit += gold * (1.0 - hgt) * (1.0 - tod) * 0.45;
                lit += rimC * rim * tod * 1.1;
                float a = 1.0 - exp(-d * dt * 0.9);
                acc += transmit * a * lit;
                transmit *= 1.0 - a;
            }
        }
    }
    float3 col = mix(sky + sunL * disc * transmit, sky * transmit + acc, horizonFade);
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    float desat = clamp(cover * 0.45 + rain * 0.55, 0.0, 0.85);
    col = mix(col, float3(lum) * float3(0.9, 0.95, 1.0), desat);
    col *= 1.0 - 0.45 * rain - 0.2 * storm;
    float flashClock = time * 0.8;
    float flash = storm * step(0.93, hash21(float2(floor(flashClock), 1.0))) * exp(-fract(flashClock) * 7.0);
    col += flash * float3(0.8, 0.85, 1.0) * 0.6;
    return col;
}
"""

    private const val MAIN = """
half4 main(float2 fragCoord) {
    float2 uvd = fragCoord / resolution.y;
    float2 uv = fragCoord / resolution;
    float spec = 0.0;
    float shade = 0.0;
    if (rain > 0.02) {
        float4 a = dropLayer(uvd, 5.0, 0.0);
        float4 b = dropLayer(uvd, 11.0, 1.0);
        uv += float2((a.x + b.x) * resolution.y / resolution.x, a.y + b.y);
        spec = a.z + b.z;
        shade = a.w + b.w;
    }
    float3 col = scene(uv, fragCoord);
    col = col * (1.0 - shade * 0.55) + spec * float3(1.0, 0.98, 0.94) * 0.9;
    col = 1.0 - exp(-col * 1.25);
    col = toSrgb(col);
    col += (hash21(fragCoord + 7.0) - 0.5) / 255.0;
    return half4(float4(col, 1.0));
}
"""

    val SOURCE: String = HEADER + NOISE + DropLib.SOURCE + SKY + CLOUDS + MAIN

    val GLASS: String = """
uniform float2 resolution;
uniform float time;
uniform float rain;
uniform float seed;
""" + DropLib.SOURCE + """
half4 main(float2 fragCoord) {
    float2 uvd = fragCoord / resolution.y + float2(seed, seed * 0.7);
    float4 a = dropLayer(uvd, 5.0, 0.0);
    float4 b = dropLayer(uvd, 11.0, 1.0);
    float spec = a.z + b.z;
    float shade = a.w + b.w;
    float lensTint = clamp(length(a.xy + b.xy) * 18.0, 0.0, 1.0) * 0.10;
    float lightA = clamp(spec * 0.9 + lensTint, 0.0, 0.8);
    float darkA = clamp(shade * 0.4, 0.0, 0.5);
    float alpha = lightA + darkA * (1.0 - lightA);
    float3 rgb = float3(1.0, 0.99, 0.96) * lightA;
    return half4(float4(rgb, alpha));
}
"""
}
