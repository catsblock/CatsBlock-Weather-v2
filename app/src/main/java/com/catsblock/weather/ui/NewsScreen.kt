package com.catsblock.weather.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.NewsLoad
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr
import com.catsblock.weather.ui.typography.GoogleSansFlex
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val SEARCH_DEBOUNCE_MS = 2500L

internal fun newsDescription(value: String): String {
    val clean = value.replace(Regex("\\s+"), " ").trim()
    if (clean.length <= 75) return clean
    val cut = clean.substring(0, 75).substringBeforeLast(" ").trimEnd()
    return "$cut…"
}

internal fun publishedLabel(epochMs: Long, locale: Locale): String {
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

private enum class NewsPhase { Loading, Ready, Empty, Error, Unavailable }

@Composable
fun NewsScreen(vm: WeatherViewModel) {
    val city by vm.city.collectAsStateWithLifecycle()
    val language = I18n.lang
    val locale = language.locale
    val region = city?.code?.takeIf { it.length == 2 } ?: NewsFeed.regionFor(language.code)
    val news: NewsViewModel = viewModel()
    val state by news.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    var openedId by rememberSaveable { mutableStateOf<String?>(null) }
    val scheme = MaterialTheme.colorScheme
    val side = detailSidePadding()
    val topInset = LocalBottomInset.current

    LaunchedEffect(query) {
        delay(SEARCH_DEBOUNCE_MS)
        debounced = query.trim().replace(Regex("\\s+"), " ")
    }
    LaunchedEffect(language.code, region, debounced) {
        news.ensureLoaded(language.code, region, debounced)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { index -> news.loadMoreIfNeeded(index) }
    }


    val phase = when {
        !state.available -> NewsPhase.Unavailable
        state.articles.isNotEmpty() -> NewsPhase.Ready
        state.loading -> NewsPhase.Loading
        state.failed -> NewsPhase.Error
        state.status != 0 && state.status != 1 -> NewsPhase.Error
        else -> NewsPhase.Empty
    }

    Column(Modifier.fillMaxSize().background(scheme.background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = side).padding(top = 28.dp)) {
            Text(
                tr("Weather news"),
                color = scheme.onSurface,
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
                shape = RoundedCornerShape(20.dp),
                placeholder = { Text(tr("Search news")) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = tr("Close"), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val normalized = query.trim().replace(Regex("\\s+"), " ")
                    if (normalized != debounced) debounced = normalized else news.refresh()
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = scheme.primary.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh
                )
            )
            if (state.refreshing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHigh
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "newsPhase"
            ) { target ->
                when (target) {
                    NewsPhase.Loading -> NewsSkeletonFeed()
                    NewsPhase.Unavailable -> NewsMessage(
                        tr("News unavailable"),
                        tr("The news service is not available right now.")
                    ) { news.refresh() }
                    NewsPhase.Error -> NewsMessage(
                        if (state.status == 429) tr("Weather news") else tr("News unavailable"),
                        if (state.status == 429) tr("Too many requests. Try again later.") else tr("The news service is not available right now.")
                    ) { news.refresh() }
                    NewsPhase.Empty -> NewsMessage(
                        tr("No meteorological news found"),
                        tr("Try a different search or check again later.")
                    ) { news.refresh() }
                    NewsPhase.Ready -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = side, end = side, top = 8.dp, bottom = topInset + 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.articles, key = { it.id }) { article ->
                            NewsCard(
                                article = article,
                                locale = locale,
                                onOpen = { openedId = article.id },
                                modifier = Modifier.animateItem()
                            )
                        }
                        if (state.cursor != null) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.loading) {
                                        NewsSkeletonMore()
                                    } else {
                                        TextButton(onClick = { news.loadMore() }) { Text(tr("Retry")) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (state.articles.isNotEmpty() && state.failed) {
                Surface(
                    onClick = { news.refresh() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = side).padding(bottom = 12.dp),
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        tr("The news service is not available right now."),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = scheme.onSurface,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
    val opened = state.articles.firstOrNull { it.id == openedId }
    if (opened != null) {
        NewsBrowserDialog(opened) { openedId = null }
    }

}

@Composable
internal fun EventNewsSection(event: String, region: String) {
    val language = I18n.lang
    val locale = language.locale
    var articles by remember(event, language.code, region) { mutableStateOf<List<NewsArticle>>(emptyList()) }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }
    var loading by remember(event, language.code, region) { mutableStateOf(true) }
    LaunchedEffect(event, language.code, region) {
        loading = true
        articles = when (val result = NewsFeed.event(language.code, region, event)) {
            is NewsLoad.Success -> result.page.articles.take(10)
            is NewsLoad.Failure -> emptyList()
        }
        loading = false
    }
    if (loading) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(tr("Related meteorological news"), style = MaterialTheme.typography.titleMedium)
            NewsSkeletonCard()
        }
        return
    }
    if (articles.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Related meteorological news"), style = MaterialTheme.typography.titleMedium)
        articles.forEach { article ->
            NewsCard(article = article, locale = locale, onOpen = { selected = article })
        }
    }
    selected?.let { NewsBrowserDialog(it) { selected = null } }
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
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(tr("Retry")) }
    }
}
