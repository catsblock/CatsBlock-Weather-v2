package com.catsblock.weather.ui.sky

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalContext
import com.catsblock.weather.data.Eclipse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val MOON_TEXTURE = "moon/moon_albedo_512.jpg"
private val textureCache = ConcurrentHashMap<String, Bitmap>()

internal const val MOON_ORB_AGSL = """
uniform float2 uRes;
uniform shader uTex;
uniform float2 uTexSize;
uniform float uReady;
uniform float2 uRot;
uniform float3 uL;
uniform float uEarth;
uniform float4 uEcl;
uniform float uHalo;

const float PI = 3.14159265;
const float FILL = 0.82;

float3 texel(float2 c) {
  float2 p = c - 0.5;
  float2 i = floor(p);
  float2 f = p - i;
  float x0 = mod(i.x, uTexSize.x);
  float x1 = mod(i.x + 1.0, uTexSize.x);
  float y0 = clamp(i.y, 0.0, uTexSize.y - 1.0);
  float y1 = clamp(i.y + 1.0, 0.0, uTexSize.y - 1.0);
  float3 a = uTex.eval(float2(x0 + 0.5, y0 + 0.5)).rgb;
  float3 b = uTex.eval(float2(x1 + 0.5, y0 + 0.5)).rgb;
  float3 c2 = uTex.eval(float2(x0 + 0.5, y1 + 0.5)).rgb;
  float3 d = uTex.eval(float2(x1 + 0.5, y1 + 0.5)).rgb;
  return mix(mix(a, b, f.x), mix(c2, d, f.x), f.y);
}

float3 aces(float3 c) {
  return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

half4 main(float2 fc) {
  float radiusPx = 0.5 * min(uRes.x, uRes.y) * FILL;
  float2 p = float2(fc.x - 0.5 * uRes.x, 0.5 * uRes.y - fc.y) / radiusPx;
  float r = length(p);
  float coverage = clamp((1.0 - r) * radiusPx + 0.5, 0.0, 1.0);
  float edge = 1.0 / FILL;
  float glowA = uHalo * 0.30 * exp(-max(r - 1.0, 0.0) * 3.5) * (1.0 - smoothstep(1.0, edge, r)) * (1.0 - coverage);
  float3 glowC = float3(0.80, 0.86, 1.0);
  float3 col = float3(0.0);
  if (coverage > 0.0) {
    float2 q = p / max(r, 1.0);
    float nz = sqrt(max(1.0 - dot(q, q), 0.0));
    float e = q.x * uRot.x - q.y * uRot.y;
    float nn = q.x * uRot.y + q.y * uRot.x;
    float lon = atan(e, nz);
    float lat = asin(clamp(nn, -1.0, 1.0));
    float2 tc = float2((0.5 + lon / (2.0 * PI)) * uTexSize.x, (0.5 - lat / PI) * uTexSize.y);
    float3 albedo = uReady > 0.5 ? pow(texel(tc), float3(2.2)) : float3(0.3);
    float mu0 = dot(float3(q.x, q.y, nz), uL);
    float lit = mu0 > 0.0 ? mix(mu0, 2.0 * mu0 / (mu0 + nz + 0.0001), 0.7) : 0.0;
    float shade = 1.0;
    float3 redLight = float3(0.0);
    if (uEcl.w > 0.0) {
      float dd = length(q - uEcl.xy);
      float inPen = smoothstep(uEcl.w, uEcl.z, dd);
      float inUmb = 1.0 - smoothstep(uEcl.z - 0.05, uEcl.z + 0.05, dd);
      float r01 = clamp(dd / max(uEcl.z, 0.001), 0.0, 1.0);
      float3 redCol = mix(float3(0.22, 0.025, 0.010), float3(0.85, 0.20, 0.05), pow(r01, 2.5));
      shade = (1.0 - 0.35 * inPen) * (1.0 - inUmb);
      redLight = redCol * inUmb * 0.5 * sqrt(nz);
    }
    float3 earth = float3(0.85, 0.95, 1.1) * uEarth * nz;
    float3 lin = albedo * (lit * shade + redLight + earth) * 2.4;
    col = pow(aces(lin), float3(1.0 / 2.2));
  }
  float a = coverage + glowA * (1.0 - coverage);
  float3 rgb = col * coverage + glowC * glowA * (1.0 - coverage);
  return half4(half3(rgb), half(a));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun MoonShaderOrb(epochMs: Long, eclipse: Eclipse?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val shader = remember { RuntimeShader(MOON_ORB_AGSL) }
    val texture by produceState(textureCache[MOON_TEXTURE]) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open(MOON_TEXTURE).use { BitmapFactory.decodeStream(it) } }
                    .getOrNull()
                    ?.also { textureCache[MOON_TEXTURE] = it }
            }
        }
    }
    val placeholder = remember { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    val active = texture ?: placeholder
    val ready = texture != null
    remember(shader, active) {
        shader.setInputShader("uTex", BitmapShader(active, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
    }
    val brush = remember(shader) { ShaderBrush(shader) }
    val minute = epochMs / 60_000L
    val disk = remember(minute, eclipse) { SkyAstro.moonDisk(epochMs, eclipse) }
    val illumination = remember(disk) { (disk.light[2] + 1f) / 2f }
    Box(
        modifier.drawBehind {
            shader.setFloatUniform("uRes", size.width, size.height)
            shader.setFloatUniform("uTexSize", active.width.toFloat(), active.height.toFloat())
            shader.setFloatUniform("uReady", if (ready) 1f else 0f)
            shader.setFloatUniform("uRot", disk.cosTheta, disk.sinTheta)
            shader.setFloatUniform("uL", disk.light[0], disk.light[1], disk.light[2])
            shader.setFloatUniform("uEarth", disk.earthshine)
            shader.setFloatUniform("uEcl", disk.eclipse[0], disk.eclipse[1], disk.eclipse[2], disk.eclipse[3])
            shader.setFloatUniform("uHalo", illumination)
            drawRect(brush)
        }
    )
}
