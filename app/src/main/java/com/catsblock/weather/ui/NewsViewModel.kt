package com.catsblock.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.AccountAuth
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.NewsLikeResult
import com.catsblock.weather.data.NewsLoad
import com.catsblock.weather.data.NewsReactionResult
import com.catsblock.weather.data.Reaction
import com.catsblock.weather.data.withLike
import com.catsblock.weather.data.withReaction
import com.catsblock.weather.i18n.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewsUiState(
    val articles: List<NewsArticle> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val failed: Boolean = false,
    val status: Int = 0,
    val message: String? = null,
    val available: Boolean = NewsFeed.available(),
    val authRequired: Boolean = false
)

class NewsViewModel : ViewModel() {
    private val _state = MutableStateFlow(NewsUiState())
    val state: StateFlow<NewsUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var generation = 0
    private var scopeKey = ""
    private var language = "en"
    private var region = "US"
    private var query = ""
    private var pendingLike: Pair<String, Boolean>? = null
    private var pendingReaction: Pair<String, String>? = null

    private val likeQueues = HashMap<String, LatestSender<Boolean>>()
    private val reactionQueues = HashMap<String, LatestSender<String>>()
    private val likeSnapshots = HashMap<String, Pair<Boolean, Int>>()
    private val reactionSnapshots = HashMap<String, Pair<String?, Map<String, Int>>>()
    private val likeBlockedUntil = HashMap<String, Long>()
    private val reactionBlockedUntil = HashMap<String, Long>()

    fun ensureLoaded(language: String, region: String, query: String) {
        this.language = language
        this.region = region
        this.query = query
        val target = "$language|$region|$query"
        val current = _state.value
        if (target == scopeKey && (current.loading || current.articles.isNotEmpty())) return
        scopeKey = target
        fetch(reset = true)
    }

