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
    // App 启动时间戳（秒级）。给 custom-image URL 当 query 参数，保证：
    //   - 同一次启动内 Glide 的磁盘缓存会命中（SOURCES 缓存）
    //   - 下次启动（或用户手动杀进程重开）后 URL 改变 → 自动用 picture/ 目录里的新内容
    val SESSION_NONCE: Long = System.currentTimeMillis() / 1000L


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

        // 检测 code=203 仅对 JSON 响应做 peekBody + JSON 解析
        // 图片/视频等二进制响应跳过，避免无谓的内存分配和字符串化开销
        val contentType = response.header("Content-Type")
        val isJson = contentType != null && contentType.contains("application/json", ignoreCase = true)
        if (isJson) {
            try {
                val peekBody = response.peekBody(4096)
                val json = org.json.JSONObject(peekBody.string())
                if (json.optInt("code") == 203 && json.has("token")) {
                    val newToken = json.getString("token")
                    App.token = newToken
                    android.util.Log.d("OneList", "Token auto-refreshed by interceptor")
                    val retryRequest = original.newBuilder()
                        .header("Authorization", newToken)
                        .build()
                    response.close()
                    return@Interceptor chain.proceed(retryRequest)
                }
            } catch (e: Exception) {
                android.util.Log.w("OneList", "Token refresh check failed: ${e.message}")
            }
        }

        // 检测 401 状态码：token 失效，清除本地 token 并发广播跳转登录页
        if (response.code == 401) {
            android.util.Log.w("OneList", "Token invalid (HTTP 401), clearing local token")
            App.token = null
            val intent = android.content.Intent("ACTION_TOKEN_INVALID")
            App.context.sendBroadcast(intent)
        }
        response
    }

    private val httpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.onelist.tv.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
    }

    /** API 调用专用 client：带 token 刷新/401 广播/日志拦截器 */
    internal val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(httpLoggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 图片加载专用 client（Glide / 自定义封面 / 壁纸 / TMDB 海报）
     * - 带 Authorization（/custom-image 等后端私有端点需要 token）
     * - 不带 token-refresh JSON 解析、不带 BODY 级别日志，节省每一次图片响应的开销
     * - 超时更短，失败快速回退到 error drawable
     */
    internal val imageOkHttpClient: OkHttpClient by lazy {
        val tokenHeaderInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = App.token
            val req = if (!token.isNullOrEmpty()) {
                original.newBuilder().header("Authorization", token).build()
            } else original
            chain.proceed(req)
        }
        OkHttpClient.Builder()
            .addInterceptor(tokenHeaderInterceptor)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
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
        return "$normalizedBase/custom-image/$videoId?t=$SESSION_NONCE"
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
