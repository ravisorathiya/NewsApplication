package com.ravi_100rathiya.mvvmnewsapp.features.worldnews

import androidx.lifecycle.*
import com.ravi_100rathiya.mvvmnewsapp.data.NewsArticle
import com.ravi_100rathiya.mvvmnewsapp.data.NewsRepository
import com.ravi_100rathiya.mvvmnewsapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class WorldNewsViewModel @Inject constructor(
    private val repository: NewsRepository
) : ViewModel() {

    private val refreshTriggerChannel = Channel<Refresh>()
    private val refreshTrigger = refreshTriggerChannel.receiveAsFlow()

    private val eventChannel = Channel<Event>()
    val events = eventChannel.receiveAsFlow()

    var pendingScrollToTopAfterRefresh = false

    val breakingNews = refreshTrigger.flatMapLatest { refresh ->
        repository.getBreakingNews(
            refresh == Refresh.FORCE,
            onFetchSuccess = {
                pendingScrollToTopAfterRefresh = true
            },
            onFetchFailed = { t ->
                viewModelScope.launch { eventChannel.send(Event.ShowErrorMessage(t)) }
            }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    init {
        viewModelScope.launch {
            repository.deleteNonBookmarkedArticlesOlderThan(
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
            )
        }
    }

    fun onStart() {
        if (breakingNews.value !is Resource.Loading) {
            viewModelScope.launch {
                refreshTriggerChannel.send(Refresh.NORMAL)
            }
        }
    }

    fun onManualRefresh() {
        if (breakingNews.value !is Resource.Loading) {
            viewModelScope.launch {
                refreshTriggerChannel.send(Refresh.FORCE)
            }
        }
    }

    fun onBookmarkClick(article: NewsArticle) {
        val currentlyBookmarked = article.isBookmarked
        val updatedArticle = article.copy(isBookmarked = !currentlyBookmarked)
        viewModelScope.launch {
            repository.updateArticle(updatedArticle)
        }
    }

    enum class Refresh {
        FORCE, NORMAL
    }

    sealed class Event {
        data class ShowErrorMessage(val error: Throwable) : Event()
    }
}