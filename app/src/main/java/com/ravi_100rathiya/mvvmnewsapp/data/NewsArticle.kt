package com.ravi_100rathiya.mvvmnewsapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticle(
    val title: String,
    @PrimaryKey val url: String,
    val thumbnailUrl: String?,
    val isBookmarked: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "breaking_news")
data class BreakingNews(
    val articleUrl: String,
    @PrimaryKey(autoGenerate = true) val id: Int = 0 // we need this for the order, otherwise an article can move around if it also appears in a search query
)

// url as a primary key gets rid of duplicates
@Entity(tableName = "search_results", primaryKeys = ["searchQuery", "articleUrl"])
// Foreign key on articleUrl deletes articles out of previous search queries on REPLACE
data class SearchResult(
    val searchQuery: String,
    val articleUrl: String,
    val queryPosition: Int
)