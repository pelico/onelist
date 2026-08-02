package com.onelist.tv.data

import com.onelist.tv.App
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
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

    private val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(httpLoggingInterceptor)
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
     * Matches tv/index.html imgUrl(): '/t/p/' + path.replace(/^\//, '')
     * TMDB images: original/... or /original/... -> serverUrl + /t/p/ + path
     * Already absolute: http... -> return as-is
     */
    fun imageUrl(path: String?): String? {
        if (path == null || path.isEmpty()) return null
        if (path.startsWith("http")) return path
        // "/" is the default PosterPath for unscraped records - not a valid image
        if (path == "/") return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        // Match tv/index.html: '/t/p/' + path.replace(/^\//, '')
        val stripped = if (path.startsWith("/")) path.substring(1) else path
        return "$normalizedBase/t/p/$stripped"
    }

    /**
     * Build video URL based on source type.
     *
     * Backend stores Alist video paths as "/d/path/to/file.mp4".
     * The Alist proxy route is: GET /alist/proxy/:gallery_uid/*path
     * So the full URL for Alist is: baseUrl + /alist/proxy/{galleryUid}/d/path/to/file.mp4
     *
     * Local file paths start with /file/...
     * Backend route: GET /file/*path
     * So the full URL is: baseUrl + /file/path
     */
    fun videoUrl(url: String?, galleryUid: String?): String? {
        if (url == null || url.isEmpty()) return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base

        return when {
            // Already absolute URL
            url.startsWith("http") -> url
            // Already a full alist proxy path
            url.startsWith("/alist/proxy/") -> "$normalizedBase$url"
            // Alist gallery: /d/... path with galleryUid -> /alist/proxy/{uid}/d/...
            // Note: no extra "/" between galleryUid and url, since url already starts with /
            galleryUid != null && url.startsWith("/d/") -> {
                "$normalizedBase/alist/proxy/$galleryUid$url"
            }
            // Local file: /file/... path
            url.startsWith("/file/") -> "$normalizedBase$url"
            // Other absolute paths (fallback to base)
            url.startsWith("/") -> "$normalizedBase$url"
            // Relative path
            else -> "$normalizedBase/$url"
        }
    }
}
