package com.onelist.tv.data

import com.onelist.tv.App
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private var currentBaseUrl: String = ""
    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val token = App.token
        val request = if (token != null && token.isNotEmpty()) {
            original.newBuilder()
                .header("Authorization", token)
                .build()
        } else {
            original
        }
        chain.proceed(request)
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getBaseUrl(): String {
        return App.serverUrl ?: ""
    }

    fun getService(): ApiService {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) {
            throw IllegalStateException("Server URL not configured")
        }
        if (apiService != null && baseUrl == currentBaseUrl) {
            return apiService!!
        }
        currentBaseUrl = baseUrl
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        retrofit = Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit!!.create(ApiService::class.java)
        return apiService!!
    }

    /**
     * Build a full image URL from a relative path.
     * TMDB images: original/... or /original/... -> serverUrl + /t/p/ + path
     * Gallery images: /gallery/... -> serverUrl + path
     * Already absolute: http... -> return as-is
     */
    fun imageUrl(path: String?): String? {
        if (path == null || path.isEmpty()) return null
        if (path.startsWith("http")) return path
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        // TMDB poster/backdrop paths need /t/p/ prefix
        val imagePath = if (path.startsWith("/t/p/")) {
            path
        } else if (path.startsWith("/")) {
            "/t/p$path"
        } else {
            "/t/p/$path"
        }
        return "$normalizedBase$imagePath"
    }

    /**
     * Build video URL based on source type.
     * Local file: path starts with / -> serverUrl + /file/ + path
     * Alist: path contains gallery_uid -> serverUrl + /alist/proxy/ + galleryUid + / + path
     */
    fun videoUrl(url: String?, galleryUid: String?): String? {
        if (url == null || url.isEmpty()) return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base

        return when {
            // Alist proxy
            galleryUid != null && url.contains(galleryUid) -> {
                "$normalizedBase/alist/proxy/$galleryUid$url"
            }
            // Local file
            url.startsWith("/") -> {
                "$normalizedBase/file$url"
            }
            // Already absolute
            url.startsWith("http") -> url
            // Relative path
            else -> "$normalizedBase/$url"
        }
    }
}
