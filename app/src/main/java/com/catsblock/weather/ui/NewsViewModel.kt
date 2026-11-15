package com.catsblock.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.NewsLikeResult
import com.catsblock.weather.data.NewsLoad
import com.catsblock.weather.data.NewsReactionResult
import com.catsblock.weather.data.Reaction
import com.catsblock.weather.data.optimisticLike
import com.catsblock.weather.data.optimisticReaction
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
    val available: Boolean = NewsFeed.available()
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
    private val likeTokens = HashMap<String, Int>()
    private val reactionTokens = HashMap<String, Int>()

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
                is NewsLoad.Success -> _state.update { current ->
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

    fun like(article: NewsArticle) {
        val current = _state.value.articles.firstOrNull { it.id == article.id } ?: return
        val target = !current.liked
        updateArticle(current.id) { it.optimisticLike() }
        val token = (likeTokens[current.id] ?: 0) + 1
        likeTokens[current.id] = token
        viewModelScope.launch {
            val result = NewsFeed.like(current.id, unlike = !target)
            if (likeTokens[current.id] != token) return@launch
            when (result) {
                is NewsLikeResult.Updated -> updateArticle(current.id) {
                    it.copy(likes = result.likes, liked = result.liked)
                }
                else -> {
                    updateArticle(current.id) { it.copy(liked = current.liked, likes = current.likes) }
                    _state.update { it.copy(message = likeMessage(result)) }
                }
            }
        }
    }

    fun react(article: NewsArticle, reaction: Reaction) {
        val current = _state.value.articles.firstOrNull { it.id == article.id } ?: return
        val previousReaction = current.myReaction
        val previousCounts = current.reactions
        updateArticle(current.id) { it.optimisticReaction(reaction.emoji) }
        val token = (reactionTokens[current.id] ?: 0) + 1
        reactionTokens[current.id] = token
        viewModelScope.launch {
            val result = NewsFeed.react(current.id, reaction.emoji)
            if (reactionTokens[current.id] != token) return@launch
            when (result) {
                is NewsReactionResult.Updated -> updateArticle(current.id) {
                    it.copy(myReaction = result.reaction, reactions = result.counts)
                }
                else -> {
                    updateArticle(current.id) {
                        it.copy(myReaction = previousReaction, reactions = previousCounts)
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
        NewsLikeResult.RateLimited -> tr("Too many requests. Try again later.")
        NewsLikeResult.Unavailable -> tr("The news service is not available right now.")
        else -> tr("The news service is not available right now.")
    }

    private fun reactionMessage(result: NewsReactionResult): String = when (result) {
        NewsReactionResult.RateLimited -> tr("Too many requests. Try again later.")
        else -> tr("The news service is not available right now.")
    }
}
