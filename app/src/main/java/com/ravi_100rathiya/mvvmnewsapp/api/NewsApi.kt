package com.ravi_100rathiya.mvvmnewsapp.api

import com.ravi_100rathiya.mvvmnewsapp.BuildConfig
import retrofit2.http.GET
import retrofit2.http.Query

interface NewsApi {

    // Im Kurs nehme ich stattdessen newsapi.org da die Guardian API einige Probleme hat (Reihenfolge
    // ändert sich oft, error response für empty page etc.)
    companion object {
        const val BASE_URL = "https://content.guardianapis.com/"
        const val API_KEY = BuildConfig.GUARDIAN_API_KEY
    }

    @GET("search?api-key=$API_KEY&section=world&page-size=100&show-fields=thumbnail")
    suspend fun getWorldNews(): NewsResponse

    @GET("search?api-key=$API_KEY&show-fields=thumbnail")
    suspend fun searchNews(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("page-size") pageSize: Int,
    ): NewsResponse
}