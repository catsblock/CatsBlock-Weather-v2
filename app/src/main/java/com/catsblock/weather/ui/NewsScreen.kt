package com.catsblock.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.NewsLikeResult
import com.catsblock.weather.data.NewsLoad
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.components.ExpressiveLoader
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.min

private const val SEARCH_DEBOUNCE_MS = 2500L

private fun publishedLabel(epochMs: Long, locale: Locale): String {
    if (epochMs <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = Instant.now().atZone(zone).toLocalDate()
    val date = time.toLocalDate()
    return when {
        date == today -> time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        date == today.minusDays(1) -> tr("Yesterday")
        else -> time.format(DateTimeFormatter.ofPattern(if (locale.language == "en") "MMM d" else "d MMM", locale))
    }
}

@Composable
fun NewsScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    val language = I18n.lang
    val locale = language.locale
    val region = city?.code?.takeIf { it.length == 2 } ?: NewsFeed.regionFor(language.code)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    var articles by remember { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    var liked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var message by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val available = remember { NewsFeed.available() }

    fun load(reset: Boolean) {
        if (loading) return
        loading = true
        if (reset) status = 0
        scope.launch {
            val next = if (reset) "" else cursor.orEmpty()
            when (val result = NewsFeed.page(language.code, region, debounced, next)) {
                is NewsLoad.Success -> {
                    val incoming = result.page.articles
                    val merged = if (reset) incoming else (articles + incoming).distinctBy { it.id }
                    articles = merged
                    cursor = result.page.cursor
                    status = 1
                }
                is NewsLoad.Failure -> status = if (reset) result.status else status
            }
            loading = false
        }
    }

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        debounced = query.trim()
    }
    LaunchedEffect(language.code, region, debounced, attempt) {
        cursor = null
        articles = emptyList()
        load(reset = true)
    }
    LaunchedEffect(listState, cursor, loading) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { index ->
                if (cursor != null && !loading && index >= articles.size - 2 && articles.isNotEmpty()) load(reset = false)
            }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            message = null
        }
    }
    fun like(article: NewsArticle) {
        if (article.id in liked) return
        scope.launch {
            when (val result = NewsFeed.like(article.id)) {
                is NewsLikeResult.Liked -> {
                    liked = liked + article.id
                    articles = articles.map { if (it.id == article.id) it.copy(likes = result.likes) else it }
                }
                is NewsLikeResult.AlreadyLiked -> {
                    liked = liked + article.id
                    articles = articles.map { if (it.id == article.id) it.copy(likes = result.likes) else it }
                }
                NewsLikeResult.LimitReached -> message = tr("Like limit reached")
                NewsLikeResult.RateLimited -> message = tr("Too many requests. Try again later.")
                NewsLikeResult.Unavailable, NewsLikeResult.Failed -> message = tr("The news service is not available right now.")
            }
        }
    }

    val side = detailSidePadding()
    val topInset = LocalBottomInset.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = side).padding(top = 28.dp)) {
            Text(
                tr("Weather news"),
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = GoogleSansFlex.family,
                fontWeight = FontWeight.SemiBold,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                letterSpacing = (-0.8).sp
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { if (it.length <= 60) query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text(tr("Search news")) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = tr("Close"))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            message?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !available -> NewsMessage(tr("News unavailable"), tr("The news service is not available right now.")) { attempt++ }
                status == 0 && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveLoader() }
                status == 0 && articles.isEmpty() -> NewsMessage(tr("News unavailable"), tr("The news service is not available right now.")) { attempt++ }
                status == 429 -> NewsMessage(tr("Weather news"), tr("Too many requests. Try again later.")) { attempt++ }
                articles.isEmpty() -> NewsMessage(tr("No meteorological news found"), tr("Try a different search or check again later.")) { attempt++ }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = topInset + 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(articles, key = { it.id }) { article ->
                        NewsCard(
                            article = article,
                            locale = locale,
                            liked = article.id in liked,
                            onLike = { like(article) },
                            onOpen = { selected = article }
                        )
                    }
                    if (cursor != null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                                } else {
                                    TextButton(onClick = { load(reset = false) }) { Text(tr("Retry")) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    selected?.let { article ->
        NewsBrowserDialog(article) { selected = null }
    }
}

@Composable
private fun NewsMessage(title: String, body: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = GoogleSansFlex.family,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(tr("Retry")) }
    }
}

@Composable
private fun NewsCard(
    article: NewsArticle,
    locale: Locale,
    liked: Boolean,
    onLike: () -> Unit,
    onOpen: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onOpen)
    ) {
        Row(Modifier.padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        article.source.ifBlank { tr("Source") },
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val date = publishedLabel(article.publishedAt, locale)
                    if (date.isNotEmpty()) {
                        Text(" · ", color = scheme.onSurfaceVariant, fontSize = 12.sp)
                        Text(date, color = scheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    article.title,
                    color = scheme.onSurface,
                    fontFamily = GoogleSansFlex.family,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
                if (article.summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        article.summary,
                        color = scheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (liked) scheme.primary.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = !liked, onClick = onLike)
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = tr("Like"),
                                tint = if (liked) scheme.primary else scheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (article.likes > 0) "${min(article.likes, NewsFeed.MAX_LIKES)}" else tr("Like"),
                                color = if (liked) scheme.primary else scheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            val image = article.imageUrl
            if (!image.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(RoundedCornerShape(14.dp))
                )
            }
        }
    }
}
