package com.catsblock.weather.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.catsblock.weather.i18n.tr
import kotlin.random.Random

private fun DrawScope.ball(c: Offset, r: Float, a: Color, b: Color) {
    drawCircle(
        Brush.radialGradient(
            listOf(a, b, Color(0xFF05070C)),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.35f),
            radius = r * 1.7f
        ),
        r,
        c
    )
}

@Composable
fun NightSkyCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), onClick) {
        CardHeader(Icons.Rounded.AutoAwesome, tr("Night sky"))
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF02040A), Color(0xFF14213D))))
                val rnd = Random(11)
                repeat(90) {
                    val p = Offset(rnd.nextFloat() * size.width, rnd.nextFloat() * size.height)
                    drawCircle(
                        Color.White.copy(alpha = 0.3f + rnd.nextFloat() * 0.7f),
                        0.6.dp.toPx() + rnd.nextFloat() * 1.2.dp.toPx(),
                        p
                    )
                }
                val g = Offset(size.width * 0.8f, size.height * 0.3f)
                rotate(-20f, g) {
                    drawOval(
                        Brush.radialGradient(listOf(Color(0x99C9B8FF), Color.Transparent), center = g, radius = 34.dp.toPx()),
                        Offset(g.x - 34.dp.toPx(), g.y - 12.dp.toPx()),
                        Size(68.dp.toPx(), 24.dp.toPx())
                    )
                }
                ball(Offset(size.width * 0.16f, size.height * 0.34f), 26.dp.toPx(), Color(0xFFF4EFD8), Color(0xFF8A8570))
                ball(Offset(size.width * 0.38f, size.height * 0.5f), 15.dp.toPx(), Color(0xFFE8C9A0), Color(0xFF8B5E3C))
                val s = Offset(size.width * 0.58f, size.height * 0.3f)
                val sr = 11.dp.toPx()
                ball(s, sr, Color(0xFFF0DDAA), Color(0xFF8C7648))
                rotate(-18f, s) {
                    drawOval(
                        Color(0xAAD8C48A),
                        Offset(s.x - sr * 2f, s.y - sr * 0.55f),
                        Size(sr * 4f, sr * 1.1f),
                        style = Stroke(2.dp.toPx())
                    )
                }
                ball(Offset(size.width * 0.72f, size.height * 0.62f), 7.dp.toPx(), Color(0xFFE07A4F), Color(0xFF6A2A15))
                ball(Offset(size.width * 0.9f, size.height * 0.7f), 4.5.dp.toPx(), Color(0xFF6C8CFF), Color(0xFF1B2F7A))
            }
            Text(
                tr("Point your phone at the sky to see the Moon, planets, stars and galaxies."),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
