package com.ravi_100rathiya.mvvmnewsapp.api

data class NewsArticleDto(
    val webTitle: String,
    val webUrl: String,
    val fields: Fields?
) {
    data class Fields(
        val thumbnail: String
    )
}