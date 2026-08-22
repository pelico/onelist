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

        val response = chain.proceed(request)

        // 检测后端返回 code=203（JWT 过期并附带新 token），自动刷新后重试请求
        try {
            val peekBody = response.peekBody(4096)
            val json = org.json.JSONObject(peekBody.string())
            if (json.optInt("code") == 203 && json.has("token")) {
                val newToken = json.getString("token")
                App.token = newToken
                android.util.Log.d("OneList", "Token auto-refreshed by interceptor")
                // 用新 token 重试原始请求，关闭旧响应避免连接泄漏
                val retryRequest = original.newBuilder()
                    .header("Authorization", newToken)
                    .build()
                response.close()
                return@Interceptor chain.proceed(retryRequest)
            }
        } catch (e: Exception) {
            android.util.Log.w("OneList", "Token refresh check failed: ${e.message}")
        }

        response
    }

    private val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.onelist.tv.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    internal val okHttpClient: OkHttpClient by lazy {
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
        // 与 Web 端 getPosterUrl 保持一致：拼接分辨率前缀，后端从 images/w220_and_h330_face/ 目录读取
        return "$normalizedBase/t/p/w220_and_h330_face$path"
    }

    /**
     * 自定义封面 URL：当 poster_path 为空时，请求后端 /custom-image/{seed} 接口，
     * 后端用 FNV-1a 哈希 + Fisher-Yates 洗牌从 picture/ 目录确定性分配一张随机封面。
     * seed 用影片 ID，保证同一影片每次得到同一张图。
     */
    fun customImageUrl(videoId: Int?): String? {
        if (videoId == null) return null
        val base = getBaseUrl()
        if (base.isEmpty()) return null
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base
        return "$normalizedBase/custom-image/$videoId"
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
