package com.catsblock.weather.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.catsblock.weather.data.NewsArticle
import com.catsblock.weather.data.NewsFeed
import com.catsblock.weather.data.NewsLoad
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

    fun refresh() = fetch(reset = true)

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.cursor == null) return
        fetch(reset = false)
    }

    fun loadMoreIfNeeded(lastVisibleIndex: Int) {
        val current = _state.value
        if (current.loading || current.cursor == null || lastVisibleIndex < current.articles.size - 2) return
        fetch(reset = false)
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
                    val merged = if (reset) result.page.articles else (current.articles + result.page.articles).distinctBy { it.id }
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
}
