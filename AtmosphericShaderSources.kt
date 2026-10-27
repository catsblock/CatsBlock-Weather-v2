package com.catsblock.weather.atmosphere

internal object AtmosphericShaderSources {
    const val CLOUDS = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uCloudCover;
        uniform float uWindKmh;
        uniform float uSunElevation;
        layout(color) uniform half4 uSunColor;
        layout(color) uniform half4 uSkyTint;

        float rayleighPhase(float cosineAngle) {
            return 0.75 * (1.0 + cosineAngle * cosineAngle);
        }

        float miePhase(float cosineAngle) {
            float asymmetry = 0.76;
            float denominator = max(0.02, 1.0 + asymmetry * asymmetry - 2.0 * asymmetry * cosineAngle);
            return (1.0 - asymmetry * asymmetry) / (denominator * sqrt(denominator));
        }

        float hash31(float3 p) {
            p = fract(p * 0.1031);
            p += dot(p, p.yzx + 33.33);
            return fract((p.x + p.y) * p.z);
        }

        float noise3(float3 p) {
            float3 i = floor(p);
            float3 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            float n000 = hash31(i + float3(0.0, 0.0, 0.0));
            float n100 = hash31(i + float3(1.0, 0.0, 0.0));
            float n010 = hash31(i + float3(0.0, 1.0, 0.0));
            float n110 = hash31(i + float3(1.0, 1.0, 0.0));
            float n001 = hash31(i + float3(0.0, 0.0, 1.0));
            float n101 = hash31(i + float3(1.0, 0.0, 1.0));
            float n011 = hash31(i + float3(0.0, 1.0, 1.0));
            float n111 = hash31(i + float3(1.0, 1.0, 1.0));
            float x00 = mix(n000, n100, f.x);
            float x10 = mix(n010, n110, f.x);
            float x01 = mix(n001, n101, f.x);
            float x11 = mix(n011, n111, f.x);
            return mix(mix(x00, x10, f.y), mix(x01, x11, f.y), f.z);
        }

