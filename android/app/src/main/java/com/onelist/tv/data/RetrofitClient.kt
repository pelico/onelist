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

    fun imageUrl(path: String?): String? {
        if (path == null || path.isEmpty()) return null
        if (path.startsWith("http")) return path
        if (path == "/") return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        val stripped = if (path.startsWith("/")) path.substring(1) else path
        return "$normalizedBase/t/p/$stripped"
    }

    fun videoUrl(url: String?, galleryUid: String?): String? {
        if (url == null || url.isEmpty()) return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        if (url.startsWith("http")) return url
        if (url.startsWith("/alist/proxy/")) return "$normalizedBase$url"
        if (galleryUid != null && url.startsWith("/d/")) {
            return "$normalizedBase/alist/proxy/$galleryUid$url"
        }
        if (url.startsWith("/file/")) return "$normalizedBase$url"
        if (url.startsWith("/")) return "$normalizedBase$url"
        return "$normalizedBase/$url"
    }
}