    fun refresh() {
        fetch(reset = true)
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.cursor == null) return
        fetch(reset = false)
    }

    fun loadMoreIfNeeded(lastVisibleIndex: Int) {
        val current = _state.value
        if (current.loading || current.cursor == null) return
        if (lastVisibleIndex < current.articles.size - 2) return
        fetch(reset = false)
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun dismissAuth() {
        pendingLike = null
        pendingReaction = null
        _state.update { it.copy(authRequired = false) }
    }

    fun retryPending() {
        val like = pendingLike
        val reaction = pendingReaction
        pendingLike = null
        pendingReaction = null
        _state.update { it.copy(authRequired = false) }
        if (like != null) setLikeById(like.first, like.second)
        if (reaction != null) setReactionById(reaction.first, reaction.second)
    }

    fun setLike(article: NewsArticle, liked: Boolean) {
        if (!AccountAuth.loggedIn()) {
            pendingLike = article.id to liked
            _state.update { it.copy(authRequired = true) }
            return
        }
        setLikeById(article.id, liked)
    }

    private fun setLikeById(articleId: String, liked: Boolean) {
        val current = _state.value.articles.firstOrNull { it.id == articleId } ?: return
        if ((likeBlockedUntil[current.id] ?: 0L) > System.currentTimeMillis()) return
        if (current.liked == liked) return
        likeSnapshots.putIfAbsent(current.id, current.liked to current.likes)
        updateArticle(current.id) { it.withLike(liked) }
        likeQueue(current.id).submit(liked)
    }

    fun toggleReaction(article: NewsArticle, reaction: Reaction) {
        if (!AccountAuth.loggedIn()) {
            pendingReaction = article.id to reaction.emoji
            _state.update { it.copy(authRequired = true) }
            return
        }
        setReactionById(article.id, if (article.myReaction == reaction.emoji) "" else reaction.emoji)
    }

    private fun setReactionById(articleId: String, emoji: String) {
        val current = _state.value.articles.firstOrNull { it.id == articleId } ?: return
        if ((reactionBlockedUntil[current.id] ?: 0L) > System.currentTimeMillis()) return
        val target = emoji.takeIf { it.isNotEmpty() }
        if (current.myReaction == target) return
        reactionSnapshots.putIfAbsent(current.id, current.myReaction to current.reactions)
        updateArticle(current.id) { it.withReaction(target) }
        reactionQueue(current.id).submit(target.orEmpty())
    }

    private fun fetch(reset: Boolean) {
        val token = ++generation
        job?.cancel()
        val cursor = if (reset) "" else _state.value.cursor.orEmpty()
        if (!reset && cursor.isEmpty()) return
        _state.update {
            it.copy(
                loading = true,
                refreshing = reset && it.articles.isNotEmpty(),
                failed = false,
                status = if (reset && it.articles.isEmpty()) 0 else it.status
            )
        }
        job = viewModelScope.launch {
            val result = NewsFeed.page(language, region, query, cursor)
            if (token != generation) return@launch
            when (result) {
                is NewsLoad.Success -> {
                    result.page.articles.forEach { article ->
                        likeSnapshots[article.id] = article.liked to article.likes
                        reactionSnapshots[article.id] = article.myReaction to article.reactions
                    }
                    _state.update { current ->
                        val merged = if (reset) {
                            result.page.articles
                        } else {
                            (current.articles + result.page.articles).distinctBy { it.id }
                        }
                        current.copy(
                            articles = merged,
                            cursor = result.page.cursor,
                            loading = false,
                            refreshing = false,
                            failed = false,
                            status = 1
                        )
                    }
                }
                is NewsLoad.Failure -> _state.update { current ->
                    current.copy(
                        loading = false,
                        refreshing = false,
                        failed = true,
                        status = if (current.articles.isEmpty() && reset) result.status else current.status
                    )
                }
            }
        }
    }

    private fun likeQueue(articleId: String): LatestSender<Boolean> = likeQueues.getOrPut(articleId) {
        LatestSender(viewModelScope) { target ->
            when (val result = NewsFeed.setLike(articleId, target)) {
                is NewsLikeResult.Updated -> {
                    likeSnapshots[articleId] = result.liked to result.likes
                    updateArticle(articleId) { it.copy(liked = result.liked, likes = result.likes) }
                }
                is NewsLikeResult.RateLimited -> {
                    likeBlockedUntil[articleId] = System.currentTimeMillis() + result.retryAfterSeconds * 1000L
                    likeSnapshots[articleId]?.let { snapshot ->
                        updateArticle(articleId) { it.copy(liked = snapshot.first, likes = snapshot.second) }
                    }
                    _state.update { it.copy(message = likeMessage(result)) }
                }
                NewsLikeResult.AuthenticationRequired -> _state.update { it.copy(authRequired = true) }
                else -> {
                    likeSnapshots[articleId]?.let { snapshot ->
                        updateArticle(articleId) { it.copy(liked = snapshot.first, likes = snapshot.second) }
                    }
                    _state.update { it.copy(message = likeMessage(result)) }
                }
            }
        }
    }

    private fun reactionQueue(articleId: String): LatestSender<String> = reactionQueues.getOrPut(articleId) {
        LatestSender(viewModelScope) { target ->
            when (val result = NewsFeed.setReaction(articleId, target.ifEmpty { null })) {
                is NewsReactionResult.Updated -> {
                    reactionSnapshots[articleId] = result.reaction to result.counts
                    updateArticle(articleId) { it.copy(myReaction = result.reaction, reactions = result.counts) }
                }
                is NewsReactionResult.RateLimited -> {
                    reactionBlockedUntil[articleId] = System.currentTimeMillis() + result.retryAfterSeconds * 1000L
                    reactionSnapshots[articleId]?.let { snapshot ->
                        updateArticle(articleId) { it.copy(myReaction = snapshot.first, reactions = snapshot.second) }
                    }
                    _state.update { it.copy(message = reactionMessage(result)) }
                }
                NewsReactionResult.AuthenticationRequired -> _state.update { it.copy(authRequired = true) }
                else -> {
                    reactionSnapshots[articleId]?.let { snapshot ->
                        updateArticle(articleId) { it.copy(myReaction = snapshot.first, reactions = snapshot.second) }
                    }
                    _state.update { it.copy(message = reactionMessage(result)) }
                }
            }
        }
    }

    private fun updateArticle(id: String, transform: (NewsArticle) -> NewsArticle) {
        _state.update { current ->
            current.copy(articles = current.articles.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun likeMessage(result: NewsLikeResult): String = when (result) {
        NewsLikeResult.LimitReached -> tr("Like limit reached")
        is NewsLikeResult.RateLimited -> tr("Too many requests. Try again later.")
        NewsLikeResult.Unavailable -> tr("The news service is not available right now.")
        else -> tr("The news service is not available right now.")
    }

    private fun reactionMessage(result: NewsReactionResult): String = when (result) {
        is NewsReactionResult.RateLimited -> tr("Too many requests. Try again later.")
        else -> tr("The news service is not available right now.")
    }
}
