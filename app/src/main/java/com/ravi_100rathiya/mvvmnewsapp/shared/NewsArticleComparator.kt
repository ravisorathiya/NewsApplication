package com.ravi_100rathiya.mvvmnewsapp.shared

import androidx.recyclerview.widget.DiffUtil
import com.ravi_100rathiya.mvvmnewsapp.data.NewsArticle

class NewsArticleComparator : DiffUtil.ItemCallback<NewsArticle>() {
    override fun areItemsTheSame(oldItem: NewsArticle, newItem: NewsArticle) =
        oldItem.url == newItem.url

    override fun areContentsTheSame(oldItem: NewsArticle, newItem: NewsArticle) =
        oldItem == newItem
}