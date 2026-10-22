package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.TrendingDown
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.catsblock.weather.data.AlertKind
import com.catsblock.weather.data.City
import com.catsblock.weather.data.WeatherAlert
import com.catsblock.weather.i18n.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private fun alertIcon(kind: AlertKind): ImageVector = when (kind) {
    AlertKind.LightRain -> Icons.Rounded.Umbrella
    AlertKind.LightSnow -> Icons.Rounded.AcUnit
    AlertKind.HeavyRain, AlertKind.HeavySnow -> Icons.Rounded.Warning
    AlertKind.Fog -> Icons.Rounded.Cloud
    AlertKind.Warm -> Icons.Rounded.TrendingUp
    AlertKind.Cool -> Icons.Rounded.TrendingDown
}

@Composable
fun AlertCard(alert: WeatherAlert, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(alertIcon(alert.kind), null, Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Text(
                alert.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private const val MAP_ZOOM = 8
private val TileSize = 170.dp

@Composable
fun PrecipMapCard(city: City, snow: Boolean, rate: Double, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val n = 1 shl MAP_ZOOM
    val tx = (city.lon + 180.0) / 360.0 * n
    val latRad = Math.toRadians(city.lat)
    val ty = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
    val ix = floor(tx).toInt()
    val iy = floor(ty).toInt()
    val fx = (tx - ix).toFloat()
    val fy = (ty - iy).toFloat()
    val label = tr(if (snow) "Snow" else "Rain")
    val tint = if (snow) Color(0x3380A0C8) else Color(0x33103A66)

    GlassCard(modifier.fillMaxWidth()) {
        CardHeader(if (snow) Icons.Rounded.AcUnit else Icons.Rounded.WaterDrop, label)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(20.dp))) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .requiredSize(TileSize * 3)
                    .offset(TileSize * (0.5f - fx), TileSize * (0.5f - fy))
            ) {
                Column {
                    for (dy in -1..1) {
                        Row {
                            for (dx in -1..1) {
                                val x = Math.floorMod(ix + dx, n)
                                val y = (iy + dy).coerceIn(0, n - 1)
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data("https://tile.openstreetmap.org/$MAP_ZOOM/$x/$y.png")
                                        .addHeader("User-Agent", "CatsBlockWeather/2.0")
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(TileSize),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.fillMaxSize().background(tint))
            Box(
                Modifier.align(Alignment.Center).size(56.dp).background(Color.White.copy(alpha = 0.22f), CircleShape)
            )
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(18.dp)
                    .background(Color(0xFF2196F3), CircleShape)
                    .border(3.dp, Color.White, CircleShape)
            )
            Text(
                "$label · ${f1(rate)} mm/h",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "© OpenStreetMap",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color(0x66000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White,
                fontSize = 9.sp
            )
        }
    }
}
