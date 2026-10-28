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
uniform float2 windVector;
uniform float altoChance;
uniform float seed;
uniform float snow;
uniform float pressure;
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
    for (int i = 0; i < 3; i++) {
        sum += amp * noise3(p);
        p = p * 2.03 + float3(1.7, 9.2, 3.1);
        amp *= 0.5;
    }
    return sum / 0.875;
}

float3 toLinear(float3 c) {
    return pow(c, float3(2.2));
}

float3 toSrgb(float3 c) {
    return pow(max(c, float3(0.0)), float3(1.0 / 2.2));
}
"""

    private const val SKY = """
float3 cctLinear(float kelvin) {
    float t = clamp(kelvin, 1000.0, 12000.0) / 100.0;
    float over = max(t - 60.0, 0.001);
    float rHi = clamp(1.2929362 * pow(over, -0.1332048), 0.0, 1.0);
    float r = mix(rHi, 1.0, step(t, 66.0));
    float gLo = clamp(0.3900816 * log(t) - 0.6318414, 0.0, 1.0);
    float gHi = clamp(1.1298909 * pow(over, -0.0755148), 0.0, 1.0);
    float g = mix(gHi, gLo, step(t, 66.0));
    float bMid = clamp(0.5432068 * log(max(t - 10.0, 1.0)) - 1.1962541, 0.0, 1.0);
    float b = mix(bMid, 1.0, step(66.0, t));
    return toLinear(float3(r, g, b));
}

float sunKelvin() {
    float morning = mix(3500.0, 5000.0, smoothstep(0.0, 0.45, tod));
    float afternoon = mix(3000.0, 2000.0, smoothstep(0.58, 1.0, tod));
    return mix(morning, afternoon, smoothstep(0.45, 0.58, tod));
}

float3 skyColor(float h) {
    float3 beta = float3(0.174, 0.405, 0.993) * (1.0 - 0.12 * pressure);
    float cosz = mix(0.06, 1.0, pow(h, 0.85));
    float3 scatter = 1.0 - exp(-beta / cosz);
    float3 illum = mix(cctLinear(sunKelvin()), float3(1.0), 0.45);
    return illum * scatter * 1.9;
}
"""

    private const val CLOUDS = """
float hg(float c, float g) {
    float g2 = g * g;
    return (1.0 - g2) / (12.566371 * pow(max(1.0 + g2 - 2.0 * g * c, 0.001), 1.5));
}

float miePhase(float c) {
    return mix(hg(c, 0.78), hg(c, -0.22), 0.28);
}

