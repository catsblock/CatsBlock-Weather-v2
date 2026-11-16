package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex
import java.util.Locale

@Composable
fun NewsCard(
    article: NewsArticle,
    locale: Locale,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
            Row {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(
                            article.source.ifBlank { tr("Source") },
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val date = publishedLabel(article.publishedAt, locale)
                        if (date.isNotEmpty()) Text(" · $date", color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        article.title,
                        color = scheme.onSurface,
                        fontFamily = GoogleSansFlex.family,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                        letterSpacing = (-0.2).sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (article.summary.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            newsDescription(article.summary),
                            color = scheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                article.imageUrl?.takeIf(String::isNotBlank)?.let { image ->
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerHighest)
                    )
                }
            }
        }
    }
}