        float fbm3(float3 p) {
            float value = 0.0;
            float amplitude = 0.5;
            for (int i = 0; i < 2; i++) {
                value += noise3(p) * amplitude;
                p = p * 2.03 + float3(17.1, 9.2, 13.7);
                amplitude *= 0.5;
            }
            return value;
        }


        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            if (uCloudCover < 0.055) return half4(0.0);
            float drift = uTime * (0.0015 + uWindKmh * 0.000012);
            float2 base = float2(uv.x * 3.2 + drift, uv.y * 2.1);
            float domainWarp = fbm3(float3(base * 0.82, uTime * 0.0018));
            float broadField = fbm3(float3(base * 1.17 + 4.3, uTime * 0.0012));
            float cover = clamp(uCloudCover, 0.0, 1.0);
            float opacity = 0.0;
            float transmittance = 1.0;
            float lighting = 0.0;
            for (int i = 0; i < 8; i++) {
                float depth = (float(i) + 0.5) / 8.0;
                float3 position = float3(base.x + depth * 0.51, base.y - depth * 0.23, depth * 2.8);
                position += float3(domainWarp * 0.24, domainWarp * 0.15, uTime * 0.0012);
                float detail = noise3(position * 1.72 + float3(0.0, uTime * 0.001, 2.3));
                float density = smoothstep(0.47 - cover * 0.25, 0.82, detail + (broadField - 0.5) * 0.48);
                float extinction = density * (0.035 + cover * 0.10);
                opacity += transmittance * extinction;
                transmittance *= 1.0 - extinction;
                lighting += density * (1.0 - depth) / 12.0;
            }
            float horizonFade = smoothstep(0.015, 0.16, uv.y) * (1.0 - smoothstep(0.82, 0.99, uv.y));
            float alpha = clamp(opacity * 3.15 * horizonFade, 0.0, 0.56);
            if (alpha <= 0.003) return half4(0.0);
            half3 sunLinear = toLinearSrgb(uSunColor.rgb);
            half3 skyLinear = toLinearSrgb(uSkyTint.rgb);
            float viewAngle = clamp((uv.x - 0.5) * 1.7 + uSunElevation * 0.24, -1.0, 1.0);
            float rayleigh = rayleighPhase(viewAngle);
            float mie = miePhase(viewAngle);
            float sunScatter = clamp(0.12 + uSunElevation * 0.45 + lighting * 0.7 + mie * 0.025, 0.0, 1.0);
            half3 atmosphericLinear = skyLinear * (0.72 + rayleigh * 0.06);
            half3 cloudBaseLinear = toLinearSrgb(half3(0.84, 0.87, 0.89));
            half3 cloudLinear = mix(atmosphericLinear, cloudBaseLinear, 0.58);
            cloudLinear = mix(cloudLinear, sunLinear, sunScatter * 0.42);
            half3 cloudColor = fromLinearSrgb(cloudLinear);
            return half4(cloudColor * alpha, alpha);
        }
    """

    const val RAIN_LENS = """
        uniform shader uContent;
        uniform float2 uResolution;
        uniform float uTime;
        uniform float uIntensity;
        uniform float uViscosity;

        float hash11(float n) {
            n = fract(n * 0.1031);
            n *= n + 33.33;
            n *= n + n;
            return fract(n);
        }

        float sdEllipse(float2 point, float2 radius) {
            return length(point / radius) - 1.0;
        }

        half4 main(float2 fragCoord) {
            float2 resolution = max(uResolution, float2(1.0));
            float2 uv = fragCoord / resolution;
            if (uIntensity <= 0.001) return uContent.eval(fragCoord);
            float2 refractOffset = float2(0.0);
            float highlightStrength = 0.0;
            float rimStrength = 0.0;
            float rippleStrength = 0.0;
            float shadeStrength = 0.0;
            for (int i = 0; i < 14; i++) {
                float index = float(i) + 1.0;
                float seedX = hash11(index * 1.73);
                float seedY = hash11(index * 3.11 + 4.7);
                float speed = (0.004 + hash11(index * 5.3) * 0.014) * (1.0 - uViscosity * 0.28);
                float2 center = float2(seedX, fract(seedY + uTime * speed));
                float rx = 0.007 + hash11(index * 7.9) * 0.009;
                float ry = rx * (1.12 + uIntensity * 0.95);
                float2 delta = uv - center;
                float2 radius = float2(rx, ry);
                float2 q = delta / radius;
                float signedDistance = sdEllipse(delta, radius);
                float radial = signedDistance + 1.0;
                float inside = 1.0 - smoothstep(0.72, 1.0, radial);
                float rim = smoothstep(0.86, 1.0, radial) * (1.0 - smoothstep(1.0, 1.12, radial));
                float lensDepth = sqrt(max(0.0, 1.0 - min(radial * radial, 1.0)));
                float refractionBand = 0.5 + 0.5 * sin(radial * radial * 18.0 - uTime * (1.6 + speed * 24.0));
                float highlight = 1.0 - smoothstep(0.12, 0.96, length(q - float2(-0.28, -0.34)));
                float gravityTrail = smoothstep(0.16, 1.0, q.y) * (1.0 - smoothstep(0.95, 1.24, abs(q.x)));
                float rippleDamping = exp(-abs(radial - 0.68) * (5.0 + uViscosity * 8.0));
                float ripple = (0.5 + 0.5 * sin(radial * 28.0 - uTime * (2.0 + (1.0 - uViscosity) * 2.0))) * rippleDamping;
                float2 normal = normalize(q + float2(0.0001, 0.0001));
                refractOffset -= normal * float2(rx * resolution.x, ry * resolution.y) * inside * lensDepth * 0.18 * uIntensity;
                highlightStrength = max(highlightStrength, highlight * inside);
                rimStrength = max(rimStrength, rim);
                rippleStrength = max(rippleStrength, ripple * inside);
                shadeStrength = max(shadeStrength, inside * lensDepth * (0.5 + refractionBand * 0.5));
            }
            half4 scene = uContent.eval(clamp(fragCoord + refractOffset, float2(0.0), resolution));
            half3 coolLens = half3(0.53, 0.65, 0.73);
            half3 brightLens = half3(0.98, 0.99, 1.0);
            half3 result = mix(scene.rgb, coolLens, shadeStrength * uIntensity * 0.075);
            result = mix(result, half3(0.18, 0.25, 0.31), rimStrength * uIntensity * 0.22);
            result = mix(result, brightLens, clamp(highlightStrength * uIntensity * 0.44 + rippleStrength * uIntensity * 0.07, 0.0, 0.58));
            float alpha = clamp(scene.a, 0.0, 1.0);
            return half4(result, alpha);
        }
    """
}
