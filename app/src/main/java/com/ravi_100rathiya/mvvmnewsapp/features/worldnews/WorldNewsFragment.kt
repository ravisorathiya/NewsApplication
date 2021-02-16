package com.ravi_100rathiya.mvvmnewsapp.features.worldnews

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ravi_100rathiya.mvvmnewsapp.MainActivity
import com.ravi_100rathiya.mvvmnewsapp.R
import com.ravi_100rathiya.mvvmnewsapp.databinding.FragmentSearchNewsBinding
import com.ravi_100rathiya.mvvmnewsapp.shared.NewsArticleListAdapter
import com.ravi_100rathiya.mvvmnewsapp.databinding.FragmentWorldNewsBinding
import com.ravi_100rathiya.mvvmnewsapp.util.Resource
import com.ravi_100rathiya.mvvmnewsapp.util.exhaustive
import com.ravi_100rathiya.mvvmnewsapp.util.showSnackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect

@AndroidEntryPoint
class WorldNewsFragment : Fragment(R.layout.fragment_world_news),
    MainActivity.OnBottomNavigationFragmentReselectedListener {

    private val viewModel: WorldNewsViewModel by viewModels()

    private var currentBinding: FragmentWorldNewsBinding? = null
    private val binding get() = currentBinding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        currentBinding = FragmentWorldNewsBinding.bind(view)

        val newsArticleAdapter = NewsArticleListAdapter(
            onItemClick = { article ->
                val uri = Uri.parse(article.url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                requireActivity().startActivity(intent)
            },
            onBookmarkClick = { article ->
                viewModel.onBookmarkClick(article)
            }
        )

        newsArticleAdapter.stateRestorationPolicy =
            RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY

        binding.apply {
            recyclerView.apply {
                adapter = newsArticleAdapter
                layoutManager = LinearLayoutManager(requireContext())
                setHasFixedSize(true)
                itemAnimator?.changeDuration = 0 // get rid of bookmark click flash
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                viewModel.breakingNews.collect {
                    val result = it ?: return@collect

                    swipeRefreshLayout.isRefreshing = result is Resource.Loading
                    recyclerView.isVisible = !result.data.isNullOrEmpty()
                    textViewError.isVisible = result.error != null && result.data.isNullOrEmpty()
                    buttonRetry.isVisible = result.error != null && result.data.isNullOrEmpty()
                    textViewError.text = resources.getString(
                        R.string.could_not_refresh,
                        result.error?.localizedMessage
                            ?: getString(R.string.unknown_error_occurred)
                    )

                    newsArticleAdapter.submitList(result.data) {
                        if (viewModel.pendingScrollToTopAfterRefresh) {
                            recyclerView.scrollToPosition(0)
                            viewModel.pendingScrollToTopAfterRefresh = false
                        }
                    }
                }
            }

            swipeRefreshLayout.setOnRefreshListener {
                viewModel.onManualRefresh()
            }

            buttonRetry.setOnClickListener {
                viewModel.onManualRefresh()
            }

            viewLifecycleOwner.lifecycleScope.launchWhenStarted {
                viewModel.events.collect { event ->
                    when (event) {
                        is WorldNewsViewModel.Event.ShowErrorMessage ->
                            showSnackbar(
                                resources.getString(
                                    R.string.could_not_refresh,
                                    event.error.localizedMessage
                                        ?: getString(R.string.unknown_error_occurred)
                                )
                            )
                    }.exhaustive
                }
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onStart()
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.action_refresh -> {
                viewModel.onManualRefresh()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun onBottomNavigationFragmentReselected() {
        binding.recyclerView.scrollToPosition(0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentBinding = null
    }
}