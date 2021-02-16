package com.ravi_100rathiya.mvvmnewsapp.features.searchnews

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.recyclerview.widget.LinearLayoutManager
import com.ravi_100rathiya.mvvmnewsapp.MainActivity
import com.ravi_100rathiya.mvvmnewsapp.R
import com.ravi_100rathiya.mvvmnewsapp.databinding.FragmentSearchNewsBinding
import com.ravi_100rathiya.mvvmnewsapp.util.onQueryTextSubmit
import com.ravi_100rathiya.mvvmnewsapp.util.showIfOrInvisible
import com.ravi_100rathiya.mvvmnewsapp.util.showSnackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import timber.log.Timber

@AndroidEntryPoint
class SearchNewsFragment : Fragment(R.layout.fragment_search_news),
    MainActivity.OnBottomNavigationFragmentReselectedListener {

    private val viewModel: SearchNewsViewModel by viewModels()

    private var currentBinding: FragmentSearchNewsBinding? = null
    private val binding get() = currentBinding!!

    private lateinit var newsArticleAdapter: NewsArticlePagingAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentBinding = FragmentSearchNewsBinding.bind(view)

        newsArticleAdapter = NewsArticlePagingAdapter(
            onItemClick = { article ->
                val uri = Uri.parse(article.url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                requireActivity().startActivity(intent)
            },
            onBookmarkClick = { article ->
                viewModel.onBookmarkClick(article)
            }
        )

        binding.apply {
            recyclerView.apply {
                adapter = newsArticleAdapter.withLoadStateFooter(
                    NewsArticleLoadStateAdapter(newsArticleAdapter::retry)
                )
                layoutManager = LinearLayoutManager(requireContext())
                setHasFixedSize(true)
                itemAnimator?.changeDuration = 0 // get rid of bookmark click flash
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                viewModel.searchResults.collectLatest { data ->
                    // Careful - observe & NotLoading don't have a strict order
                    newsArticleAdapter.submitData(data)
                }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                viewModel.hasCurrentQuery.collect { hasCurrentQuery ->
                    textViewInstructions.isVisible = !hasCurrentQuery
                    swipeRefreshLayout.isEnabled = hasCurrentQuery

                    if (!hasCurrentQuery) {
                        Timber.d("no current query -> setting RV gone")
                        recyclerView.isVisible = false
                    }
                }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                newsArticleAdapter.loadStateFlow
                    .distinctUntilChangedBy { it.source.refresh }
                    .filter { it.source.refresh is LoadState.NotLoading }
                    .collect {
                        if (viewModel.pendingScrollToTopAfterNewQuery) {
                            Timber.d("new query scroll")
                            recyclerView.scrollToPosition(0)
                            viewModel.pendingScrollToTopAfterNewQuery = false
                        }
                        if (viewModel.pendingScrollToTopAfterRefresh && it.mediator?.refresh is LoadState.NotLoading) {
                            Timber.d("refresh scroll")
                            recyclerView.scrollToPosition(0)
                            viewModel.pendingScrollToTopAfterRefresh = false
                        }
                    }
            }

            // This is the loadState listener that should work for scrollToPosition(0) but doesn't,
            // because it triggers too early. Leave it here for testing purposes.
            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                newsArticleAdapter.loadStateFlow
                    .distinctUntilChangedBy { it.refresh }
                    .filter { it.refresh is LoadState.NotLoading }
                    .collect {
                        Timber.d("FAIL")
                    }
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                newsArticleAdapter.loadStateFlow
                    .collect { loadState ->
                        Timber.d("loadState = $loadState")
                        // mediator?.refresh avoids that we get the first NotLoading with mediator = null
                        when (val refresh = loadState.mediator?.refresh) {
                            is LoadState.Loading -> {
                                textViewError.isVisible = false
                                buttonRetry.isVisible = false
                                swipeRefreshLayout.isRefreshing = true
                                textViewNoResults.isVisible = false

                                recyclerView.showIfOrInvisible { // not GONE because it suspends DiffUtil animations
                                    !viewModel.newQueryInProgress && newsArticleAdapter.itemCount > 0
                                }

                                viewModel.refreshInProgress = true
                                viewModel.pendingScrollToTopAfterRefresh = true
                            }
                            is LoadState.NotLoading -> {
                                textViewError.isVisible = false
                                buttonRetry.isVisible = false
                                swipeRefreshLayout.isRefreshing = false
                                recyclerView.isVisible = newsArticleAdapter.itemCount > 0

                                val noResults =
                                    newsArticleAdapter.itemCount < 1 && loadState.append.endOfPaginationReached
                                            && loadState.source.append.endOfPaginationReached

                                textViewNoResults.isVisible = noResults

                                viewModel.refreshInProgress = false
                                viewModel.newQueryInProgress = false
                            }
                            is LoadState.Error -> {
                                swipeRefreshLayout.isRefreshing = false
                                textViewNoResults.isVisible = false
                                recyclerView.isVisible = newsArticleAdapter.itemCount > 0

                                val noCachedResults =
                                    newsArticleAdapter.itemCount < 1 && loadState.source.append.endOfPaginationReached

                                textViewError.isVisible = noCachedResults
                                buttonRetry.isVisible = noCachedResults

                                val errorMessage = getString(
                                    R.string.could_not_load_search_results,
                                    refresh.error.localizedMessage
                                        ?: getString(R.string.unknown_error_occurred)
                                )
                                textViewError.text = errorMessage

                                if (viewModel.refreshInProgress) {
                                    showSnackbar(errorMessage)
                                }
                                viewModel.refreshInProgress = false
                                viewModel.newQueryInProgress = false
                                viewModel.pendingScrollToTopAfterRefresh = false
                            }
                        }
                    }
            }

            swipeRefreshLayout.setOnRefreshListener {
                newsArticleAdapter.refresh()
            }

            buttonRetry.setOnClickListener {
                newsArticleAdapter.retry()
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {

            R.id.action_search -> {

                val searchView = item.actionView as SearchView
                searchView.onQueryTextSubmit { query ->
                    viewModel.onSearchQuerySubmit(query)
                    searchView.clearFocus()

                }
                true
            }
            R.id.action_refresh -> {
                // clicking retry on the footer after this failed causes it to retry refresh. I reported
                // this to dlam and he said they will probably provide an argument in the future
                newsArticleAdapter.refresh()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBottomNavigationFragmentReselected() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentBinding = null
    }
}
