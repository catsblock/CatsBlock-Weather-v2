package com.catsblock.weather.ui.sky

internal const val SKY_AGSL = """
uniform float2 uRes;
uniform float3 uSun;
uniform float3 uRight;
uniform float3 uUp;
uniform float3 uFwd;
uniform float3 uSunT;
uniform float3 uAmb;
uniform float2 uOff;
uniform float2 uOffHigh;
uniform float2 uWindDir;
uniform float4 uStyle;
uniform float uCirrus;
uniform float uDark;
uniform float uFog;
uniform float4 uFlash;
uniform float3 uSunTHigh;
uniform float uCover;
uniform float uThick;
uniform float uOct;
uniform float uShadow;
uniform float uSteps;
uniform float uMie;
uniform float uFade;
uniform float uGain;
uniform float4 uSkyEq;
uniform shader uMoonTex;
uniform float2 uMoonSize;
uniform float4 uMoonA;
uniform float2 uMoonB;
uniform float3 uMoonDir;
uniform float3 uMoonRight;
uniform float3 uMoonUp;
uniform float3 uMoonL;
uniform float3 uMoonT;
uniform float3 uMoonLight;
uniform float4 uEcl;

const float PI = 3.14159265;
const float RE = 6371000.0;
const float RAT = 6471000.0;
const float HR = 7994.0;
const float HM = 1200.0;
const float HO = 15000.0;
const float3 BR = float3(5.8e-6, 13.5e-6, 33.1e-6);
const float3 BO = float3(0.65e-6, 1.881e-6, 0.085e-6);
const float3 LUMA = float3(0.2126, 0.7152, 0.0722);

float airmass(float cz) {
  float c = clamp(cz, 0.0, 1.0);
  float z = acos(c) * 57.29578;
  return 1.0 / (c + 0.50572 * pow(max(96.07995 - z, 0.01), -1.6364));
}

float3 skyColor(float3 rd, float3 sd) {
  float ry = max(rd.y, 0.003);
  float3 d = normalize(float3(rd.x, ry, rd.z));
  float oy = RE + 2.0;
  float b = oy * d.y;
  float len = -b + sqrt(b * b - (oy * oy - RAT * RAT));
  float mu = dot(d, sd);
  float rph = 3.0 / (16.0 * PI) * (1.0 + mu * mu);
  float g = 0.76;
  float mph = 3.0 / (8.0 * PI) * ((1.0 - g * g) * (1.0 + mu * mu)) / ((2.0 + g * g) * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
  float odR = 0.0;
  float odM = 0.0;
  float3 sR = float3(0.0);
  float3 sM = float3(0.0);
  for (int i = 0; i < 10; i++) {
    if (float(i) >= uSteps) break;
    float u = (float(i) + 0.5) / uSteps;
    float t = len * u * u;
    float dt = len * 2.0 * u / uSteps;
    float3 p = float3(d.x * t, oy + d.y * t, d.z * t);
    float r = length(p);
    float h = max(r - RE, 0.0);
    float er = exp(-h / HR);
    float em = exp(-h / HM);
    float hr = er * dt;
    float hm = em * dt;
    odR += hr;
    odM += hm;
    float cz = dot(p, sd) / r;
    float m = airmass(cz);
    float sh = clamp(cz * 20.0 + 1.0, 0.0, 1.0);
    float3 tauS = (BR * HR * er + uMie * 1.11 * HM * em + BO * HO) * m;
    float3 tau = BR * odR + uMie * 1.11 * odM + BO * HO * (odR / HR);
    float3 att = exp(-(tau + tauS)) * sh;
    sR += att * hr;
    sM += att * hm;
  }
  float3 col = 20.0 * (sR * BR * rph + sM * uMie * mph) * 0.6;
  float ds = clamp(-rd.y / 0.12, 0.0, 1.0);
  float lm = dot(col, LUMA);
  col = mix(col, float3(lm), 0.6 * ds);
  return col * (1.0 - 0.8 * ds);
}

float hash12(float2 p) {
  float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

float vnoise(float2 p) {
  float2 i = floor(p);
  float2 f = fract(p);
  float2 u = f * f * (3.0 - 2.0 * f);
  float a = hash12(mod(i, 256.0));
  float b = hash12(mod(i + float2(1.0, 0.0), 256.0));
  float c = hash12(mod(i + float2(0.0, 1.0), 256.0));
  float d = hash12(mod(i + float2(1.0, 1.0), 256.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p, float oct) {
  float s = 0.0;
  float a = 0.5;
  float norm = 0.0;
  for (int i = 0; i < 6; i++) {
    if (float(i) >= oct) break;
    s += a * vnoise(p);
    norm += a;
    p = p * 2.0 + float2(17.3, 9.1);
    a *= 0.5;
  }
  return s / max(norm, 0.0001);
}

float cloudField(float2 q, float detail, float oct) {
  float2 across = float2(-uWindDir.y, uWindDir.x);
  float2 a = float2(dot(q, uWindDir) * uStyle.x, dot(q, across) * uStyle.y);
  float2 p = a * 1.15;
  float wx = fbm(p * 0.5 + float2(3.1, 7.7), 2.0) - 0.5;
  float wy = fbm(p * 0.5 + float2(11.5, 2.3), 2.0) - 0.5;
  p += float2(wx, wy) * 1.4;
  float base = fbm(p, oct);
  float cov = fbm(p * 0.2 + float2(5.0, 1.0), 3.0);
  float f = (cov * 0.55 + base * 0.45 - 0.5) * 2.4 + 0.5;
  if (detail > 0.0) {
    float det = fbm(p * 3.5 + float2(20.0, 8.0), 3.0);
    f += 0.34 * (det - 0.5) * detail * uStyle.w;
  }
  return f;
}

float cirrusField(float2 q) {
  float2 across = float2(-uWindDir.y, uWindDir.x);
  float2 p = float2(dot(q, uWindDir) * 0.30, dot(q, across) * 1.7);
  float w = fbm(p * 0.5 + float2(2.0, 5.0), 2.0) - 0.5;
  p += float2(w * 0.8, w * 1.6);
  float n = fbm(p, min(uOct + 1.0, 4.0));
  float ridged = 1.0 - abs(2.0 * n - 1.0);
  float mask = fbm(p * 0.13 + float2(9.0, 3.0), 3.0);
  return clamp((mask - 0.50) * 2.6, 0.0, 1.0) * pow(ridged, 3.0);
}

float3 moonTexel(float2 c) {
  float2 p = c - 0.5;
  float2 i = floor(p);
  float2 f = p - i;
  float x0 = mod(i.x, uMoonSize.x);
  float x1 = mod(i.x + 1.0, uMoonSize.x);
  float y0 = clamp(i.y, 0.0, uMoonSize.y - 1.0);
  float y1 = clamp(i.y + 1.0, 0.0, uMoonSize.y - 1.0);
  float3 a = uMoonTex.eval(float2(x0 + 0.5, y0 + 0.5)).rgb;
  float3 b = uMoonTex.eval(float2(x1 + 0.5, y0 + 0.5)).rgb;
  float3 c2 = uMoonTex.eval(float2(x0 + 0.5, y1 + 0.5)).rgb;
  float3 d = uMoonTex.eval(float2(x1 + 0.5, y1 + 0.5)).rgb;
  return mix(mix(a, b, f.x), mix(c2, d, f.x), f.y);
}

float starField(float3 rd, float pixelAngle) {
  float a = uSkyEq.y * rd.y - uSkyEq.x * rd.z;
  float b = -rd.x;
  float x = uSkyEq.w * a + uSkyEq.z * b;
  float y = uSkyEq.z * a - uSkyEq.w * b;
  float z = uSkyEq.x * rd.y + uSkyEq.y * rd.z;
  float ra = atan(y, x);
  float cu = (ra / (2.0 * PI) + 0.5) * 96.0;
  float cv = (z * 0.5 + 0.5) * 48.0;
  float2 cell = floor(float2(cu, cv));
  float2 fr = float2(cu, cv) - cell;
  if (hash12(cell + 7.0) > 0.30) return 0.0;
  float2 sp = float2(hash12(cell + 31.7), hash12(cell + 91.3)) * 0.6 + 0.2;
  float cosd = sqrt(max(1.0 - z * z, 0.0004));
  float dx = (fr.x - sp.x) * (2.0 * PI / 96.0) * cosd;
  float dy = (fr.y - sp.y) * (2.0 / 48.0) / cosd;
  float r = max(0.0016, pixelAngle * 0.9);
  float mag = pow(hash12(cell + 53.1), 5.0);
  float flux = (0.06 + 0.94 * mag) * (0.0016 * 0.0016) / (r * r);
  return flux * exp(-(dx * dx + dy * dy) / (r * r));
}

float3 aces(float3 c) {
  return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

half4 main(float2 fc) {
  float2 uv = float2(fc.x - 0.5 * uRes.x, 0.5 * uRes.y - fc.y) / uRes.y;
  float ur = length(uv);
  float theta = 2.0 * atan(ur / (2.0 * 0.5361));
  float3 radial = (uRight * uv.x + uUp * uv.y) / max(ur, 0.00001);
  float3 rd = normalize(uFwd * cos(theta) + radial * sin(theta));
  float pixelAngle = (1.0 / uRes.y) / 0.5361;
  float3 sky = skyColor(rd, uSun) + float3(0.0030, 0.0040, 0.0085) * (0.5 + 1.5 * pow(1.0 - clamp(rd.y, 0.0, 1.0), 3.0));
  float skyLum = dot(sky, LUMA);
  float horizon = smoothstep(-0.015, 0.01, rd.y);

  float3 moonCol = float3(0.0);
  float3 halo = float3(0.0);
  if (uMoonA.w > 0.5) {
    float md = dot(rd, uMoonDir);
    if (md > 0.9) {
      float ang = acos(clamp(md, -1.0, 1.0));
      halo = uMoonT * uMoonB.y * (0.0040 / (1.0 + pow(ang / 0.03, 1.5)));
      float rho = uMoonA.x;
      float2 p = float2(dot(rd, uMoonRight), dot(rd, uMoonUp)) / md / rho;
      float r = length(p);
      float pix = pixelAngle / rho;
      float coverage = clamp((1.0 - r) / pix + 0.5, 0.0, 1.0);
      if (coverage > 0.0) {
        float2 q = p / max(r, 1.0);
        float nz = sqrt(max(1.0 - dot(q, q), 0.0));
        float e = q.x * uMoonA.y - q.y * uMoonA.z;
        float nn = q.x * uMoonA.z + q.y * uMoonA.y;
        float lon = atan(e, nz);
        float lat = asin(clamp(nn, -1.0, 1.0));
        float2 tc = float2((0.5 + lon / (2.0 * PI)) * uMoonSize.x, (0.5 - lat / PI) * uMoonSize.y);
        float3 albedo = pow(moonTexel(tc), float3(2.2));
        float mu0 = dot(float3(q.x, q.y, nz), uMoonL);
        float lit = mu0 > 0.0 ? mix(mu0, 2.0 * mu0 / (mu0 + nz + 0.0001), 0.7) : 0.0;
        float shade = 1.0;
        float3 tint = float3(1.0);
        if (uEcl.w > 0.0) {
          float dd = length(q - uEcl.xy);
          float inPen = smoothstep(uEcl.w, uEcl.z, dd);
          float inUmb = 1.0 - smoothstep(uEcl.z - 0.10, uEcl.z + 0.10, dd);
          float umbra = 0.22 + 0.45 * pow(clamp(dd / max(uEcl.z, 0.001), 0.0, 1.0), 2.0);
          shade = mix(1.0 - 0.45 * inPen, umbra, inUmb);
          tint = mix(float3(1.0), float3(1.0, 0.33, 0.10), inUmb);
        }
        float3 earth = float3(0.85, 0.95, 1.1) * uMoonB.x * nz;
        moonCol = albedo * (lit * shade * tint + earth) * 0.22 * uMoonT * coverage * horizon;
      }
    }
  }

  float vis = 1.0 - smoothstep(0.004, 0.03, dot(sky + halo, LUMA));
  float3 stars = float3(0.0);
  if (vis > 0.001 && rd.y > -0.015) {
    stars = float3(0.85, 0.92, 1.0) * starField(rd, pixelAngle) * 0.5 * vis * horizon;
  }
  float fl = uFlash.w * (0.10 + 0.90 * exp(-(1.0 - dot(rd, uFlash.xyz)) * 18.0));
  float3 skyFull = sky + halo * horizon + moonCol + stars + float3(0.7, 0.78, 1.0) * fl * 0.3;

  float sunI = clamp(uSun.y * 8.0 + 0.05, 0.0, 1.0);
  float cosg = dot(rd, uSun);
  float ry = rd.y;
  float3 amb = uAmb + float3(0.0045, 0.0058, 0.0110) * (1.0 + 2.5 * uThick * uCover);
  float moonPhase = 0.7 + 0.6 * pow(clamp(dot(rd, uMoonDir), 0.0, 1.0), 8.0);

  float3 base = skyFull;
  float tHigh = 1.0;
  if (uCirrus > 0.001 && ry > 0.05) {
    float hh = 8.0 / max(ry, 0.05);
    float2 qh = float2(rd.x * hh, rd.z * hh) - uOffHigh;
    float dh = cirrusField(qh) * smoothstep(0.05, 0.2, ry);
    float tauH = 2.2 * uCirrus * dh / max(ry, 0.3);
    float aH = 1.0 - exp(-tauH);
    float sunIH = clamp(uSun.y * 8.0 + 0.5, 0.0, 1.0);
    float3 cH = uSunTHigh * sunIH * (0.8 + 1.6 * pow(clamp(cosg, 0.0, 1.0), 8.0)) + amb * 0.9 + uMoonLight * moonPhase;
    base = mix(base, cH, aH);
    tHigh = 1.0 - aH;
  }

  float th = 1.6 / max(ry, 0.03);
  float2 q2 = float2(rd.x * th, rd.z * th) - uOff;
  float t = 0.90 - 0.83 * uCover;
  float dfade = smoothstep(0.05, 0.45, ry) * clamp((uOct - 2.0) / 2.0, 0.0, 1.0);
  float f = -1.0;
  float dens = 0.0;
  float thk = 0.0;
  float occ = 0.0;
  if (ry > 0.035) {
    float cOct = mix(2.0, uOct, smoothstep(0.06, 0.3, ry));
    f = cloudField(q2, dfade, cOct);
    float farFade = smoothstep(0.035, 0.1, ry);
    dens = pow(clamp((f - t) / uStyle.z, 0.0, 1.0), 1.4) * mix(smoothstep(0.03, 0.2, ry), 1.0, smoothstep(0.7, 0.95, uCover)) * farFade;
    thk = clamp((f - t) / 0.55, 0.0, 1.0);
    if (dens > 0.002 && uShadow > 0.5 && ry > 0.08) {
      float2 sh = uSun.xz / max(length(uSun.xz), 1e-3);
      for (int k = 0; k < 3; k++) {
        if (float(k) >= uShadow) break;
        float s = 0.2 + 0.3 * float(k);
        float fs = cloudField(q2 + sh * s, dfade, cOct);
        occ += clamp((fs - t) / 0.3, 0.0, 1.0) / uShadow;
      }
    }
  }
  float k = uThick * uCover;
  float3 ambc = amb * (1.0 - 0.8 * k) + dot(amb, LUMA) * 0.8 * k * float3(0.92, 0.97, 1.12);
  float phase = 0.6 + 1.4 * pow(clamp(cosg, 0.0, 1.0), 6.0);
  float silver = 1.0 + 3.0 * pow(clamp(cosg, 0.0, 1.0), 24.0) * (1.0 - thk);
  float direct = exp(-occ * (1.4 + 2.2 * uThick)) * (0.3 + 0.7 * exp(-thk * (1.2 + 1.6 * uThick)));
  float3 sunl = uSunT * sunI * direct * phase * silver * 2.0 * (1.0 - 0.6 * k) * (1.0 - 0.5 * exp(-3.0 * dens));
  float3 moonl = uMoonLight * moonPhase * (1.0 - 0.5 * thk);
  float3 diffuse = uSunT * sunI * 0.5 * k * exp(-thk * 1.5) * (0.6 + 0.4 * (1.0 - occ));
  float3 cl = ambc * 1.15 * (1.0 - (0.25 + 0.4 * uThick) * thk) + sunl + moonl + diffuse;
  float slant = mix(1.0, 1.0 / max(ry, 0.2), 0.5);
  float tauL = (1.0 + 3.0 * uThick) * dens * 1.3 * slant;
  float alpha = 1.0 - exp(-tauL);
  cl *= mix(1.1, 0.78, clamp((f - t) / 0.8, 0.0, 1.0));
  cl *= 1.0 - 0.55 * uDark;
  cl += float3(0.75, 0.82, 1.0) * fl * 1.6 * (1.0 - 0.4 * thk);
  float3 col = mix(base, cl, alpha);
  float transmit = tHigh * (1.0 - alpha);

  float ang = acos(clamp(cosg, -1.0, 1.0));
  float r = 0.070;
  float disc = smoothstep(r * 1.06, r * 0.92, ang) * step(-0.02, rd.y);
  float limb = 1.0 - 0.25 * (1.0 - sqrt(clamp(1.0 - (ang / r) * (ang / r), 0.0, 1.0)));
  float glow = 4.0 / (1.0 + pow(ang / 0.085, 2.4)) + 0.35 * exp(-ang / 0.18);
  float lining = 0.5 * pow(clamp(cosg, 0.0, 1.0), 12.0) * alpha * (1.0 - 0.6 * thk);
  col += uSunT * sunI * (limb * 30.0 * disc * pow(transmit, 3.0) + glow * transmit + lining);
  col *= 1.0 - 0.3 * uDark;
  if (uFog > 0.001) {
    float fv = fbm(q2 * 0.35 + float2(4.0, 9.0), 2.0);
    float dFog = uFog * (0.75 + 0.5 * fv);
    float fogT = exp(-dFog * 3.2 * mix(1.0, 1.0 / max(ry, 0.25), 0.5));
    float fl0 = dot(uAmb, LUMA);
    float3 fogc = float3(fl0) * float3(1.0, 1.02, 1.08) * 2.4 + uSunT * sunI * 0.10 * (1.0 + 2.0 * pow(clamp(cosg, 0.0, 1.0), 6.0)) + float3(0.0045, 0.0058, 0.0110) * 3.0 + uMoonLight * 2.0;
    col = mix(fogc, col, fogT);
  }
  col = pow(aces(col * uGain), float3(1.0 / 2.2));
  col += (hash12(fc) - 0.5) / 255.0;
  col = clamp(col, 0.0, 1.0);
  return half4(half3(col * uFade), half(uFade));
}
"""
