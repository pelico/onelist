package com.onelist.tv

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.onelist.tv.data.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.onelist.tv.App
import com.onelist.tv.CardAdapter
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {

    // Current screen state
    private enum class Screen { LOGIN, HOME, LIST, DETAIL, SEARCH, PLAYER }
    private var currentScreen = Screen.LOGIN

    // Root container
    private lateinit var rootLayout: FrameLayout

    // Reusable views
    private var loginView: View? = null
    private var homeView: View? = null
    private var listView: View? = null
    private var detailView: View? = null
    private var searchView: View? = null
    private var playerView: View? = null
    private var player: ExoPlayer? = null

    // 播放列表：用于遥控器上下键切换集/电影
    private data class PlayItem(val url: String, val galleryUid: String?, val title: String? = null)
    private var currentPlaylist: List<PlayItem>? = null
    private var currentPlayIndex: Int = 0
    private var currentMovieList: List<Movie>? = null // 当前电影列表，用于构建播放列表

    // 服务端连播列表：播放结束后自动播放同目录下一个视频
    private var serverPlaylist: List<String> = emptyList()
    private var serverPlaylistIndex: Int = 0

    // SSE message center
    private var sseClient: OkHttpClient? = null
    private var sseEventSource: EventSource? = null
    private val gson = Gson()
    private var isDestroying = false

    // Play statistics heartbeat
    private var heartbeatExecutor: ScheduledExecutorService? = null
    private var lastHeartbeatPosition: Long = 0
    
    // Current video metadata for heartbeat
    private var currentVideoDataType: String? = null
    private var currentVideoDataId: Int? = null
    private var currentVideoTitle: String? = null
    private var currentVideoGalleryUid: String? = null
    private var currentVideoGalleryTitle: String? = null

    // ==================== 返回栈管理 ====================
    /** 屏幕状态：用于按遥控器返回键时恢复到上一步的精确位置 */
    private sealed class ScreenState {
        object Home : ScreenState()
        object Search : ScreenState()
        data class ListScreen(
            val galleryId: String?,
            val galleryTitle: String?,
            val galleryType: String?
        ) : ScreenState()
        data class MovieDetailScreen(
            val galleryId: String?,
            val galleryTitle: String?,
            val galleryType: String?,
            val fromSearch: Boolean,
            val movie: Movie
        ) : ScreenState()
        data class TvDetailScreen(
            val galleryId: String?,
            val galleryTitle: String?,
            val galleryType: String?,
            val fromSearch: Boolean,
            val tv: Tv
        ) : ScreenState()
        data class EpisodeListScreen(
            val galleryId: String?,
            val galleryTitle: String?,
            val galleryType: String?,
            val fromSearch: Boolean,
            val parentTv: Tv,
            val episodes: List<Episode>,
            val seasonTitle: String
        ) : ScreenState()
        data class PlayerScreen(
            val prev: ScreenState, // 播放器前的屏幕，直接存作为pop后的状态
            val url: String,
            val galleryUid: String?
        ) : ScreenState()
    }

    /** 返回栈：按遥控器返回键时 pop 栈顶并恢复 */
    private val screenBackStack = ArrayDeque<ScreenState>()

    // State
    private var currentGalleryId: String? = null
    private var currentGalleryTitle: String? = null
    private var currentGalleryType: String? = null // "movie" or "tv"
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true
    private val listItems = mutableListOf<Any>() // Movie or Tv objects
    private var listAdapter: CardAdapter? = null
    // 用于按返回键恢复详情/剧集列表时不重新请求 API
    private var currentMovie: Movie? = null
    private var currentTv: Tv? = null
    private var currentSeasonEpisodes: List<Episode>? = null
    private var currentSeasonTitle: String? = null
    // 标记详情是否从搜索页进入（返回时要回搜索而非列表）
    private var detailFromSearch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.parseColor("#0d0d1a"))
        // 在根布局上设置 OnKeyListener，确保 BACK/ESC 键在任何焦点状态下都能被拦截
        rootLayout.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && 
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)) {
                navigateBack()
                return@setOnKeyListener true
            }
            false
        }
        rootLayout.isFocusable = true
        rootLayout.isFocusableInTouchMode = true
        setContentView(rootLayout)

        // Init SSE message center
        initSSE()

        if (App.isLoggedIn()) {
            showHome()
        } else {
            showLogin()
        }
    }
    // ==================== SSE 消息中心初始化 ====================
    
    private fun initSSE() {
        if (isDestroying) return
        val token = App.token
        if (token.isNullOrEmpty()) return
        
        sseClient = OkHttpClient.Builder().build()
        
        val baseUrl = RetrofitClient.getBaseUrl()
        val url = "${baseUrl}v1/api/message/sse?token=${java.net.URLEncoder.encode(token, "UTF-8")}"
        
        val request = Request.Builder().url(url).build()
        
        val factory = EventSources.createFactory(sseClient!!)
        sseEventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                android.util.Log.d("OneList", "SSE connected")
            }
            
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                android.util.Log.d("OneList", "SSE message: type=$type data=$data")
                
                when (type) {
                    "init" -> {
                        // 初始未读消息
                        try {
                            val messages = gson.fromJson(data, Array<Message>::class.java).toList()
                            for (msg in messages) {
                                showMessage(msg)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("OneList", "Failed to parse init messages", e)
                        }
                    }
                    "message" -> {
                        // 新消息
                        try {
                            val msg = gson.fromJson(data, Message::class.java)
                            showMessage(msg)
                        } catch (e: Exception) {
                            android.util.Log.e("OneList", "Failed to parse message", e)
                        }
                    }
                }
            }
            
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                android.util.Log.e("OneList", "SSE failure: ${t?.message}")
                // 不在这里做延迟重连：onStop() cancel 会触发 onFailure，
                // 此时 Activity 可能只是短暂不可见，重连由 onStart() 负责。
                // OkHttp EventSource 自身也有内部重连机制。
            }
            
            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("OneList", "SSE closed")
            }
        })
    }
    
    private fun showMessage(msg: Message) {
        runOnUiThread {
            if (isDestroying) return@runOnUiThread
            if (msg.priority == "forced") {
                // 强制通知：全屏弹窗，必须确认
                showForcedMessageDialog(msg)
            } else {
                // 普通通知通过Toast
                toast("📩 ${msg.content}")
            }
            
            // 自动标记为已读
            markMessageAsRead(msg.id)
        }
    }
    
    private fun showForcedMessageDialog(msg: Message) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        
        val layout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#000000"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(40), dp(40), dp(40))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        
        val icon = TextView(this).apply {
            text = "📩"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(72f))
            gravity = Gravity.CENTER
        }
        contentLayout.addView(icon)
        
        val title = TextView(this).apply {
            text = "重要通知"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(28f))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, tvDp(20), 0, tvDp(10))
        }
        contentLayout.addView(title)
        
        val content = TextView(this).apply {
            text = msg.content
            setTextColor(Color.LTGRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
            gravity = Gravity.CENTER
            setPadding(tvDp(20), 0, tvDp(20), 0)
        }
        contentLayout.addView(content)
        
        val confirmBtn = Button(this).apply {
            text = "我知道了"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
            setPadding(tvDp(40), tvDp(12), tvDp(40), tvDp(12))
            isFocusable = true
            isClickable = true
            applyFocusGlow()
            setOnClickListener {
                dialog.dismiss()
            }
        }
        contentLayout.addView(confirmBtn)
        
        layout.addView(contentLayout)
        dialog.setContentView(layout)
        dialog.show()
    }
    
    private fun markMessageAsRead(messageId: Int) {
        try {
            RetrofitClient.getService().readMessage(messageId).enqueue(object : Callback<ApiResponse<Void>> {
                override fun onResponse(call: Call<ApiResponse<Void>>, response: Response<ApiResponse<Void>>) {
                    // 忽略成功
                }
                override fun onFailure(call: Call<ApiResponse<Void>>, t: Throwable) {
                    android.util.Log.e("OneList", "Failed to mark message as read", t)
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("OneList", "Exception marking message as read", e)
        }
    }

    // ==================== 播放统计心跳 ====================

    /**
     * 开始播放时启动，每30秒上报一次播放进度
     */
    private fun startHeartbeat(player: ExoPlayer) {
        // 如果没有视频元数据，不上报
        if (currentVideoDataType == null || currentVideoDataId == null || currentVideoTitle == null) {
            android.util.Log.w("OneList", "Heartbeat skipped: metadata missing — type=$currentVideoDataType id=$currentVideoDataId title=$currentVideoTitle")
            return
        }

        stopHeartbeat() // 先停止之前的

        android.util.Log.d("OneList", "Heartbeat starting: type=${currentVideoDataType} id=${currentVideoDataId} title='${currentVideoTitle}' gallery='${currentVideoGalleryUid}' galleryTitle='${currentVideoGalleryTitle}'")

        lastHeartbeatPosition = player.currentPosition
        
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor()
        heartbeatExecutor?.scheduleAtFixedRate({
            // ExoPlayer 强制要求在主线程访问其属性，否则抛 IllegalStateException
            runOnUiThread {
                try {
                    val currentPosition = player.currentPosition
                    val durationSeconds = ((currentPosition - lastHeartbeatPosition) / 1000).coerceAtLeast(0).toInt()
                    val positionSeconds = (currentPosition / 1000).toInt()
                    val totalDurationSeconds = (player.duration / 1000).toInt()

                    android.util.Log.d("OneList", "Heartbeat tick: pos=${positionSeconds}s dur=${durationSeconds}s total=${totalDurationSeconds}s")

                    if (durationSeconds > 0) {
                        val request = HeartbeatRequest(
                            dataType = currentVideoDataType!!,
                            dataId = currentVideoDataId!!,
                            title = currentVideoTitle!!,
                            galleryUid = currentVideoGalleryUid ?: "",
                            galleryTitle = currentVideoGalleryTitle ?: "",
                            duration = durationSeconds,
                            position = positionSeconds,
                            totalDuration = totalDurationSeconds
                        )

                        RetrofitClient.getService().sendHeartbeat(request).enqueue(object : Callback<ApiResponse<PlayHistory>> {
                            override fun onResponse(call: Call<ApiResponse<PlayHistory>>, response: Response<ApiResponse<PlayHistory>>) {
                                val body = response.body()
                                if (body != null && body.code == 200) {
                                    android.util.Log.d("OneList", "Heartbeat OK: pos=$positionSeconds dur=$durationSeconds")
                                } else {
                                    android.util.Log.w("OneList", "Heartbeat rejected: HTTP ${response.code()} code=${body?.code} msg=${body?.msg}")
                                }
                            }
                            override fun onFailure(call: Call<ApiResponse<PlayHistory>>, t: Throwable) {
                                android.util.Log.e("OneList", "Heartbeat network failed: ${t.message}", t)
                            }
                        })
                    }

                    lastHeartbeatPosition = currentPosition
                } catch (e: Exception) {
                    android.util.Log.e("OneList", "Heartbeat error: ${e.message}", e)
                }
            }
        }, 5, 30, TimeUnit.SECONDS)

        android.util.Log.d("OneList", "Heartbeat scheduled: first in 5s, then every 30s")
    }

    /**
     * 停止播放时停止心跳
     */
    private fun stopHeartbeat() {
        heartbeatExecutor?.shutdownNow()
        heartbeatExecutor = null
        android.util.Log.d("OneList", "Heartbeat stopped")
    }


    // ==================== SCREEN MANAGEMENT ====================

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        rootLayout.removeAllViews()
        stopHeartbeat()
        player?.release()
        player = null

        when (screen) {
            Screen.LOGIN -> showLogin()
            Screen.HOME -> showHome()
            Screen.LIST -> showList()
            Screen.DETAIL -> {} // showDetail is called with params
            Screen.SEARCH -> showSearch()
            Screen.PLAYER -> {} // showPlayer is called with params
        }
    }

    // ==================== LOGIN SCREEN ====================

    private fun showLogin() {
        currentScreen = Screen.LOGIN
        screenBackStack.clear()
        player?.release(); player = null
        rootLayout.removeAllViews()

        val ctx = this
        val scroll = ScrollView(this)
        scroll.fillParent()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(40), dp(80), dp(40), dp(40))
        }

        // Title
        val title = TextView(this).apply {
            text = "Onelist TV"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(title, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(40)
        })

        // Server URL input
        val serverLabel = label("服务器地址")
        layout.addView(serverLabel)
        val serverInput = EditText(this).apply {
            hint = "http://192.168.1.100:8080"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            val saved = App.serverUrl
            if (saved != null) setText(saved)
        }
        layout.addView(serverInput, lp().apply { bottomMargin = dp(20) })

        // Username input
        val userLabel = label("用户名")
        layout.addView(userLabel)
        val userInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            val saved = App.username
            if (saved != null) setText(saved)
        }
        layout.addView(userInput, lp().apply { bottomMargin = dp(20) })

        // Password input
        val passLabel = label("密码")
        layout.addView(passLabel)
        val passInput = EditText(this).apply {
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        layout.addView(passInput, lp().apply { bottomMargin = dp(30) })

        // Login button
        val loginBtn = Button(this).apply {
            text = "登 录"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(40), dp(14), dp(40), dp(14))
            isFocusable = true
            isClickable = true
            applyFocusGlow()
            setOnClickListener {
                val url = serverInput.text.toString().trim().trimEnd('/')
                val user = userInput.text.toString().trim()
                val pass = passInput.text.toString().trim()
                if (url.isEmpty() || user.isEmpty() || pass.isEmpty()) {
                    toast("请填写所有字段")
                    return@setOnClickListener
                }
                App.serverUrl = url
                App.username = user
                doLogin(user, pass, this)
            }
        }
        layout.addView(loginBtn, lp().apply {
            width = dp(240)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // Status text
        val statusText = TextView(this).apply {
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            visibility = View.GONE
        }
        layout.addView(statusText, lp().apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
        })

        scroll.addView(layout)
        rootLayout.addView(scroll)
    }

    private fun doLogin(username: String, password: String, btn: Button) {
        btn.isEnabled = false
        btn.text = "登录中..."
        try {
            val call = RetrofitClient.getService().login(LoginRequest(username, password))
            call.enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    val body = response.body()
                    android.util.Log.d("OneList", "Login response code: ${response.code()}")
                    android.util.Log.d("OneList", "Login body: code=${body?.code}, msg=${body?.msg}")
                    android.util.Log.d("OneList", "Login token: ${body?.token?.take(30)}...")
                    
                    if (body != null && body.code == 200 && body.token != null) {
                        App.token = body.token
                        App.userId = body.user?.id
                        App.username = username
                        android.util.Log.d("OneList", "Saved token: ${App.token?.take(30)}...")
                        toast("登录成功")
                        showHome()
                    } else {
                        btn.isEnabled = true
                        btn.text = "登 录"
                        toast(body?.msg ?: "登录失败")
                    }
                }
                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    btn.isEnabled = true
                    btn.text = "登 录"
                    toast("连接失败: ${t.message}")
                }
            })
        } catch (e: Exception) {
            btn.isEnabled = true
            btn.text = "登 录"
            toast("错误: ${e.message}")
        }
    }

    // ==================== HOME SCREEN ====================

    private fun showHome() {
        currentScreen = Screen.HOME
        // 回到首页清空返回栈，避免返回时乱跳
        screenBackStack.clear()
        // 离开播放器界面时释放播放器，避免音频继续在后台播放
        stopHeartbeat()
        player?.release()
        player = null
        rootLayout.removeAllViews()

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, tvDp(20), 0, tvDp(40))
        }

        // Top bar
        val topBar = buildTopBar("悠悠TV", showSearch = true, showLogout = true)
        layout.addView(topBar)

        // Loading container with ProgressBar
        val loadingContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        }
        
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        loadingContainer.addView(progressBar)
        
        val loadingText = TextView(this).apply {
            text = "加载中..."
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                gravity = Gravity.CENTER
                topMargin = tvDp(60)
            }
        }
        loadingContainer.addView(loadingText)
        
        layout.addView(loadingContainer)

        scroll.addView(layout)
        rootLayout.addView(scroll)

        // Fetch home data
        try {
            val service = RetrofitClient.getService()
            val token = App.token
            android.util.Log.d("OneList", "API call to: ${RetrofitClient.getBaseUrl()}v1/api/home")
            android.util.Log.d("OneList", "Token: ${token?.take(20)}...")
            
            service.getHome().enqueue(object : Callback<ApiResponse<HomeData>> {
                override fun onResponse(call: Call<ApiResponse<HomeData>>, response: Response<ApiResponse<HomeData>>) {
                    android.util.Log.d("OneList", "HTTP code: ${response.code()}")
                    android.util.Log.d("OneList", "Response raw: ${response.errorBody()?.string() ?: "no error body"}")
                    
                    val body = response.body()
                    if (body != null) {
                        android.util.Log.d("OneList", "Body code: ${body.code}")
                        android.util.Log.d("OneList", "Body msg: ${body.msg}")
                        android.util.Log.d("OneList", "Body data null: ${body.data == null}")
                    } else {
                        // Body is null - could be deserialization failure
                        val rawError = try { response.errorBody()?.string() } catch (e: Exception) { null }
                        android.util.Log.e("OneList", "Body is null! HTTP ${response.code()}, errorBody: $rawError")
                    }
                    
                    if (body != null && body.code == 200 && body.data != null) {
                        // 隐藏加载指示器
                        loadingContainer.visibility = View.GONE
                        val data = body.data!!
                        android.util.Log.d("OneList", "Home data: latestMovies=${data.latestMovies?.size ?: 0} latestTvs=${data.latestTvs?.size ?: 0} galleries=${data.galleries?.size ?: 0}")
                        if (data.latestMovies != null && data.latestMovies.isNotEmpty()) {
                            val m = data.latestMovies!![0]
                            android.util.Log.d("OneList", "First latestMovie: title='${m.title}' origTitle='${m.originalTitle}' posterPath='${m.posterPath}' backdropPath='${m.backdropPath}' id=${m.id}")
                        }
                        if (data.latestTvs != null && data.latestTvs.isNotEmpty()) {
                            val t = data.latestTvs!![0]
                            android.util.Log.d("OneList", "First latestTv: name='${t.name}' origName='${t.originalName}' posterPath='${t.posterPath}' backdropPath='${t.backdropPath}' id=${t.id}")
                        }
                        renderHomeData(layout, data)
                    } else {
                        val currentToken = App.token
                        val errorMsg = buildString {
                            append("加载失败\n")
                            append("HTTP: ${response.code()}\n")
                            append("Token: ${if (currentToken == null) "null" else if (currentToken.isEmpty()) "empty" else currentToken.take(20) + "..."}\n")
                            if (body != null) {
                                append("Code: ${body.code}\n")
                                append("Msg: ${body.msg ?: "null"}\n")
                                append("Data: ${if (body.data == null) "null" else "not null"}")
                            } else {
                                append("Body: null")
                            }
                        }
                        android.util.Log.e("OneList", errorMsg)
                        loadingText.text = errorMsg
                    }
                }
                override fun onFailure(call: Call<ApiResponse<HomeData>>, t: Throwable) {
                    android.util.Log.e("OneList", "API failure: ${t.message}", t)
                    loadingText.text = "连接失败: ${t.message}"
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("OneList", "Exception: ${e.message}", e)
            loadingText.text = "错误: ${e.message}"
        }
    }

    private fun renderHomeData(parent: LinearLayout, data: HomeData) {
        val ctx = this

        // Debug: log overall data structure
        android.util.Log.d("OneList", "renderHomeData: galleries=${data.galleries?.size ?: 0} latestMovies=${data.latestMovies?.size ?: 0} latestTvs=${data.latestTvs?.size ?: 0} galleryItems keys=${data.galleryItems?.keys?.joinToString(",") ?: "null"}")
        if (data.galleries != null) {
            for (g in data.galleries) {
                val itemCount = data.galleryItems?.get(g.galleryUid)?.let { if (it.isJsonArray) it.asJsonArray.size() else "not-array" } ?: "missing"
                android.util.Log.d("OneList", "  Gallery: title='${g.title}' uid='${g.galleryUid}' type='${g.galleryType}' isTv=${g.isTv} isAlist=${g.isAlist} items=$itemCount")
            }
        }

        // Latest movies row
        if (data.latestMovies != null && data.latestMovies.isNotEmpty()) {
            val row = buildContentRow("最新电影", "movie", null)
            parent.addView(row)
            val recyclerView = buildHorizontalCardList(data.latestMovies.map { it as Any }, "movie")
            parent.addView(recyclerView)
        }

        // Latest TVs row
        if (data.latestTvs != null && data.latestTvs.isNotEmpty()) {
            val row = buildContentRow("最新电视", "tv", null)
            parent.addView(row)
            val recyclerView = buildHorizontalCardList(data.latestTvs.map { it as Any }, "tv")
            parent.addView(recyclerView)
        }

        // Gallery rows - manually parse JsonElement to avoid Gson nested generic type erasure
        if (data.galleries != null) {
            val gson = Gson()
            for (gallery in data.galleries) {
                val jsonElement = data.galleryItems?.get(gallery.galleryUid)
                val items: List<GalleryItem> = if (jsonElement != null && jsonElement.isJsonArray) {
                    jsonElement.asJsonArray.map { gson.fromJson(it, GalleryItem::class.java) }
                } else emptyList()

                // Determine gallery type for list view: prefer gallery_type, fallback to is_tv
                val galleryType = when {
                    gallery.galleryType == "tv" -> "tv"
                    gallery.galleryType == "movie" -> "movie"
                    gallery.isTv == true -> "tv"
                    else -> "movie"
                }
                android.util.Log.d("OneList", "Gallery '${gallery.title}' uid=${gallery.galleryUid} items=${items.size} type=$galleryType")
                if (items.isNotEmpty()) {
                    // Log first item to debug field mapping
                    val first = items[0]
                    android.util.Log.d("OneList", "  First item: title='${first.title}' name='${first.name}' poster='${first.posterPath}'")

                    val row = buildContentRow(gallery.title ?: "媒体库", galleryType, gallery.galleryUid)
                    parent.addView(row)
                    // Per-item type detection: has "title" field -> Movie, has "name" field -> Tv
                    // This matches tv/index.html: var type = forceType || (item.title ? 'movie' : 'tv')
                    val mappedItems = items.map { item ->
                        if (item.title != null) {
                            Movie(id = item.id, title = item.title, posterPath = item.posterPath)
                        } else {
                            Tv(id = item.id, name = item.name, posterPath = item.posterPath)
                        } as Any
                    }
                    val recyclerView = buildHorizontalCardList(mappedItems, "mixed")
                    parent.addView(recyclerView)
                }
            }
        }
    }

    private fun buildContentRow(title: String, type: String, galleryId: String?): LinearLayout {
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tvDp(24), tvDp(16), tvDp(24), tvDp(8))
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(20f))
            setTypeface(null, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setPadding(tvDp(8), tvDp(4), tvDp(8), tvDp(4))
            setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    (v as TextView).setTextColor(Color.parseColor("#6366f1"))
                } else {
                    (v as TextView).setTextColor(Color.WHITE)
                }
            }
            setOnClickListener {
                currentGalleryId = galleryId
                currentGalleryTitle = title
                currentGalleryType = type
                showList()
            }
        }
        row.addView(titleView)

        // "查看更多" hint
        if (galleryId != null) {
            val more = TextView(this).apply {
                text = "查看更多 >"
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(14f))
                setPadding(tvDp(12), tvDp(4), tvDp(4), tvDp(4))
            }
            row.addView(more)
        }

        return row
    }

    private fun buildHorizontalCardList(items: List<Any>, type: String): FrameLayout {
        val adapter = CardAdapter(items, type) { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        }
        
        // 创建带边缘渐变提示的容器
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tvDp(280)
            )
        }
        
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
            setPadding(tvDp(16), 0, tvDp(16), 0)
            clipToPadding = false
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(recyclerView)
        
        // 左侧渐变遮罩（指示可以向左滚动）
        val leftGradient = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(tvDp(40), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START
            }
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.RIGHT_LEFT
                colors = intArrayOf(
                    Color.parseColor("#0d0d1a"),
                    Color.TRANSPARENT
                )
            }
        }
        container.addView(leftGradient)
        
        // 右侧渐变遮罩（指示可以向右滚动）
        val rightGradient = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(tvDp(40), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
            }
            background = GradientDrawable().apply {
                orientation = GradientDrawable.Orientation.LEFT_RIGHT
                colors = intArrayOf(
                    Color.parseColor("#0d0d1a"),
                    Color.TRANSPARENT
                )
            }
        }
        container.addView(rightGradient)
        
        return container
    }

    // ==================== LIST SCREEN ====================

    /**
     * @param restoreFromCache true=按返回键恢复，复用已加载数据不重新请求API；false=首次进入，加载第一页
     */
    private fun showList(restoreFromCache: Boolean = false) {
        // 只有正向导航（非按返回键恢复）才推入返回栈
        if (!restoreFromCache) pushCurrentToBackStack()
        currentScreen = Screen.LIST
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        detailFromSearch = false

        if (!restoreFromCache) {
            // 首次进入：重置列表状态
            currentPage = 1
            isLoadingMore = false
            hasMorePages = true
            listItems.clear()
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        val topBar = buildTopBar(currentGalleryTitle ?: "浏览", showSearch = true, showLogout = false)
        layout.addView(topBar)

        // Loading indicator with ProgressBar
        val loadingContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }
        
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            id = android.R.id.progress
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            visibility = View.VISIBLE
        }
        loadingContainer.addView(progressBar)
        
        val loadingText = TextView(this).apply {
            id = android.R.id.text1
            text = "加载中..."
            setTextColor(Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                gravity = Gravity.CENTER
                topMargin = tvDp(60)
            }
        }
        loadingContainer.addView(loadingText)
        
        listAdapter = CardAdapter(listItems, currentGalleryType ?: "movie") { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        }
        val gridColumns = calculateGridColumns()
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
            this.adapter = listAdapter
            setPadding(tvDp(16), tvDp(8), tvDp(16), tvDp(16))
            clipToPadding = false
            visibility = View.GONE // 初始隐藏，加载完成后显示
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        loadingContainer.addView(recyclerView)
        
        layout.addView(loadingContainer, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply {
            weight = 1f
        })

        val loadMoreBtn = Button(this).apply {
            text = "加载更多"
            setTextColor(Color.WHITE)
            visibility = View.GONE
            isFocusable = true
            isClickable = true
            applyFocusGlow(Color.parseColor("#1a1a2e"), Color.parseColor("#6366f1"), Color.WHITE)
            setOnClickListener {
                if (!isLoadingMore && hasMorePages) {
                    currentPage++
                    loadMoreItems(recyclerView, this as Button)
                }
            }
        }
        val loadMoreLayout = FrameLayout(this).apply {
            addView(loadMoreBtn, FrameLayout.LayoutParams(dp(200), dp(48)).apply {
                gravity = Gravity.CENTER
            })
            setPadding(0, dp(8), 0, dp(16))
        }
        layout.addView(loadMoreLayout)

        rootLayout.addView(layout)

        if (!restoreFromCache) {
            loadListData(recyclerView, loadMoreBtn)
        } else {
            // 恢复时保持和之前一致的加载更多按钮状态
            loadMoreBtn.visibility = if (hasMorePages) View.VISIBLE else View.GONE
            listAdapter?.notifyDataSetChanged()
            // 显示列表，隐藏加载指示器
            recyclerView.visibility = View.VISIBLE
            val parent = recyclerView.parent as? FrameLayout
            parent?.findViewById<View>(android.R.id.progress)?.visibility = View.GONE
            parent?.findViewById<TextView>(android.R.id.text1)?.visibility = View.GONE
        }
    }

    private fun loadListData(recyclerView: RecyclerView, loadMoreBtn: Button) {
        val type = currentGalleryType ?: "movie"
        val galleryId = currentGalleryId

        if (type == "tv") {
            val call = if (galleryId != null) {
                RetrofitClient.getService().getTvListByGallery(galleryId, 1, 30)
            } else {
                RetrofitClient.getService().getTvList(1, 30)
            }
            call.enqueue(object : Callback<ApiListResponse<Tv>> {
                override fun onResponse(call: Call<ApiListResponse<Tv>>, response: Response<ApiListResponse<Tv>>) {
                    val body = response.body()
                    if (body != null && body.code == 200) {
                        listItems.clear()
                        if (body.data != null) listItems.addAll(body.data!!)
                        hasMorePages = body.data != null && body.data!!.size >= 30
                        listAdapter?.notifyDataSetChanged()
                        loadMoreBtn.visibility = if (hasMorePages) View.VISIBLE else View.GONE
                        // 隐藏加载指示器，显示列表
                        recyclerView.visibility = View.VISIBLE
                        val parent = recyclerView.parent as? FrameLayout
                        parent?.findViewById<View>(android.R.id.progress)?.visibility = View.GONE
                        parent?.findViewById<TextView>(android.R.id.text1)?.visibility = View.GONE
                    } else {
                        // 显示错误状态
                        showErrorInLoadingContainer(recyclerView, "加载失败: ${body?.msg ?: "未知错误"}")
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Tv>>, t: Throwable) {
                    toast("加载失败: ${t.message}")
                    showErrorInLoadingContainer(recyclerView, "网络错误: ${t.message}")
                }
            })
        } else {
            val call = if (galleryId != null) {
                RetrofitClient.getService().getMovieListByGallery(galleryId, 1, 30)
            } else {
                RetrofitClient.getService().getMovieList(1, 30)
            }
            call.enqueue(object : Callback<ApiListResponse<Movie>> {
                override fun onResponse(call: Call<ApiListResponse<Movie>>, response: Response<ApiListResponse<Movie>>) {
                    val body = response.body()
                    if (body != null && body.code == 200) {
                        listItems.clear()
                        if (body.data != null) listItems.addAll(body.data!!)
                        currentMovieList = listItems.filterIsInstance<Movie>()
                        hasMorePages = body.data != null && body.data!!.size >= 30
                        listAdapter?.notifyDataSetChanged()
                        loadMoreBtn.visibility = if (hasMorePages) View.VISIBLE else View.GONE
                        // 隐藏加载指示器，显示列表
                        recyclerView.visibility = View.VISIBLE
                        val parent = recyclerView.parent as? FrameLayout
                        parent?.findViewById<View>(android.R.id.progress)?.visibility = View.GONE
                        parent?.findViewById<TextView>(android.R.id.text1)?.visibility = View.GONE
                    } else {
                        // 显示错误状态
                        showErrorInLoadingContainer(recyclerView, "加载失败: ${body?.msg ?: "未知错误"}")
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Movie>>, t: Throwable) {
                    toast("加载失败: ${t.message}")
                    showErrorInLoadingContainer(recyclerView, "网络错误: ${t.message}")
                }
            })
        }
    }

    private fun loadMoreItems(recyclerView: RecyclerView, loadMoreBtn: Button) {
        isLoadingMore = true
        loadMoreBtn.text = "加载中..."
        loadMoreBtn.isEnabled = false

        val type = currentGalleryType ?: "movie"
        val galleryId = currentGalleryId

        if (type == "tv") {
            val call = if (galleryId != null) {
                RetrofitClient.getService().getTvListByGallery(galleryId, currentPage, 30)
            } else {
                RetrofitClient.getService().getTvList(currentPage, 30)
            }
            call.enqueue(object : Callback<ApiListResponse<Tv>> {
                override fun onResponse(call: Call<ApiListResponse<Tv>>, response: Response<ApiListResponse<Tv>>) {
                    isLoadingMore = false
                    loadMoreBtn.isEnabled = true
                    loadMoreBtn.text = "加载更多"
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val start = listItems.size
                        listItems.addAll(body.data!!)
                        listAdapter?.notifyItemRangeInserted(start, body.data!!.size)
                        hasMorePages = body.data!!.size >= 30
                        if (!hasMorePages) loadMoreBtn.visibility = View.GONE
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Tv>>, t: Throwable) {
                    isLoadingMore = false
                    loadMoreBtn.isEnabled = true
                    loadMoreBtn.text = "加载更多"
                    toast("加载失败")
                }
            })
        } else {
            val call = if (galleryId != null) {
                RetrofitClient.getService().getMovieListByGallery(galleryId, currentPage, 30)
            } else {
                RetrofitClient.getService().getMovieList(currentPage, 30)
            }
            call.enqueue(object : Callback<ApiListResponse<Movie>> {
                override fun onResponse(call: Call<ApiListResponse<Movie>>, response: Response<ApiListResponse<Movie>>) {
                    isLoadingMore = false
                    loadMoreBtn.isEnabled = true
                    loadMoreBtn.text = "加载更多"
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val start = listItems.size
                        listItems.addAll(body.data!!)
                        currentMovieList = listItems.filterIsInstance<Movie>()
                        listAdapter?.notifyItemRangeInserted(start, body.data!!.size)
                        hasMorePages = body.data!!.size >= 30
                        if (!hasMorePages) loadMoreBtn.visibility = View.GONE
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Movie>>, t: Throwable) {
                    isLoadingMore = false
                    loadMoreBtn.isEnabled = true
                    loadMoreBtn.text = "加载更多"
                    toast("加载失败")
                }
            })
        }
    }

    // ==================== DETAIL SCREEN ====================

    /**
     * 从列表或搜索页点击卡片进入电影详情。
     * @param fromSearch true=从搜索结果进入（按返回键要回搜索页）
     */
    private fun showMovieDetail(movie: Movie, fromSearch: Boolean = false) {
        pushCurrentToBackStack()
        detailFromSearch = fromSearch
        renderMovieDetail(movie)
    }

    /** 纯渲染电影详情（用于返回键恢复，不推栈、不重新请求 API） */
    private fun renderMovieDetail(movie: Movie) {
        currentScreen = Screen.DETAIL
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        // 清除剧集列表缓存（进入电影详情后不可能还在剧集列表）
        currentSeasonEpisodes = null
        currentSeasonTitle = null
        currentMovie = movie
        currentTv = null

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(40))
        }

        val topBar = buildTopBar(movie.title ?: "详情", showSearch = false, showLogout = false)
        layout.addView(topBar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(32), dp(16), dp(32), 0)
        }

        val posterUrl = RetrofitClient.imageUrl(movie.posterPath) ?: RetrofitClient.customImageUrl(movie.id)
        android.util.Log.d("OneList", "MovieDetail: title='${movie.title}' origTitle='${movie.originalTitle}' posterPath='${movie.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(tvDp(180), tvDp(270))
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a2e"))
                    cornerRadius = tvDp(4).toFloat()
                }
                Glide.with(this@MainActivity).load(posterUrl).placeholder(placeholder).error(placeholder).into(this)
            }
        }
        contentLayout.addView(posterView)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tvDp(24), 0, 0, 0)
        }

        val titleView = TextView(this).apply {
            text = movie.title ?: movie.originalTitle ?: ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(28f))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        infoLayout.addView(titleView)

        val metaText = buildString {
            if (movie.year != null) append(movie.year)
            if (movie.score != null) {
                if (isNotEmpty()) append("  ·  ")
                append("评分: ${movie.score}")
            }
        }
        if (metaText.isNotEmpty()) {
            val metaView = TextView(this).apply {
                text = metaText
                setTextColor(Color.parseColor("#aaaaaa"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                setPadding(0, tvDp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        if (movie.genres != null && movie.genres.isNotEmpty()) {
            val genreText = movie.genres.joinToString(" / ") { it.name ?: "" }
            val genreView = TextView(this).apply {
                text = genreText
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(14f))
                setPadding(0, tvDp(6), 0, 0)
            }
            infoLayout.addView(genreView)
        }

        val playBtn = Button(this).apply {
            text = "▶  播放"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
            setPadding(tvDp(32), tvDp(12), tvDp(32), tvDp(12))
            isFocusable = true
            isClickable = true
            applyFocusGlow(Color.parseColor("#e50914"), Color.parseColor("#ff3b4f"), Color.WHITE)
            setOnClickListener {
                if (movie.url != null) {
                    // 设置心跳元数据，使播放统计正常上报
                    currentVideoDataType = "movie"
                    currentVideoDataId = movie.id
                    currentVideoTitle = movie.title
                    currentVideoGalleryUid = movie.galleryUid
                    currentVideoGalleryTitle = currentGalleryTitle
                    android.util.Log.d("OneList", "Movie play: id=${movie.id} title='${movie.title}' galleryUid='${movie.galleryUid}' galleryTitle='$currentGalleryTitle' url='${movie.url}'")
                    val pl = currentMovieList?.map { PlayItem(it.url!!, it.galleryUid, it.title) }
                    val idx = pl?.indexOfFirst { it.url == movie.url } ?: 0
                    showPlayer(movie.url!!, movie.galleryUid, pl, if (idx >= 0) idx else 0)
                } else {
                    toast("暂无播放源")
                }
            }
        }
        infoLayout.addView(playBtn, LinearLayout.LayoutParams.WRAP_CONTENT.let {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = tvDp(20)
            }
        })

        contentLayout.addView(infoLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(contentLayout)

        if (movie.desc != null && movie.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(tvDp(32), tvDp(24), tvDp(32), tvDp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = movie.desc
                setTextColor(Color.parseColor("#cccccc"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(15f))
                setLineSpacing(0f, 1.4f)
                setPadding(tvDp(32), 0, tvDp(32), 0)
            }
            layout.addView(descView)
        }

        scroll.addView(layout)
        rootLayout.addView(scroll)

        playBtn.post { playBtn.requestFocus() }

        if (movie.desc == null && movie.id != null) {
            fetchMovieDetail(movie.id.toString(), titleView, infoLayout, playBtn)
        }
    }

    private fun fetchMovieDetail(id: String, titleView: TextView, infoLayout: LinearLayout, playBtn: Button) {
        try {
            RetrofitClient.getService().getMovieDetail(id).enqueue(object : Callback<MovieDetailResponse> {
                override fun onResponse(call: Call<MovieDetailResponse>, response: Response<MovieDetailResponse>) {
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val detail = body.data!!
                        if (detail.desc != null) {
                            // 只重新渲染，不要再 push 一次栈（避免返回键失效）
                            renderMovieDetail(Movie(
                                id = detail.id, title = detail.title, posterPath = detail.posterPath,
                                overview = detail.overview, url = detail.url, voteAverage = detail.voteAverage,
                                releaseDate = detail.releaseDate, genres = detail.genres, galleryUid = detail.galleryUid
                            ))
                        }
                    }
                }
                override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {}
            })
        } catch (e: Exception) {}
    }

    private fun showTvDetail(tv: Tv, fromSearch: Boolean = false) {
        pushCurrentToBackStack()
        detailFromSearch = fromSearch
        renderTvDetail(tv)
    }

    /** 纯渲染电视详情（用于返回键恢复，不推栈、不重新请求 API） */
    private fun renderTvDetail(tv: Tv) {
        currentScreen = Screen.DETAIL
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        // 进入TV详情后，可能跳到剧集列表，之后返回回来还能正确识别
        currentSeasonEpisodes = null
        currentSeasonTitle = null
        currentMovie = null
        currentTv = tv

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(40))
        }

        val topBar = buildTopBar(tv.title ?: "详情", showSearch = false, showLogout = false)
        layout.addView(topBar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(32), dp(16), dp(32), 0)
        }

        val posterUrl = RetrofitClient.imageUrl(tv.posterPath) ?: RetrofitClient.customImageUrl(tv.id)
        android.util.Log.d("OneList", "TvDetail: name='${tv.name}' origName='${tv.originalName}' posterPath='${tv.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(tvDp(180), tvDp(270))
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a2e"))
                    cornerRadius = tvDp(4).toFloat()
                }
                Glide.with(this@MainActivity).load(posterUrl).placeholder(placeholder).error(placeholder).into(this)
            }
        }
        contentLayout.addView(posterView)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tvDp(24), 0, 0, 0)
        }

        val titleView = TextView(this).apply {
            text = tv.name ?: tv.originalName ?: ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(28f))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        infoLayout.addView(titleView)

        val metaText = buildString {
            if (tv.year != null) append(tv.year)
        }
        if (metaText.isNotEmpty()) {
            val metaView = TextView(this).apply {
                text = metaText
                setTextColor(Color.parseColor("#aaaaaa"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                setPadding(0, tvDp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        contentLayout.addView(infoLayout)
        layout.addView(contentLayout)

        if (tv.desc != null && tv.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(tvDp(32), tvDp(24), tvDp(32), tvDp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = tv.desc
                setTextColor(Color.parseColor("#cccccc"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(15f))
                setLineSpacing(0f, 1.4f)
                setPadding(tvDp(32), 0, tvDp(32), 0)
            }
            layout.addView(descView)
        }

        val seasonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(tvDp(32), tvDp(24), tvDp(32), 0)
        }
        val seasonsLabel = TextView(this).apply {
            text = "分季"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        seasonsContainer.addView(seasonsLabel)
        layout.addView(seasonsContainer)

        scroll.addView(layout)
        rootLayout.addView(scroll)

        if (tv.id != null) {
            fetchTvDetail(tv.id.toString(), seasonsContainer)
        }
    }

    private fun fetchTvDetail(id: String, seasonsContainer: LinearLayout) {
        try {
            RetrofitClient.getService().getTvDetail(id).enqueue(object : Callback<TvDetailResponse> {
                override fun onResponse(call: Call<TvDetailResponse>, response: Response<TvDetailResponse>) {
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val detail = body.data!!
                        if (detail.seasons != null) {
                            for (season in detail.seasons) {
                                val seasonBtn = Button(this@MainActivity).apply {
                                    text = "第 ${season.seasonNumber ?: "?"} 季"
                                    setTextColor(Color.WHITE)
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                                    setPadding(tvDp(16), tvDp(10), tvDp(16), tvDp(10))
                                    isFocusable = true
                                    isClickable = true
                                    applyFocusGlow(Color.parseColor("#1a1a2e"), Color.parseColor("#6366f1"), Color.WHITE)
                                    val lp = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    lp.topMargin = tvDp(8)
                                    layoutParams = lp
                                    setOnClickListener {
                                        if (season.id != null) {
                                            showSeasonEpisodes(season.id.toString(), season.seasonNumber ?: 0)
                                        }
                                    }
                                }
                                seasonsContainer.addView(seasonBtn)
                            }
                        }
                    }
                }
                override fun onFailure(call: Call<TvDetailResponse>, t: Throwable) {
                    toast("加载详情失败")
                }
            })
        } catch (e: Exception) {}
    }

    private fun showSeasonEpisodes(seasonId: String, seasonNumber: Int) {
        try {
            RetrofitClient.getService().getSeasonDetail(seasonId).enqueue(object : Callback<ApiResponse<SeasonDetail>> {
                override fun onResponse(call: Call<ApiResponse<SeasonDetail>>, response: Response<ApiResponse<SeasonDetail>>) {
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val seasonDetail = body.data!!
                        showEpisodeList(seasonDetail.episodes ?: emptyList(), "第 $seasonNumber 季")
                    }
                }
                override fun onFailure(call: Call<ApiResponse<SeasonDetail>>, t: Throwable) {
                    toast("加载剧集失败")
                }
            })
        } catch (e: Exception) {}
    }

    private fun showEpisodeList(episodes: List<Episode>, seasonTitle: String, restoreFromCache: Boolean = false) {
        if (!restoreFromCache) pushCurrentToBackStack()
        currentScreen = Screen.DETAIL
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        // 保存剧集列表缓存（返回剧集详情还能回播放器 → 再返回来恢复剧集列表）
        currentSeasonEpisodes = episodes
        currentSeasonTitle = seasonTitle
        // currentMovie/currentTv 保留不变，因为从剧集列表返回回到 tv detail 时需要 currentTv

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        val topBar = buildTopBar(seasonTitle, showSearch = false, showLogout = false)
        layout.addView(topBar)

        val scroll = ScrollView(this)
        val episodeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
        }

        for (ep in episodes) {
            val epBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1a1a2e"))
                setPadding(tvDp(16), tvDp(12), tvDp(16), tvDp(12))
                isClickable = true
                isFocusable = true
                applyCardFocus()
                setOnClickListener {
                    if (ep.url != null) {
                        // 设置心跳元数据，使播放统计正常上报
                        currentVideoDataType = "tv"
                        currentVideoDataId = currentTv?.id
                        currentVideoTitle = currentTv?.name ?: ep.title
                        currentVideoGalleryUid = ep.galleryUid
                        currentVideoGalleryTitle = currentGalleryTitle
                        android.util.Log.d("OneList", "TV play: tvId=${currentTv?.id} title='${currentVideoTitle}' galleryUid='${ep.galleryUid}' galleryTitle='$currentGalleryTitle' url='${ep.url}'")
                        val pl = episodes.map { PlayItem(it.url!!, it.galleryUid, it.title) }
                        val idx = episodes.indexOf(ep)
                        showPlayer(ep.url!!, ep.galleryUid, pl, if (idx >= 0) idx else 0)
                    } else {
                        toast("暂无播放源")
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = tvDp(6)

            val epNum = TextView(this).apply {
                text = "E${ep.episodeNumber ?: "?"}"
                setTextColor(Color.parseColor("#6366f1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            epBtn.addView(epNum)

            val epTitle = TextView(this).apply {
                text = "  ${ep.title ?: ""}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(15f))
            }
            epBtn.addView(epTitle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f })

            episodeLayout.addView(epBtn, lp)
        }

        scroll.addView(episodeLayout)
        layout.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })

        rootLayout.addView(layout)
    }

    // ==================== SEARCH SCREEN ====================

    /**
     * 搜索页。正向导航时（从顶栏搜索图标进入）自动推栈。
     * restoreFromCache=true 时不重新初始化数据——但搜索页没有缓存状态要求，
     * 所以不管 restore 与否都重新渲染，只是不推栈。
     */
    private fun showSearch(restoreFromCache: Boolean = false) {
        if (!restoreFromCache) pushCurrentToBackStack()
        currentScreen = Screen.SEARCH
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        val topBar = buildTopBar("搜索", showSearch = false, showLogout = false)
        layout.addView(topBar)

        // Search input row
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(8))
        }

        val searchInput = EditText(this).apply {
            hint = "输入关键词..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            isSingleLine = true
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        searchRow.addView(searchInput)

        val searchBtn = Button(this).apply {
            text = "搜索"
            setTextColor(Color.WHITE)
            isFocusable = true
            isClickable = true
            applyFocusGlow()
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dp(12)
            layoutParams = lp
            setOnClickListener {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) doSearch(q, layout)
            }
        }
        searchRow.addView(searchBtn)
        layout.addView(searchRow)

        // Results container
        val resultsScroll = ScrollView(this)
        val resultsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(16))
            id = View.generateViewId()
        }
        resultsScroll.addView(resultsLayout)
        layout.addView(resultsScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })

        rootLayout.addView(layout)

        // Focus search input
        searchInput.requestFocus()
    }

    private fun doSearch(query: String, parentLayout: LinearLayout) {
        val resultsLayout = parentLayout.findViewById<LinearLayout>(
            (parentLayout.getChildAt(parentLayout.childCount - 1) as ScrollView).let { sv ->
                (sv.getChildAt(0) as LinearLayout).id
            }
        )
        resultsLayout.removeAllViews()

        val loadingView = TextView(this).apply {
            text = "搜索中..."
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(40), 0, dp(40))
        }
        resultsLayout.addView(loadingView)

        // Search movies and TV in parallel
        var movieResults: List<Movie>? = null
        var tvResults: List<Tv>? = null
        var completedCount = 0

        val checkDone = Runnable {
            runOnUiThread {
                completedCount++
                if (completedCount >= 2) {
                    resultsLayout.removeAllViews()
                    renderSearchResults(resultsLayout, movieResults ?: emptyList(), tvResults ?: emptyList())
                }
            }
        }

        try {
            RetrofitClient.getService().searchMovie(query).enqueue(object : Callback<ApiResponse<List<Movie>>> {
                override fun onResponse(call: Call<ApiResponse<List<Movie>>>, response: Response<ApiResponse<List<Movie>>>) {
                    movieResults = response.body()?.data
                    checkDone.run()
                }
                override fun onFailure(call: Call<ApiResponse<List<Movie>>>, t: Throwable) {
                    checkDone.run()
                }
            })
        } catch (e: Exception) { checkDone.run() }

        try {
            RetrofitClient.getService().searchTv(query).enqueue(object : Callback<ApiResponse<List<Tv>>> {
                override fun onResponse(call: Call<ApiResponse<List<Tv>>>, response: Response<ApiResponse<List<Tv>>>) {
                    tvResults = response.body()?.data
                    checkDone.run()
                }
                override fun onFailure(call: Call<ApiResponse<List<Tv>>>, t: Throwable) {
                    checkDone.run()
                }
            })
        } catch (e: Exception) { checkDone.run() }
    }

    private fun renderSearchResults(layout: LinearLayout, movies: List<Movie>, tvs: List<Tv>) {
        if (movies.isEmpty() && tvs.isEmpty()) {
            val emptyLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(tvDp(32), tvDp(60), tvDp(32), tvDp(60))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val emptyIcon = TextView(this).apply {
                text = "🔍"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(48f))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, tvDp(16))
            }
            emptyLayout.addView(emptyIcon)
            
            val empty = TextView(this).apply {
                text = "未找到结果\n请尝试其他关键词"
                setTextColor(Color.parseColor("#cccccc"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.5f)
            }
            emptyLayout.addView(empty)
            
            layout.addView(emptyLayout)
            return
        }

        if (movies.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "电影 (${movies.size})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, tvDp(8), 0, tvDp(8))
            }
            layout.addView(label)
            val gridColumns = calculateGridColumns()
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
                adapter = CardAdapter(movies.map { it as Any }, "movie") { item ->
                    if (item is Movie) showMovieDetail(item, fromSearch = true)
                }
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tvDp(280 * ((movies.size + gridColumns - 1) / gridColumns))
            ))
        }

        if (tvs.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "电视 (${tvs.size})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, tvDp(16), 0, tvDp(8))
            }
            layout.addView(label)
            val gridColumns = calculateGridColumns()
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
                adapter = CardAdapter(tvs.map { it as Any }, "tv") { item ->
                    if (item is Tv) showTvDetail(item, fromSearch = true)
                }
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                tvDp(280 * ((tvs.size + gridColumns - 1) / gridColumns))
            ))
        }
    }

    // ==================== PLAYER SCREEN ====================

    private fun showPlayer(
        url: String, galleryUid: String?,
        playlist: List<PlayItem>? = null, playIndex: Int = 0,
        pushToBackStack: Boolean = true
    ) {
        // 进入播放器前，将上一步（详情/剧集列表/搜索）推入返回栈
        // 自动连播/上下键切换时不推栈，避免返回栈堆积重复的播放器页面
        if (pushToBackStack) {
            pushCurrentToBackStack()
        }
        currentScreen = Screen.PLAYER
        // 释放旧播放器，避免切换视频时旧视频继续在后台播放（音频叠加）
        stopHeartbeat()
        player?.release()
        player = null
        rootLayout.removeAllViews()

        // 保存播放列表状态（用于遥控器上下键切换）
        currentPlaylist = playlist
        currentPlayIndex = playIndex

        android.util.Log.d("OneList", "Player: original url='$url' galleryUid='$galleryUid' playlist=${playlist?.size ?: 0} index=$playIndex")
        if (url.isEmpty() || galleryUid == null) {
            toast("无效的播放地址")
            showHome()
            return
        }

        // 渲染播放器框架 + loading 提示
        val playerContainer = FrameLayout(this).apply { fillParent() }

        val playerView = PlayerView(this).apply {
            fillParent()
            useController = true
        }
        playerContainer.addView(playerView)

        val loadingText = TextView(this).apply {
            text = "正在加载视频源..."
            setTextColor(Color.parseColor("#cccccc"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        val loadingLP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        playerContainer.addView(loadingText, loadingLP)

        // 连播提示（右上角显示"下一个"）
        val nextHint = TextView(this).apply {
            setTextColor(Color.parseColor("#cccccc"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.parseColor("#80000000"))
            visibility = View.GONE
        }
        val nextHintLP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = dp(16); rightMargin = dp(16) }
        playerContainer.addView(nextHint, nextHintLP)

        rootLayout.addView(playerContainer)

        // 加载播放列表（同目录视频列表），然后播放当前视频
        loadPlaylistAndPlay(playerView, loadingText, nextHint, url, galleryUid)
    }

    /**
     * 请求 /v1/api/playlist 获取同目录视频列表，找到当前 url 的索引，
     * 然后解析并播放。播放列表为空也能正常播放当前视频。
     */
    private fun loadPlaylistAndPlay(
        playerView: PlayerView, loadingText: TextView, nextHint: TextView,
        url: String, galleryUid: String
    ) {
        try {
            RetrofitClient.getService().getPlaylist(galleryUid, url)
                .enqueue(object : Callback<PlaylistResponse> {
                    override fun onResponse(call: Call<PlaylistResponse>, response: Response<PlaylistResponse>) {
                        val body = response.body()
                        if (body != null && body.code == 200 && body.data != null && body.data!!.isNotEmpty()) {
                            serverPlaylist = body.data!!
                            // 查找当前 url 在列表中的位置
                            serverPlaylistIndex = serverPlaylist.indexOfFirst { p ->
                                p == url || p == url.trimStart('/') ||
                                (url.startsWith("/file/") && p == url) ||
                                (p.startsWith("/file/") && p.substring(5) == url.trimStart('/'))
                            }
                            if (serverPlaylistIndex < 0) serverPlaylistIndex = 0
                            android.util.Log.d("OneList", "Playlist loaded: ${serverPlaylist.size} items, currentIndex=$serverPlaylistIndex")
                            updateNextHint(nextHint)
                        } else {
                            android.util.Log.d("OneList", "Playlist empty or failed, single play mode")
                        }
                        // 解析并播放当前视频
                        resolveAndPlay(playerView, loadingText, nextHint, url, galleryUid)
                    }
                    override fun onFailure(call: Call<PlaylistResponse>, t: Throwable) {
                        android.util.Log.w("OneList", "Playlist load failed: ${t.message}, single play mode")
                        resolveAndPlay(playerView, loadingText, nextHint, url, galleryUid)
                    }
                })
        } catch (e: Exception) {
            android.util.Log.w("OneList", "Playlist request error: ${e.message}, single play mode")
            resolveAndPlay(playerView, loadingText, nextHint, url, galleryUid)
        }
    }

    /**
     * 更新右上角连播提示
     */
    private fun updateNextHint(nextHint: TextView) {
        if (serverPlaylist.isNotEmpty() && serverPlaylistIndex < serverPlaylist.size - 1) {
            val nextIdx = serverPlaylistIndex + 1
            val nextName = serverPlaylist[nextIdx].substringAfterLast('/').substringBeforeLast('.')
            nextHint.text = "下一个: $nextName  (${nextIdx + 1}/${serverPlaylist.size})"
            nextHint.visibility = View.VISIBLE
        } else {
            nextHint.visibility = View.GONE
        }
    }

    /**
     * 播放列表中下一个视频（由 STATE_ENDED 触发）
     */
    private fun playNextInPlaylist(
        playerView: PlayerView, loadingText: TextView, nextHint: TextView,
        galleryUid: String
    ): Boolean {
        if (serverPlaylist.isEmpty() || serverPlaylistIndex >= serverPlaylist.size - 1) return false
        serverPlaylistIndex++
        val nextUrl = serverPlaylist[serverPlaylistIndex]
        android.util.Log.d("OneList", "Auto-playing next: index=$serverPlaylistIndex url='$nextUrl'")

        // 自动连播时更新心跳元数据：从 currentMovieList 中查找匹配的电影，更新 dataId 和 title
        // 这样自动播放的下一部电影才能生成正确的播放统计记录
        val nextMovie = currentMovieList?.firstOrNull { m ->
            val mu = m.url ?: return@firstOrNull false
            mu == nextUrl || mu == nextUrl.trimStart('/') ||
            (nextUrl.startsWith("/file/") && mu == nextUrl) ||
            (mu.startsWith("/file/") && mu.substring(5) == nextUrl.trimStart('/'))
        }
        if (nextMovie != null) {
            currentVideoDataId = nextMovie.id
            currentVideoTitle = nextMovie.title
            currentVideoGalleryUid = nextMovie.galleryUid
            android.util.Log.d("OneList", "Auto-play heartbeat updated (movie): id=${nextMovie.id} title='${nextMovie.title}' galleryUid='${nextMovie.galleryUid}'")
        } else {
            // 电视剧集自动连播：currentMovieList 为空，尝试从 currentPlaylist 查找
            // 电视剧的 dataId 是剧集 ID（同一部剧不变），但 title 需要更新为当前集
            val nextItem = currentPlaylist?.firstOrNull { p ->
                p.url == nextUrl || p.url == nextUrl.trimStart('/') ||
                (nextUrl.startsWith("/file/") && p.url == nextUrl) ||
                (p.url.startsWith("/file/") && p.url.substring(5) == nextUrl.trimStart('/'))
            }
            if (nextItem != null && currentVideoDataType == "tv") {
                // 电视剧：dataId 不变（同一部剧），只更新 title 和 galleryUid
                currentVideoTitle = nextItem.title ?: currentVideoTitle
                currentVideoGalleryUid = nextItem.galleryUid ?: currentVideoGalleryUid
                android.util.Log.d("OneList", "Auto-play heartbeat updated (tv): title='${currentVideoTitle}' galleryUid='${currentVideoGalleryUid}'")
            } else {
                android.util.Log.d("OneList", "Auto-play: no matching item found for url='$nextUrl', keeping old heartbeat metadata")
            }
        }

        updateNextHint(nextHint)
        // 显示 loading
        loadingText.text = "正在加载下一集..."
        loadingText.visibility = View.VISIBLE
        resolveAndPlay(playerView, loadingText, nextHint, nextUrl, galleryUid)
        return true
    }

    /**
     * 解析视频源 URL（alist代理/本地直链/阿里云盘open），然后启动 ExoPlayer
     */
    private fun resolveAndPlay(
        playerView: PlayerView, loadingText: TextView, nextHint: TextView,
        url: String, galleryUid: String
    ) {
        val base = App.serverUrl
        if (base == null || base.isEmpty()) {
            loadingText.text = "请先配置服务器地址"
            return
        }
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base

        // 直接 HTTP/HTTPS 直链，跳过接口判断
        if (url.startsWith("http")) {
            loadingText.visibility = View.GONE
            startExoPlayer(playerView, url, loadingText, nextHint, galleryUid)
            return
        }

        // 请求 gallery/host 判断 alist 代理 / 本地直链 / 阿里云盘open
        try {
            RetrofitClient.getService().getGalleryHost(galleryUid).enqueue(object : retrofit2.Callback<GalleryHostResponse> {
                override fun onResponse(call: retrofit2.Call<GalleryHostResponse>, response: retrofit2.Response<GalleryHostResponse>) {
                    val body = response.body()
                    android.util.Log.d("OneList", "GalleryHost code=${body?.code} msg=${body?.msg} data=${body?.`data`} isAliOpen=${body?.isAliOpen}")
                    if (body == null || body.code != 200) {
                        loadingText.text = "获取媒体库信息失败: HTTP ${response.code()}" +
                            (if (body != null) " code=${body.code} msg=${body.msg}" else "")
                        return
                    }
                    val alistHost = body.`data` ?: ""
                    val isAliOpen = body.isAliOpen == true

                    if (isAliOpen) {
                        loadingText.text = "正在获取阿里云盘播放地址..."
                        try {
                            RetrofitClient.getService().getAliOpenVideo(
                                AliOpenVideoRequest(file = url, galleryUid = galleryUid)
                            ).enqueue(object : retrofit2.Callback<AliOpenVideoResponse> {
                                override fun onResponse(call: retrofit2.Call<AliOpenVideoResponse>, response: retrofit2.Response<AliOpenVideoResponse>) {
                                    val r = response.body()
                                    if (r == null || r.code != 200 || r.data == null) {
                                        loadingText.text = "获取播放地址失败: " + (r?.msg ?: "未知错误")
                                        return
                                    }
                                    try {
                                        val tasks = r.data!!.videoPreviewPlayInfo?.liveTranscodingTaskList
                                        if (tasks != null && tasks.isNotEmpty()) {
                                            val bestUrl = tasks.lastOrNull()?.url
                                            if (bestUrl != null) {
                                                loadingText.visibility = View.GONE
                                                startExoPlayer(playerView, bestUrl, loadingText, nextHint, galleryUid)
                                            } else {
                                                loadingText.text = "没有可用的播放地址"
                                            }
                                        } else {
                                            loadingText.text = "没有可用的播放地址"
                                        }
                                    } catch (e: Exception) {
                                        loadingText.text = "解析播放地址出错: ${e.message}"
                                    }
                                }
                                override fun onFailure(call: retrofit2.Call<AliOpenVideoResponse>, t: Throwable) {
                                    loadingText.text = "获取阿里云盘地址失败: ${t.message}"
                                }
                            })
                        } catch (e: Exception) {
                            loadingText.text = "请求出错: ${e.message}"
                        }
                        return
                    }

                    // alist 代理 或 本地直链
                    val videoSrc = if (alistHost.isNotEmpty()) {
                        if (url.startsWith("/alist/proxy/")) "$normalizedBase$url"
                        else "$normalizedBase/alist/proxy/$galleryUid$url"
                    } else {
                        if (url.startsWith("/file/")) "$normalizedBase$url"
                        else "$normalizedBase/file/${url.trimStart('/')}"
                    }
                    android.util.Log.d("OneList", "Player resolved: alistHost='$alistHost' videoSrc='$videoSrc'")
                    loadingText.text = "正在播放: $videoSrc"
                    startExoPlayer(playerView, videoSrc, loadingText, nextHint, galleryUid)
                    // 播放地址显示10秒后自动隐藏，避免遮挡画面
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        if (loadingText.text.toString().startsWith("正在播放")) {
                            loadingText.visibility = View.GONE
                        }
                    }, 10000)
                }
                override fun onFailure(call: retrofit2.Call<GalleryHostResponse>, t: Throwable) {
                    loadingText.text = "获取媒体库信息失败: ${t.message}"
                }
            })
        } catch (e: Exception) {
            loadingText.text = "错误: ${e.message}"
        }
    }

    private fun startExoPlayer(
        playerView: PlayerView, videoUrl: String,
        loadingText: TextView, nextHint: TextView, galleryUid: String
    ) {
        // Use OkHttp as data source — fixes TLS/SSL issues on old Android (4.4)
        // where the built-in HttpsURLConnection doesn't support modern TLS.
        //
        // URL 编码策略：
        //   不在 MediaItem.setUri() 之前编码 URL，因为：
        //   1. android.net.Uri.encode 把空格编码为 '+'，OkHttp/Gin 不认
        //   2. MediaItem.Builder.setUri() 内部会再次解析 URL，可能双重编码
        //   改为在 OkHttp 拦截器中编码：拦截器拿到 ExoPlayer 传的原始 URL，
        //   用 URLEncoder 对每个 path segment 编码（空格→%20，中文→UTF-8 %XX），
        //   再用 OkHttp 的 HttpUrl.Builder.addEncodedPathSegment 构造请求，
        //   这样 OkHttp 不会再次编码，保证服务器收到的是正确的 URL。
        val okHttpClientForVideo = okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .also { client ->
                val token = App.token
                client.addInterceptor { chain ->
                    val originalReq = chain.request()
                    val originalUrlStr = originalReq.url.toString()

                    // 只有 URL 含中文/空格等非法字符时才编码
                    val needsEncoding = originalUrlStr.any { it.code > 127 || it == ' ' }
                    if (!needsEncoding) {
                        if (token != null && token.isNotEmpty()) {
                            val req = originalReq.newBuilder()
                                .header("Authorization", token).build()
                            return@addInterceptor chain.proceed(req)
                        }
                        return@addInterceptor chain.proceed(originalReq)
                    }

                    // 手动拆分 URL，按 path segment 用 URLEncoder 编码
                    // （OkHttp 的 addEncodedPathSegment 等 API 不可用/不可访问）
                    val encodedUrlStr = try {
                        val schemeEnd = originalUrlStr.indexOf("://")
                        val scheme = originalUrlStr.substring(0, schemeEnd)
                        val rest = originalUrlStr.substring(schemeEnd + 3)
                        val slashIdx = rest.indexOf('/')
                        if (slashIdx < 0) originalUrlStr
                        else {
                            val hostPort = rest.substring(0, slashIdx)
                            val pathAndQuery = rest.substring(slashIdx)
                            val queryIdx = pathAndQuery.indexOf('?')
                            val pathPart = if (queryIdx >= 0) pathAndQuery.substring(0, queryIdx) else pathAndQuery
                            val queryPart = if (queryIdx >= 0) pathAndQuery.substring(queryIdx) else ""
                            val encodedPath = pathPart.split("/").joinToString("/") { seg ->
                                java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
                            }
                            "$scheme://$hostPort$encodedPath$queryPart"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("OneList", "encodeUrl failed", e)
                        originalUrlStr
                    }

                    android.util.Log.d("OneList", "Interceptor: '$originalUrlStr' → '$encodedUrlStr'")

                    // 直接用编码后的 URL 字符串构造请求。
                    // Request.Builder.url(String) 内部调用 toHttpUrl()（OkHttp 包内
                    // 方法，不需要外部 import 扩展函数），已编码的 %XX 不会再次编码。
                    val newReq = try {
                        val reqBuilder = originalReq.newBuilder().url(encodedUrlStr)
                        if (token != null && token.isNotEmpty()) {
                            reqBuilder.header("Authorization", token)
                        }
                        reqBuilder.build()
                    } catch (e: Exception) {
                        android.util.Log.e("OneList", "URL build failed: ${e.message}")
                        originalReq
                    }
                    chain.proceed(newReq)
                }
            }
            .build()
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClientForVideo)

        // 根据文件扩展名推断 MIME type（用原始 URL 因为 MediaItem 只是容器，
        // 实际 URL 在拦截器中编码）
        val lowerUrl = videoUrl.lowercase()
        val mimeType = when {
            lowerUrl.contains(".mp4") -> "video/mp4"
            lowerUrl.contains(".mkv") -> "video/x-matroska"
            lowerUrl.contains(".ts") -> "video/mp2t"
            lowerUrl.contains(".webm") -> "video/webm"
            lowerUrl.contains(".flv") -> "video/x-flv"
            lowerUrl.contains(".avi") -> "video/mp4"
            lowerUrl.contains(".m4v") -> "video/mp4"
            lowerUrl.contains(".mov") -> "video/mp4"
            else -> "video/mp4"
        }

        // 释放旧播放器（自动连播时旧 player 仍存在）
        player?.release()
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                com.google.android.exoplayer2.source.DefaultMediaSourceFactory(dataSourceFactory)
            )
            .build().also { exo ->
                playerView.player = exo
                // 直接用原始 URL（含中文/空格），拦截器会编码
                val mediaItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .setMimeType(mimeType)
                    .build()
                exo.setMediaItem(mediaItem)
                exo.playWhenReady = true
                exo.prepare()
                
                // Start heartbeat on play
                startHeartbeat(exo)
                android.util.Log.d("OneList", "Player prepared, url='$videoUrl' mime='$mimeType'")
                exo.addListener(object : com.google.android.exoplayer2.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        val stateName = when (state) {
                            com.google.android.exoplayer2.Player.STATE_IDLE -> "IDLE"
                            com.google.android.exoplayer2.Player.STATE_BUFFERING -> "BUFFERING"
                            com.google.android.exoplayer2.Player.STATE_READY -> "READY"
                            com.google.android.exoplayer2.Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($state)"
                        }
                        android.util.Log.d("OneList", "Player state: $stateName")
                        // 播放结束自动播放下一个（列表连续播放）
                        if (state == com.google.android.exoplayer2.Player.STATE_ENDED) {
                            // Stop heartbeat
                            stopHeartbeat()
                            
                            val played = playNextInPlaylist(playerView, loadingText, nextHint, galleryUid)
                            if (!played) {
                                toast("播放完毕")
                            }
                        }
                    }
                    override fun onPlayerError(error: com.google.android.exoplayer2.PlaybackException) {
                        val errorCodeName = when (error.errorCode) {
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "DECODING_FORMAT_EXCEEDS_CAPABILITIES"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "FAILED_RUNTIME_CHECK"
                            com.google.android.exoplayer2.PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
                            else -> "UNKNOWN(${error.errorCode})"
                        }
                        android.util.Log.e("OneList", "Player ERROR: code=$errorCodeName(${error.errorCode}) message='${error.message}' cause=${error.cause}", error)
                        android.util.Log.e("OneList", "Player ERROR cause chain:")
                        var t: Throwable? = error
                        while (t != null) {
                            android.util.Log.e("OneList", "  -> ${t::class.java.name}: ${t.message}")
                            t = t.cause
                        }
                        // 显示 cause 链中最具体的错误信息（"Source error" 太笼统）
                        var detail = error.message ?: ""
                        var cause: Throwable? = error.cause
                        while (cause != null) {
                            val cm = cause.message
                            if (cm != null && cm.isNotEmpty()) detail = cm
                            cause = cause.cause
                        }
                        toast("播放失败 [$errorCodeName]: $detail")
                    }
                })
            }
    }

    // ==================== TOP BAR ====================

    private fun buildTopBar(title: String, showSearch: Boolean, showLogout: Boolean): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(tvDp(24), tvDp(12), tvDp(24), tvDp(12))
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        // Back button (if not on home)
        if (currentScreen != Screen.HOME && currentScreen != Screen.LOGIN) {
            val backBtn = TextView(this).apply {
                text = "← 返回"
                setTextColor(Color.parseColor("#6366f1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                isClickable = true
                isFocusable = true
                setPadding(tvDp(8), tvDp(4), tvDp(8), tvDp(4))
                applyTextFocus(Color.WHITE, Color.parseColor("#6366f1"))
                setOnClickListener { navigateBack() }
            }
            bar.addView(backBtn)
        }

        // Title
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(22f))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleLp.leftMargin = tvDp(8)
        bar.addView(titleView, titleLp)

        // Search button
        if (showSearch) {
            val searchBtn = TextView(this).apply {
                text = "🔍 搜索"
                setTextColor(Color.parseColor("#aaaaaa"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
                isClickable = true
                isFocusable = true
                setPadding(tvDp(12), tvDp(4), tvDp(12), tvDp(4))
                applyTextFocus(Color.WHITE, Color.parseColor("#aaaaaa"))
                setOnClickListener { showSearch() }
            }
            bar.addView(searchBtn)
        }

        // Logout button
        if (showLogout) {
            val logoutBtn = TextView(this).apply {
                text = "退出"
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(14f))
                isClickable = true
                isFocusable = true
                setPadding(tvDp(12), tvDp(4), tvDp(12), tvDp(4))
                applyTextFocus(Color.WHITE, Color.parseColor("#888888"))
                setOnClickListener {
                    App.logout()
                    showLogin()
                }
            }
            bar.addView(logoutBtn)
        }

        return bar
    }

    /**
     * 将当前屏幕推入返回栈。规则：
     * - HOME/SEARCH 不再往里推（避免返回时出现重复首页）
     * - 每次导航到新屏幕前调用，把「离开时的屏幕」存栈
     */
    private fun pushCurrentToBackStack() {
        when (currentScreen) {
            Screen.HOME -> screenBackStack.addLast(ScreenState.Home)
            Screen.SEARCH -> screenBackStack.addLast(ScreenState.Search)
            Screen.LIST -> screenBackStack.addLast(
                ScreenState.ListScreen(currentGalleryId, currentGalleryTitle, currentGalleryType)
            )
            Screen.DETAIL -> {
                when {
                    currentSeasonEpisodes != null -> {
                        val tv = currentTv
                        if (tv != null) {
                            screenBackStack.addLast(
                                ScreenState.EpisodeListScreen(
                                    currentGalleryId, currentGalleryTitle, currentGalleryType,
                                    detailFromSearch, tv,
                                    currentSeasonEpisodes!!,
                                    currentSeasonTitle ?: "剧集"
                                )
                            )
                        }
                    }
                    currentMovie != null -> {
                        screenBackStack.addLast(
                            ScreenState.MovieDetailScreen(
                                currentGalleryId, currentGalleryTitle, currentGalleryType,
                                detailFromSearch, currentMovie!!
                            )
                        )
                    }
                    currentTv != null -> {
                        screenBackStack.addLast(
                            ScreenState.TvDetailScreen(
                                currentGalleryId, currentGalleryTitle, currentGalleryType,
                                detailFromSearch, currentTv!!
                            )
                        )
                    }
                }
            }
            Screen.PLAYER, Screen.LOGIN -> { /* 不推入 */ }
        }
    }

    /** 根据 ScreenState 恢复对应屏幕（用缓存的数据，不再请求 API） */
    private fun restoreScreen(state: ScreenState) {
        when (state) {
            ScreenState.Home -> showHome()
            ScreenState.Search -> showSearch()
            is ScreenState.ListScreen -> {
                currentGalleryId = state.galleryId
                currentGalleryTitle = state.galleryTitle
                currentGalleryType = state.galleryType
                showList(restoreFromCache = true)
            }
            is ScreenState.MovieDetailScreen -> {
                currentGalleryId = state.galleryId
                currentGalleryTitle = state.galleryTitle
                currentGalleryType = state.galleryType
                detailFromSearch = state.fromSearch
                renderMovieDetail(state.movie) // 直接渲染，不重新请求
            }
            is ScreenState.TvDetailScreen -> {
                currentGalleryId = state.galleryId
                currentGalleryTitle = state.galleryTitle
                currentGalleryType = state.galleryType
                detailFromSearch = state.fromSearch
                renderTvDetail(state.tv)
            }
            is ScreenState.EpisodeListScreen -> {
                currentGalleryId = state.galleryId
                currentGalleryTitle = state.galleryTitle
                currentGalleryType = state.galleryType
                detailFromSearch = state.fromSearch
                currentTv = state.parentTv
                currentSeasonEpisodes = state.episodes
                currentSeasonTitle = state.seasonTitle
                showEpisodeList(state.episodes, state.seasonTitle, restoreFromCache = true)
            }
            is ScreenState.PlayerScreen -> { /* 播放器没有返回自己的场景 */ }
        }
    }

    private fun navigateBack() {
        when (currentScreen) {
            Screen.LOGIN -> {} // 登录页不能返回
            Screen.HOME -> finish() // 首页直接退出APP
            else -> {
                if (screenBackStack.isNotEmpty()) {
                    val prev = screenBackStack.removeLast()
                    android.util.Log.d("OneList", "Navigate back from $currentScreen -> ${prev::class.java.simpleName}")
                    restoreScreen(prev)
                } else {
                    // 空栈兜底：回首页
                    showHome()
                }
            }
        }
    }

    // ==================== PLAYLIST NAVIGATION ====================

    private fun playNext() {
        val pl = currentPlaylist ?: return
        if (currentPlayIndex >= pl.size - 1) {
            toast("已经是最后一个了")
            return
        }
        currentPlayIndex++
        val next = pl[currentPlayIndex]
        android.util.Log.d("OneList", "playNext: index=$currentPlayIndex title='${next.title}'")
        toast("下一集: ${next.title ?: ""}")
        showPlayer(next.url, next.galleryUid, pl, currentPlayIndex, pushToBackStack = false)
    }

    private fun playPrevious() {
        val pl = currentPlaylist ?: return
        if (currentPlayIndex <= 0) {
            toast("已经是第一个了")
            return
        }
        currentPlayIndex--
        val prev = pl[currentPlayIndex]
        android.util.Log.d("OneList", "playPrevious: index=$currentPlayIndex title='${prev.title}'")
        toast("上一集: ${prev.title ?: ""}")
        showPlayer(prev.url, prev.galleryUid, pl, currentPlayIndex, pushToBackStack = false)
    }

    // ==================== KEY EVENTS ====================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // ESC 或 BACK 键：返回上一页
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) {
                navigateBack()
                return true
            }
            
            // 播放器页面的方向键控制
            if (currentScreen == Screen.PLAYER && player != null) {
                when (event.keyCode) {
                    // 左方向键：快退 5 秒
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val newPos = (player!!.currentPosition - 5000).coerceAtLeast(0)
                        player!!.seekTo(newPos)
                        android.util.Log.d("OneList", "Seek backward 5s to ${newPos}ms")
                        return true
                    }
                    // 右方向键：快进 5 秒
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val duration = player!!.duration
                        val newPos = if (duration > 0) (player!!.currentPosition + 5000).coerceAtMost(duration) else player!!.currentPosition + 5000
                        player!!.seekTo(newPos)
                        android.util.Log.d("OneList", "Seek forward 5s to ${newPos}ms")
                        return true
                    }
                    // 上方向键：上一集/上一个电影
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        playPrevious()
                        return true
                    }
                    // 下方向键：下一集/下一个电影
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        playNext()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // onBackPressed 作为兜底，确保 BACK 键始终能触发返回逻辑
    @Deprecated("Use onBackPressedDispatcher instead", ReplaceWith("super.onBackPressed()"))
    override fun onBackPressed() {
        navigateBack()
    }

    override fun onStop() {
        super.onStop()
        // onStop 在 Activity 不可见时触发（Home键、灭屏、切换应用等）。
        // 只做最最小化：暂停播放。不 release 播放器、不动 view 树、不停心跳、不断 SSE。
        // 心跳/SSE 清理交给 onDestroy()；恢复播放由 onStart() 对称处理。
        player?.pause()
    }

    override fun onStart() {
        super.onStart()
        // 对称 onStop() 的 pause：恢复播放（排除已结束/空闲状态）。
        // 不碰 SSE 和心跳——SSE 由 onCreate/onDestroy 管理，心跳由 startExoPlayer 启动。
        if (currentScreen == Screen.PLAYER && player != null) {
            val p = player!!
            if (p.playbackState != com.google.android.exoplayer2.Player.STATE_ENDED &&
                p.playbackState != com.google.android.exoplayer2.Player.STATE_IDLE) {
                p.play()
            }
        }
    }

    override fun onDestroy() {
        isDestroying = true
        // 清理 SSE 资源
        try { sseEventSource?.cancel() } catch (_: Exception) {}
        sseEventSource = null
        try { sseClient?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        try { sseClient?.connectionPool?.evictAll() } catch (_: Exception) {}
        sseClient = null
        // 清理心跳资源
        stopHeartbeat()
        // 释放播放器
        player?.release()
        player = null
        super.onDestroy()
    }

    // ==================== HELPERS ====================

    /**
     * 为按钮设置焦点高亮样式（TV 遥控器模式）
     * - 聚焦后：放大 + 描边 + 背景变亮（TV优化：放大1.12x，4dp白色描边）
     */
    private fun View.applyFocusGlow(defaultColor: Int = Color.parseColor("#6366f1"), focusedColor: Int = Color.parseColor("#8b8ef7"), strokeColor: Int = Color.WHITE) {
        val gd = GradientDrawable().apply {
            setColor(defaultColor)
            cornerRadius = tvDp(4).toFloat()
        }
        this.background = gd
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.12f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
                val bg = (v.background as? GradientDrawable) ?: gd.also { v.background = it }
                bg.setColor(if (hasFocus) focusedColor else defaultColor)
                if (hasFocus) bg.setStroke(tvDp(4), strokeColor) else bg.setStroke(0, 0)
            } catch (e: Exception) {}
        }
    }

    /**
     * 卡片/列表项焦点高亮：放大 + 背景变色（TV优化：放大1.1x，更明显的边框）
     */
    private fun View.applyCardFocus() {
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.1f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
                if (hasFocus) {
                    v.setBackgroundColor(Color.parseColor("#6366f1"))
                    // 添加可见的边框
                    val gd = GradientDrawable().apply {
                        setColor(Color.parseColor("#6366f1"))
                        setStroke(tvDp(3), Color.WHITE)
                        cornerRadius = tvDp(4).toFloat()
                    }
                    v.background = gd
                } else {
                    v.setBackgroundColor(Color.parseColor("#1a1a2e"))
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * TextView 焦点样式（顶部导航文本按钮等）：聚焦后反色 + 描边 + 轻微放大（TV优化：1.08x）
     */
    private fun TextView.applyTextFocus(focusedTextColor: Int = Color.WHITE, defaultTextColor: Int = Color.parseColor("#6366f1")) {
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.08f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
                this.setTextColor(if (hasFocus) focusedTextColor else defaultTextColor)
                if (hasFocus) {
                    this.setBackgroundColor(Color.parseColor("#2a2a4e"))
                } else {
                    this.setBackgroundColor(Color.TRANSPARENT)
                }
            } catch (e: Exception) {}
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#aaaaaa"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, dp(4))
        }
    }

    // ==================== HELPERS ====================

    /**
     * 在加载容器中显示错误状态和重试按钮
     */
    private fun showErrorInLoadingContainer(recyclerView: RecyclerView, errorMsg: String) {
        val parent = recyclerView.parent as? FrameLayout ?: return
        parent.removeAllViews()
        
        val errorLayout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(tvDp(32), tvDp(32), tvDp(32), tvDp(32))
        }
        
        // 错误图标（用文字代替）
        val errorIcon = TextView(parent.context).apply {
            text = "⚠️"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(48f))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, tvDp(16))
        }
        errorLayout.addView(errorIcon)
        
        // 错误信息
        val errorText = TextView(parent.context).apply {
            text = errorMsg
            setTextColor(Color.parseColor("#cccccc"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, tvDp(24))
        }
        errorLayout.addView(errorText)
        
        // 重试按钮
        val retryBtn = Button(parent.context).apply {
            text = "重试"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, tvSp(16f))
            setPadding(tvDp(32), tvDp(12), tvDp(32), tvDp(12))
            isFocusable = true
            isClickable = true
            applyFocusGlow(Color.parseColor("#e50914"), Color.parseColor("#ff3b4f"), Color.WHITE)
            setOnClickListener {
                // 重新加载数据
                currentPage = 1
                isLoadingMore = false
                hasMorePages = true
                listItems.clear()
                loadListData(recyclerView, parent.findViewById<Button>(android.R.id.button1) ?: run {
                    // 如果找不到按钮，重新创建
                    val btn = Button(parent.context).apply {
                        visibility = View.GONE
                    }
                    parent.addView(btn)
                    btn
                })
            }
        }
        errorLayout.addView(retryBtn)
        
        parent.addView(errorLayout)
    }

    /**
     * 根据屏幕宽度动态计算网格列数（TV适配）
     * 每列最小宽度200dp，确保卡片在不同分辨率下都有合适大小
     */
    private fun calculateGridColumns(): Int {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val minColumnWidthDp = 200f
        val minColumnWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minColumnWidthDp,
            resources.displayMetrics
        )
        val columns = (screenWidth / minColumnWidthPx).toInt().coerceIn(3, 8)
        android.util.Log.d("OneList", "Screen width: ${screenWidth.toInt()}px, grid columns: $columns")
        return columns
    }

    /**
     * TV适配：放大字体基准（相比手机增加20-30%）
     */
    private fun tvSp(baseSp: Float): Float {
        return baseSp * 1.25f
    }

    /**
     * TV适配：增大间距（相比手机增加50%）
     */
    private fun tvDp(baseDp: Int): Int {
        return (baseDp * 1.5f).toInt()
    }

    private fun lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h)
    }

    private fun View.fillParent() {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }
}