float dens(float3 p, float hgt, float dist, float cirA, float altA, float genA) {
    float par = 1.6 / (1.0 + 0.18 * length(p));
    float2 drift = windVector * time * 0.000035 * par;
    float shear = (hgt - 0.5) * 0.12;
    float3 q = p * 0.6 + float3(drift.x + windVector.x * shear, 0.0, drift.y + windVector.y * shear + seed);
    float2 w1 = float2(noise3(q * 0.5), noise3(q * 0.5 + float3(17.3, 3.1, 9.7))) - 0.5;
    q.xz += w1 * 1.3;
    float2 w2 = float2(noise3(q * 1.3 + 4.1), noise3(q * 1.3 + float3(8.2, 1.7, 5.3))) - 0.5;
    q.xz += w2 * 0.5;
    float3 midQ = q + float3(drift.x * 0.42, 0.0, drift.y * 0.42);
    float3 highQ = q + float3(drift.x * 1.35, 0.0, drift.y * 1.35);
    float sw = (0.16 + 0.34 * dist) * (1.0 + 0.25 * pressure);
    float lowShape = smoothstep(0.0, 0.22, hgt) * (1.0 - smoothstep(0.68, 1.0, hgt));
    float midShape = smoothstep(0.14, 0.40, hgt) * (1.0 - smoothstep(0.76, 1.0, hgt));
    float highShape = smoothstep(0.34, 0.62, hgt) * (1.0 - smoothstep(0.78, 1.0, hgt));
    float towerShape = smoothstep(0.0, 0.18, hgt) * (1.0 - smoothstep(0.86, 1.0, hgt));
    float d = 0.0;
    if (cirA > 0.01) {
        float3 c = float3(highQ.x * 0.22, highQ.y * 1.2, highQ.z * 1.9);
        float n = fbm3(c * 2.0);
        float fib = noise3(float3(highQ.x * 0.06, highQ.y, highQ.z * 7.0));
        float th = mix(0.74, 0.52, cover);
        d += cirA * smoothstep(th, th + sw + 0.12, n) * (0.45 + 0.75 * fib) * highShape * 0.7;
    }
    if (altA > 0.01) {
        float b = fbm3(midQ * 1.1);
        float bill = 1.0 - abs(noise3(midQ * 3.3 + float3(5.0, 1.0, 2.0)) * 2.0 - 1.0);
        float rows = 0.55 + 0.45 * sin(midQ.z * 2.4 + b * 3.5);
        float th = mix(0.80, 0.5, cover);
        d += altA * smoothstep(th, th + sw, b * 0.72 + bill * 0.30 * rows) * midShape;
    }
    if (genA > 0.01) {
        float g = fbm3(q * 0.85);
        float erosion = noise3(q * 2.7 + float3(3.4, 8.1, 2.6));
        float body = g + (erosion - 0.5) * 0.18;
        float th = mix(0.55, 0.2, genA);
        float shape = mix(lowShape, towerShape, smoothstep(0.45, 1.0, genA));
        d += genA * smoothstep(th, th + sw + 0.2, body) * shape * 1.2;
    }
    return clamp(d * (1.0 - 0.18 * pressure), 0.0, 1.0);
}

