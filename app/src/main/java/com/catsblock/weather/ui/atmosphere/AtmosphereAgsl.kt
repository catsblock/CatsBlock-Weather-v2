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
uniform float snow;
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

    private const val SNOW = """
float snowLayer(float2 uvd, float scale, float spd, float lay) {
    float2 st = uvd * scale;
    st.y -= time * spd;
    st.x += sin(time * 0.5 + lay * 2.1 + st.y * 0.7) * 0.3 - time * wind * 0.5 * spd;
    float2 id = floor(st);
    float2 f = fract(st) - 0.5;
    float h = hash21(id + lay * 19.3);
    float2 pos = (float2(fract(h * 37.1), fract(h * 91.7)) - 0.5) * 0.5;
    float r = 0.04 + 0.05 * fract(h * 53.9);
    float d = length(f - pos);
    float on = step(1.0 - (0.25 + 0.6 * snow), h);
    return (1.0 - smoothstep(r * 0.3, r, d)) * on;
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
    if (snow > 0.02) {
        float fl = snowLayer(uvd, 9.0, 0.35, 0.0) + 0.8 * snowLayer(uvd, 16.0, 0.55, 1.0) + 0.6 * snowLayer(uvd, 28.0, 0.8, 2.0);
        col += clamp(fl, 0.0, 1.0) * float3(1.0, 1.0, 1.05) * (0.9 + 0.5 * snow);
    }
    col = 1.0 - exp(-col * 1.25);
    col = toSrgb(col);
    col += (hash21(fragCoord + 7.0) - 0.5) / 255.0;
    return half4(float4(col, 1.0));
}
"""

    val SOURCE: String = HEADER + NOISE + DropLib.SOURCE + SKY + CLOUDS + SNOW + MAIN

    private const val TOUCH = """
uniform float4 tp[10];
uniform float4 dropState;

float wipeAt(float2 fc) {
    float m = 0.0;
    float r = resolution.y * 0.085;
    for (int i = 0; i < 10; i++) {
        float d = length(fc - tp[i].xy);
        m = max(m, tp[i].z * (1.0 - smoothstep(r * 0.5, r, d)));
    }
    return m;
}

float4 touchDrop(float2 fc) {
    if (dropState.w < 0.5) {
        return float4(0.0);
    }
    float R = resolution.y;
    float t = dropState.z;
    float rr = 0.028;
    float slide = smoothstep(0.0, 1.0, clamp((t - 0.35) / 5.5, 0.0, 1.0));
    float fall = R * 0.5 * slide;
    float moving = step(0.35, t) * (1.0 - smoothstep(4.8, 5.8, t));
    float wob = sin(t * 3.0) * R * 0.004 * moving;
    float2 o = dropState.xy;
    float2 c = o + float2(wob, fall);
    float2 q = (fc - c) / R;
    float stretch = 1.0 + 0.9 * moving;
    float2 qe = float2(q.x, q.y / stretch);
    float d = length(qe) - rr;
    float2 tailC = float2(0.0, -rr * (1.2 + 0.8 * moving));
    float d2 = length(q - tailC) - rr * 0.45;
    d = smoothMin(d, d2, rr * 0.6);
    float grow = smoothstep(0.0, 0.2, t);
    float life = grow * (1.0 - smoothstep(6.0, 8.0, t));
    float inside = (1.0 - smoothstep(-0.0015, 0.0015, d)) * life;
    float depth = clamp(-d / rr, 0.0, 1.0);
    float3 nrm = normalize(float3(qe / rr, sqrt(max(1.0 - dot(qe, qe) / (rr * rr), 0.02))));
    float3 lightDir = normalize(float3(-0.45, -0.55, 0.7));
    float3 halfVec = normalize(lightDir + float3(0.0, 0.0, 1.0));
    float specular = pow(max(dot(nrm, halfVec), 0.0), 40.0) * inside;
    float caustic = pow(1.0 - depth, 3.0) * smoothstep(-0.2, 0.6, qe.y / rr) * 0.4 * inside;
    float rim = (1.0 - depth) * (1.0 - depth) * 0.5 * inside;
    float ty = clamp((fc.y - o.y) / max(fall, 1.0), 0.0, 1.0);
    float tx = o.x + sin(ty * 3.0 * t) * R * 0.004 * moving;
    float inTrail = step(o.y, fc.y) * step(fc.y, c.y - rr * R) * moving;
    float trail = (1.0 - smoothstep(0.0, rr * R * 0.22, abs(fc.x - tx))) * inTrail * life;
    return float4(specular + caustic, rim + trail * 0.18, trail, inside);
}
"""

    private const val HELP = """
float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float smoothMin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float vnoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
"""

    private const val GLASS_MAIN = """
half4 main(float2 fragCoord) {
    float2 uvd = fragCoord / resolution.y + float2(seed, seed * 0.7);
    float4 a = dropLayer(uvd, 5.0, 0.0);
    float4 b = dropLayer(uvd, 11.0, 1.0);
    float wipe = wipeAt(fragCoord);
    float keep = 1.0 - wipe * 0.92;
    float4 td = touchDrop(fragCoord);
    float spec = (a.z + b.z) * keep + td.x;
    float shade = (a.w + b.w) * keep + td.y;
    float lensTint = clamp(length(a.xy + b.xy) * 18.0, 0.0, 1.0) * 0.10 * keep;
    float lightA = clamp(spec * 0.9 + lensTint + wipe * 0.035, 0.0, 0.8);
    float darkA = clamp(shade * 0.4 + wipe * 0.04, 0.0, 0.5);
    float alpha = lightA + darkA * (1.0 - lightA);
    float3 rgb = float3(1.0, 0.99, 0.96) * lightA;
    return half4(float4(rgb, alpha));
}
"""

    private const val SNOW_MAIN = """
half4 main(float2 fc) {
    float R = resolution.y;
    float amt = clamp(snow, 0.0, 1.0);
    float edgeD = min(min(fc.x, resolution.x - fc.x), min(fc.y, resolution.y - fc.y)) / R;
    float edge = 1.0 - smoothstep(0.0, 0.22, edgeD);
    float2 p = fc / R + float2(seed, seed * 0.7);
    float n1 = vnoise(p * 14.0);
    float n2 = vnoise(p * 38.0 + 7.0);
    float n3 = vnoise(p * 95.0 + 3.0);
    float ridge = 1.0 - abs(n1 * 2.0 - 1.0);
    float crystal = smoothstep(0.62, 0.98, ridge) * (0.5 + 0.5 * n2);
    float fine = smoothstep(0.55, 0.9, n3) * 0.5;
    float growth = clamp(edge * 0.9 + 0.2 + 0.25 * (n1 - 0.5), 0.0, 1.0) * (0.35 + 0.65 * amt);
    float wipe = wipeAt(fc);
    float frost = growth * (1.0 - wipe);
    float fa = frost * (0.10 + 0.22 * crystal + 0.08 * fine);
    float cell = R * 0.012;
    float2 sc = floor(fc / cell);
    float sh = hash21(sc + seed);
    float2 sf = fract(fc / cell) - 0.5;
    float tw = 0.5 + 0.5 * sin(time * 2.3 + sh * 40.0);
    float sp = step(0.985, sh) * (1.0 - smoothstep(0.0, 0.35, length(sf))) * tw * frost;
    float bd = (resolution.y - fc.y) / R;
    float h = (0.012 + 0.022 * vnoise(float2(fc.x / R * 9.0, seed))) * (0.4 + 0.6 * amt);
    float pile = (1.0 - smoothstep(h - 0.006, h, bd)) * (1.0 - wipe * 0.8);
    float lit = smoothstep(h * 0.2, h, bd);
    float4 td = touchDrop(fc);
    float a = fa + sp * 0.8 + pile * (0.5 + 0.2 * lit);
    float3 col = mix(float3(0.82, 0.9, 1.0), float3(1.0), clamp(pile * 0.6 + sp, 0.0, 1.0));
    float lightA = clamp(a + td.x * 0.9, 0.0, 0.9);
    float darkA = clamp(td.y * 0.4, 0.0, 0.5);
    float alpha = lightA + darkA * (1.0 - lightA);
    return half4(float4(col * lightA, alpha));
}
"""

    val GLASS: String = """
uniform float2 resolution;
uniform float time;
uniform float rain;
uniform float seed;
""" + DropLib.SOURCE + TOUCH + GLASS_MAIN

    val GLASS_SNOW: String = """
uniform float2 resolution;
uniform float time;
uniform float snow;
uniform float seed;
""" + HELP + TOUCH + SNOW_MAIN
}