float3 scene(float2 uv, float2 fragCoord) {
    float aspect = resolution.x / resolution.y;
    float3 rd = normalize(float3((uv.x - 0.5) * aspect * 1.25, (0.9 - uv.y) * 1.3, 1.0));
    float h = clamp((0.9 - uv.y) / 0.9, 0.0, 1.0);
    float2 sunUV = float2(mix(0.2, 0.8, tod), 0.84 - 0.62 * sin(3.14159 * tod));
    float3 sd = normalize(float3((sunUV.x - 0.5) * aspect * 1.25, (0.9 - sunUV.y) * 1.3, 1.0));
    float sunGain = 0.8 + 0.5 * dot(toLinear(float3(sunTint.rgb)), float3(0.2126, 0.7152, 0.0722));
    float3 sunL = cctLinear(sunKelvin()) * sunGain;
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
    float limb = 1.0 - 0.35 * smoothstep(0.0, 0.035, r);
    float disc = (1.0 - smoothstep(0.02, 0.035, r)) * 6.0 * limb * (1.0 - genA);
    float transmit = 1.0;
    float3 acc = float3(0.0);
    float horizonFade = smoothstep(0.02, 0.18, rd.y);
    if (rd.y > 0.02) {
        float hb = 1.0;
        float ht = 1.7;
        float t0 = hb / rd.y;
        float t1 = ht / rd.y;
        float dt = (t1 - t0) / 10.0;
        float jit = hash21(fragCoord);
        float dist = clamp((t0 - 1.0) / 20.0, 0.0, 1.0);
        float3 amb = zenith * 0.55 + horizon * 0.25;
        float cosSun = dot(rd, sd);
        float phase = miePhase(cosSun);
        for (int i = 0; i < 10; i++) {
            if (transmit < 0.02) {
                break;
            }
            float t = t0 + dt * (float(i) + jit);
            float3 p = rd * t;
            float hgt = (p.y - hb) / (ht - hb);
            float d = dens(p, hgt, dist, cirA, altA, genA);
            if (d > 0.008) {
                float od = 0.0;
                float3 lp = p;
                float ls = 0.12;
                for (int j = 0; j < 3; j++) {
                    lp += sd * ls;
                    od += dens(lp, (lp.y - hb) / (ht - hb), dist, cirA, altA, genA) * ls;
                    ls *= 1.7;
                }
                od *= 6.0;
                float beer = exp(-od);
                float multi = 0.55 * exp(-od * 0.25);
                float powder = 1.0 - exp(-d * 3.5);
                float3 direct = sunL * (beer * (0.2 + 1.9 * phase) + multi * 0.3) * (0.55 + 0.45 * powder) * (1.0 - 0.6 * genA);
                float3 fill = amb * mix(0.55, 1.0, hgt);
                float a = 1.0 - exp(-d * dt * 1.1);
                acc += transmit * a * (direct + fill);
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
    float3 col;
    if (rain > 0.02) {
        float4 a = dropLayer(uvd, 5.0, 0.0);
        float4 b = dropLayer(uvd, 11.0, 1.0);
        float2 off = float2((a.x + b.x) * resolution.y / resolution.x, a.y + b.y);
        spec = a.z + b.z;
        shade = a.w + b.w;
        if (dot(off, off) > 0.00000001) {
            float3 c1 = scene(uv + off * 0.94, fragCoord);
            float3 c2 = scene(uv + off * 1.06, fragCoord);
            col = float3(c1.r, 0.5 * (c1.g + c2.g), c2.b);
        } else {
            col = scene(uv, fragCoord);
        }
    } else {
        col = scene(uv, fragCoord);
    }
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

    private const val WAVES = """
float2 capillary(float2 d, float age, float k, float c, float damp, float width, float amp) {
    float rho = length(d);
    float front = c * age;
    float u = (rho - front) / width;
    float env = exp(-u * u);
    float ph = (rho - front) * k;
    float decay = exp(-damp * age) / sqrt(rho + 0.04);
    float dh = env * (-sin(ph) * k - 2.0 * u / width * cos(ph));
    float2 dir = d / max(rho, 0.0001);
    return dir * dh * amp * decay;
}



float2 rippleShade(float2 g) {
    float s = dot(g, float2(-0.6, -0.8));
    return float2(clamp(s * 1.2, 0.0, 1.0), clamp(-s, 0.0, 1.0));
}
"""

    private const val IMPACT = """
float2 impactRipples(float2 uv) {
    float cell = 0.3;
    float2 st = uv / cell;
    float2 id = floor(st);
    float2 f = fract(st) - 0.5;
    float n = hash21(id + 31.7);
    float period = 2.4 + 2.0 * fract(n * 13.1);
    float tt = time / period + n * 7.0;
    float k = floor(tt);
    float age = fract(tt) * period;
    float2 c = (float2(hash21(id + k * 1.3 + 5.1), hash21(id + k * 2.7 + 9.4)) - 0.5) * 0.4;
    float on = step(0.55 - 0.5 * rain, hash21(id + k + 77.0));
    float2 d = (f - c) * cell;
    float fade = 1.0 - smoothstep(0.0, 1.0, (0.09 * age) / (cell * 0.5));
    float2 g = capillary(d, age, 120.0, 0.09, 1.4, 0.03, 0.0025) * on * fade;
    return rippleShade(g);
}
"""

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

float2 rippleAt(float2 fc) {
    float R = resolution.y;
    float2 g = float2(0.0);
    for (int i = 0; i < 10; i++) {
        float k = tp[i].z;
        if (k > 0.01) {
            float age = (1.0 - k) * 2.2 + 0.05;
            g += capillary((fc - tp[i].xy) / R, age, 95.0, 0.20, 1.6, 0.05, 0.0035) * k;
        }
    }
    return rippleShade(g);
}

float4 touchDrop(float2 fc) {
    if (dropState.w < 0.5) {
        return float4(0.0);
    }
    float R = resolution.y;
    float t = dropState.z;
    float rr = 0.028;
    float pr = clamp((t - 0.5) / 5.0, 0.0, 1.0);
    float v = stickSlip(pr, 0.37);
    float v2 = stickSlip(min(pr + 0.004, 1.0), 0.37);
    float vel = clamp((v2 - v) / 0.004 / 12.0, 0.0, 1.0) * step(0.5, t);
    float2 o = dropState.xy;
    float2 c = o + float2(R * 0.004 * sin(v * 18.0), R * 0.5 * v);
    float2 q = (fc - c) / R;
    float3 s = dropSdf(q, rr, vel);
    float life = smoothstep(0.0, 0.25, t) * (1.0 - smoothstep(6.0, 8.0, t));
    float inside = (1.0 - smoothstep(-0.0015, 0.0015, s.x)) * life;
    float4 body = lensDrop(s.yz, inside, rr);
    float k = pr * 6.0;
    float th = slipThreshold(0.37);
    float beadLight = 0.0;
    float beadDark = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float born = smoothstep(fi + th, fi + th + 0.12, k);
        float2 bc = float2(o.x + R * 0.004 * sin(fi / 6.0 * 18.0), o.y + R * 0.5 * fi / 6.0);
        float rb = rr * (0.34 - 0.05 * fi);
        float2 qb = (fc - bc) / R / rb;
        float db = (length(qb) - 1.0) * rb;
        float ib = (1.0 - smoothstep(-0.0015, 0.0015, db)) * born * life;
        float4 lb = lensDrop(qb, ib, rb);
        beadLight += lb.z;
        beadDark += lb.w;
    }
    float ty = clamp((fc.y - o.y) / max(R * 0.5 * v, 1.0), 0.0, 1.0);
    float neck = 0.35 + 0.65 * pow(abs(cos(ty * 18.8496)), 2.0);
    float tx = o.x + R * 0.004 * sin(ty * v * 18.0);
    float inTrail = step(o.y, fc.y) * step(fc.y, c.y - rr * R * 0.8) * step(0.5, t);
    float trail = (1.0 - smoothstep(0.0, rr * R * 0.12 * neck, abs(fc.x - tx))) * inTrail * life;
    float2 d = (fc - o) / R;
    float2 g = capillary(d, t, 80.0, 0.24, 1.5, 0.05, 0.0045) + capillary(d, t, 140.0, 0.34, 2.2, 0.035, 0.0022);
    float2 rip = rippleShade(g);
    float light = body.z + beadLight + rip.x * 0.6;
    float dark = body.w + beadDark + trail * 0.18 + rip.y * 0.5;
    return float4(light, dark, trail, inside);
}
"""

    private const val HELP = """
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
    float2 rp = impactRipples(uvd);
    float spec = a.z + b.z + rp.x * 0.55;
    float shade = a.w + b.w + rp.y * 0.5;
    float lensTint = clamp(length(a.xy + b.xy) * 18.0, 0.0, 1.0) * 0.10;
    float lightA = clamp(spec * 0.9 + lensTint, 0.0, 0.8);
    float darkA = clamp(shade * 0.4, 0.0, 0.5);
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
    float2 rp = rippleAt(fc);
    float a = fa + sp * 0.8 + pile * (0.5 + 0.2 * lit);
    float3 col = mix(float3(0.82, 0.9, 1.0), float3(1.0), clamp(pile * 0.6 + sp, 0.0, 1.0));
    float lightA = clamp(a + td.x * 0.9 + rp.x * 0.4, 0.0, 0.9);
    float darkA = clamp(td.y * 0.4 + rp.y * 0.2, 0.0, 0.5);
    float alpha = lightA + darkA * (1.0 - lightA);
    return half4(float4(col * lightA, alpha));
}
"""

    val GLASS: String = """
uniform float2 resolution;
uniform float time;
uniform float rain;
uniform float seed;
""" + DropLib.SOURCE + WAVES + IMPACT + GLASS_MAIN

    val GLASS_SNOW: String = """
uniform float2 resolution;
uniform float time;
uniform float snow;
uniform float seed;
""" + DropLib.BASE + HELP + WAVES + TOUCH + SNOW_MAIN

    val GRADE: String = """
uniform shader content;
uniform float2 size;
half4 main(float2 fc) {
    half4 c = content.eval(fc);
    float2 d = fc / size - 0.5;
    float v = 1.0 - dot(d, d) * 0.32;
    c.rgb *= half(v);
    return c;
}
"""
}
