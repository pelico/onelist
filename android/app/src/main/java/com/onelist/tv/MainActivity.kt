package com.onelist.tv

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
    private enum class Screen { LOGIN, HOME, LIST, DETAIL, SEARCH, PLAYER, HEART, PLAYED }
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
    // 遥控器操作提示：每次按键时在屏幕中央短暂显示操作反馈（如"快进5s"、"暂停"）
    private var operationHintView: TextView? = null
    private val operationHintHandler = Handler(Looper.getMainLooper())

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

    // Token 失效广播接收器：当后端返回 401 时自动跳转登录页
    private val tokenInvalidReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "ACTION_TOKEN_INVALID") {
                runOnUiThread {
                    if (currentScreen != Screen.LOGIN) {
                        toast("登录已失效，请重新登录")
                        App.logout()
                        showLogin()
                    }
                }
            }
        }
    }

    // Play statistics heartbeat
    private var heartbeatExecutor: ScheduledExecutorService? = null
    private var lastHeartbeatPosition: Long = 0
    private var lastHeartbeatRealTime: Long = 0 // 真实时间戳，用于限制快进导致的时长虚高
    
    // Current video metadata for heartbeat
    private var currentVideoDataType: String? = null
    private var currentVideoDataId: Int? = null
    private var currentVideoTitle: String? = null
    private var currentVideoGalleryUid: String? = null
    private var currentVideoUrl: String? = null
    private var currentVideoGalleryTitle: String? = null
    private var lastPlayedToggleId: Int? = null // 防止重复调用 togglePlayed

    // ==================== 护眼屏保状态 ====================
    private var screensaverEnabled = true
    private var screensaverPlayDuration = 3600   // 连续播放多少秒后触发休息屏保
    private var screensaverDuration = 180        // 休息屏保持续多少秒
    private var screensaverDailyLimit = 7200     // 每日最大播放秒数
    private var cumulativePlaySeconds = 0        // 本次连续播放累计秒数
    private var todayTotalSeconds = 0            // 今日已播放总秒数（服务端初始值 + 本地累计）
    private var screensaverActive = false        // 屏保是否正在显示
    private var screensaverMode = "rest"         // "rest" = 休息倒计时, "locked" = 每日锁定
    private var screensaverCountdown = 0         // 休息模式剩余秒数
    private var wallpaperFiles: List<WallpaperFile> = emptyList()
    private val screensaverHandler = Handler(Looper.getMainLooper())
    // 屏保 UI 组件
    private var screensaverOverlay: FrameLayout? = null
    private var countdownText: TextView? = null
    private var warningBanner: TextView? = null
    private var dailyLimitWarningBanner: TextView? = null
    private var dailyLimitDimOverlay: View? = null
    // 壁纸播放器（视频素材时使用）
    private var wallpaperPlayer: ExoPlayer? = null
    private var wallpaperPlayerView: PlayerView? = null
    private var wallpaperWebView: WebView? = null
    private var backdropBreathAnim: ObjectAnimator? = null

    // ==================== 首页焦点跟随模糊背景（方案 A） ====================
    private var backdropA: ImageView? = null            // 双层交叉淡入淡出
    private var backdropB: ImageView? = null
    private var backdropIsA = false                     // 当前显示的是 A 还是 B
    private var lastBackdropUrl: String? = null         // 已显示/加载中的海报 URL（去重）
    private var backdropPendingUrl: String? = null      // 防抖窗口内最后聚焦的海报 URL
    private val backdropHandler = Handler(Looper.getMainLooper())
    /** 模糊结果缓存：URL → 120x180 模糊图，LRU 保留 4 张（每张 ~86KB） */
    private val backdropCache = object : LinkedHashMap<String, Bitmap>(8, 1f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > 4
    }
    private val backdropDebounceRunnable = Runnable {
        val url = backdropPendingUrl ?: return@Runnable
        backdropPendingUrl = null
        showHomeBackdrop(url)
    }

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
        object HeartScreen : ScreenState()
        object PlayedScreen : ScreenState()
    }

    /** 返回栈：按遥控器返回键时 pop 栈顶并恢复 */
    private val screenBackStack = ArrayDeque<ScreenState>()

    // Home 页内存缓存：返回首页时秒开渲染（并发刷新数据）
    private var lastHomeCache: HomeData? = null

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
        rootLayout.setBackgroundColor(Ui.c.bg)
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

        // 注册 token 失效广播接收器
        registerReceiver(tokenInvalidReceiver, android.content.IntentFilter("ACTION_TOKEN_INVALID"))

        // Init SSE message center
        initSSE()

        if (App.isLoggedIn()) {
            // 自动登录时也需要拉取护眼屏保配置
            fetchScreensaverConfig()
            fetchWallpaperFiles()
            fetchTodayPlayDuration()
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

        // SSE 长连接：禁用 read timeout，靠服务端 30s 心跳保活。
        // 默认 10s 超时会导致每 30s 断连一次。
        sseClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val baseUrl = RetrofitClient.getBaseUrl()
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val url = "${normalizedBase}v1/api/message/sse?token=${java.net.URLEncoder.encode(token, "UTF-8")}"

        val request = Request.Builder().url(url).build()

        val factory = EventSources.createFactory(sseClient!!)
        sseEventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: okhttp3.Response) {
                android.util.Log.d("OneList", "SSE connected")
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
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
                // 应用层延迟重连：OkHttp EventSource 内部重试可能不够可靠，
                // 5 秒后尝试重建连接。onDestroy 会设 isDestroying 阻止僵尸重连。
                Handler(Looper.getMainLooper()).postDelayed({
                    reconnectSSE()
                }, 5000)
            }

            override fun onClosed(eventSource: EventSource) {
                android.util.Log.d("OneList", "SSE closed")
            }
        })
    }

    private fun reconnectSSE() {
        if (isDestroying) return
        // 清理旧连接
        try { sseEventSource?.cancel() } catch (_: Exception) {}
        sseEventSource = null
        // 重建连接
        initSSE()
    }
    
    private fun showMessage(msg: Message) {
        runOnUiThread {
            if (isDestroying) return@runOnUiThread
            // 统一使用卡片弹窗样式，普通通知 4 秒自动关闭，强制通知需确认
            showNotificationDialog(msg, autoDismiss = msg.priority != "forced")
            // 自动标记为已读
            markMessageAsRead(msg.id)
        }
    }

    private fun showNotificationDialog(msg: Message, autoDismiss: Boolean) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(Ui.dp(40), Ui.dp(30), Ui.dp(40), Ui.dp(30))
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.7).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            background = GradientDrawable().apply {
                setColor(Ui.c.cardAlt)
                cornerRadius = Ui.dp(16).toFloat()
            }
        }

        if (autoDismiss) {
            // 普通通知：半透明小卡片，无按钮，不遮挡播放画面
            layout.setBackgroundColor(Color.TRANSPARENT)
            card.layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.45).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            card.background = GradientDrawable().apply {
                setColor(Ui.c.cardGlass) // 半透明底色
                cornerRadius = Ui.dp(12).toFloat()
            }
            card.setPadding(Ui.dp(24), Ui.dp(20), Ui.dp(24), Ui.dp(20))

            val title = TextView(this).apply {
                text = "消息通知"
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                gravity = Gravity.CENTER
            }
            card.addView(title)

            val sender = TextView(this).apply {
                text = msg.senderName?.takeIf { it.isNotBlank() } ?: ""
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(13f))
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(4), 0, 0)
                visibility = if (text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            }
            card.addView(sender)

            val content = TextView(this).apply {
                text = msg.content
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(8), 0, 0)
            }
            card.addView(content)
        } else {
            // 强制通知：全遮罩 + 大卡片 + 确认按钮
            layout.setBackgroundColor(Ui.c.scrim)

            val icon = TextView(this).apply {
                text = "📩"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(48f))
                gravity = Gravity.CENTER
            }
            card.addView(icon)

            val title = TextView(this).apply {
                text = "重要通知"
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(22f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(12), 0, Ui.dp(8))
            }
            card.addView(title)

            val sender = TextView(this).apply {
                text = msg.senderName?.takeIf { it.isNotBlank() } ?: ""
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, Ui.dp(8))
                visibility = if (text.isNotBlank()) android.view.View.VISIBLE else android.view.View.GONE
            }
            card.addView(sender)

            val content = TextView(this).apply {
                text = msg.content
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                gravity = Gravity.CENTER
                setPadding(Ui.dp(24), 0, Ui.dp(24), 0)
            }
            card.addView(content)

            val confirmBtn = Button(this).apply {
            stateListAnimator = null
                text = "我知道了"
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setPadding(Ui.dp(36), Ui.dp(8), Ui.dp(36), Ui.dp(8))
                isFocusable = true
                isClickable = true
                applyFocusGlow()
                setOnClickListener { dialog.dismiss() }
            }
            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(20) }
            confirmBtn.layoutParams = btnParams
            card.addView(confirmBtn)
        }

        layout.addView(card)
        dialog.setContentView(layout)
        // 普通通知：去除 dialog 窗口背景，避免灰色遮罩挡住背后内容
        if (autoDismiss) {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        // 拦截 BACK 键：防止 Activity 的 rootLayout key listener 触发 navigateBack()
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                if (autoDismiss) {
                    // 普通通知：BACK 可关闭
                    try { dialog.dismiss() } catch (_: Exception) {}
                }
                // 强制通知：不处理 BACK，必须点确认
                true
            } else false
        }
        dialog.show()

        // 普通通知自动关闭
        if (autoDismiss) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isDestroying) {
                    try { dialog.dismiss() } catch (_: Exception) {}
                }
            }, 4000)
        }
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

        // 开始播放时标记为已播放（与网页端行为一致：打开即标记）
        val dataId = currentVideoDataId
        val dataType = currentVideoDataType
        if (dataId != null && dataType != null && lastPlayedToggleId != dataId) {
            lastPlayedToggleId = dataId
            try {
                RetrofitClient.getService().togglePlayed(HeartToggleRequest(dataType, dataId)).enqueue(object : Callback<ApiResponse<Any>> {
                    override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {
                        val body = response.body()
                        if (body != null && body.code == 200) {
                            android.util.Log.d("OneList", "Played toggle OK: type=$dataType id=$dataId")
                        } else {
                            android.util.Log.w("OneList", "Played toggle failed: code=${body?.code} msg=${body?.msg}")
                        }
                    }
                    override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                        android.util.Log.e("OneList", "Played toggle error: ${t.message}", t)
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("OneList", "Played toggle exception: ${e.message}", e)
            }
        }

        lastHeartbeatPosition = player.currentPosition
        lastHeartbeatRealTime = System.currentTimeMillis()
        var lastTickPlayerReady = false // 上一次心跳时播放器是否处于可播放状态
        
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor()
        heartbeatExecutor?.scheduleAtFixedRate({
            // ExoPlayer 强制要求在主线程访问其属性，否则抛 IllegalStateException
            runOnUiThread {
                try {
                    val currentPosition = player.currentPosition
                    val now = System.currentTimeMillis()
                    val realElapsedMs = now - lastHeartbeatRealTime
                    val isPlayerReady = player.playWhenReady &&
                        player.playbackState == com.google.android.exoplayer2.Player.STATE_READY

                    // 如果上次心跳时播放器未就绪（缓冲/加载），重置基准，避免把缓冲等待时间算进去
                    if (!lastTickPlayerReady) {
                        lastHeartbeatPosition = currentPosition
                        lastHeartbeatRealTime = now
                        lastTickPlayerReady = isPlayerReady
                        android.util.Log.d("OneList", "Heartbeat skip (player not ready last tick): state=${player.playbackState} playWhenReady=${player.playWhenReady}")
                        return@runOnUiThread
                    }
                    lastTickPlayerReady = isPlayerReady

                    // 位置差值（可能被快进放大）与真实经过时间取较小值，防止快进虚报时长
                    val positionDiffMs = (currentPosition - lastHeartbeatPosition).coerceAtLeast(0)
                    val effectiveMs = minOf(positionDiffMs, realElapsedMs + 2000) // +2s 容忍调度抖动
                    val durationSeconds = (effectiveMs / 1000).toInt()
                    val positionSeconds = (currentPosition / 1000).toInt()
                    val totalDurationSeconds = (player.duration / 1000).toInt()

                    android.util.Log.d("OneList", "Heartbeat tick: posDiff=${positionDiffMs}ms realElapsed=${realElapsedMs}ms effective=${effectiveMs}ms duration=${durationSeconds}s pos=${positionSeconds}s total=${totalDurationSeconds}s ready=$isPlayerReady")

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
                                    android.util.Log.d("OneList", "Heartbeat OK: duration=${durationSeconds}s pos=${positionSeconds}s")
                                } else {
                                    android.util.Log.w("OneList", "Heartbeat rejected: HTTP ${response.code()} code=${body?.code} msg=${body?.msg} duration=${durationSeconds}s")
                                }
                            }
                            override fun onFailure(call: Call<ApiResponse<PlayHistory>>, t: Throwable) {
                                android.util.Log.e("OneList", "Heartbeat network failed: ${t.message} duration=${durationSeconds}s", t)
                            }
                        })
                    }

                    lastHeartbeatPosition = currentPosition
                    lastHeartbeatRealTime = now
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
        lastPlayedToggleId = null // 离开播放器时重置，下次播放可重新标记
        android.util.Log.d("OneList", "Heartbeat stopped")
    }

    // ==================== SCREEN MANAGEMENT ====================

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        rootLayout.removeAllViews()
        stopBackdropRotation()
        stopHeartbeat()
        stopScreensaverTracker()
        cleanupScreensaver()
        player?.release()
        player = null

        when (screen) {
            Screen.LOGIN -> showLogin()
            Screen.HOME -> showHome()
            Screen.LIST -> showList()
            Screen.DETAIL -> {} // showDetail is called with params
            Screen.SEARCH -> showSearch()
            Screen.PLAYER -> {} // showPlayer is called with params
            Screen.HEART -> showHeartPage()
            Screen.PLAYED -> showPlayedPage()
        }
    }

    // ==================== LOGIN SCREEN ====================

    private fun showLogin() {
        currentScreen = Screen.LOGIN
        screenBackStack.clear()
        player?.release(); player = null
        rootLayout.removeAllViews()
        stopBackdropRotation()

        val ctx = this
        val scroll = ScrollView(this)
        scroll.fillParent()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.dp(40), Ui.dp(80), Ui.dp(40), Ui.dp(40))
        }

        // Title
        val title = TextView(this).apply {
            text = "Onelist TV"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Ui.c.text1)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        layout.addView(title, lp(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = Ui.dp(40)
        })

        // Server URL input
        val serverLabel = label("服务器地址")
        layout.addView(serverLabel)
        val serverInput = EditText(this).apply {
            hint = "http://192.168.1.100:8080"
            setTextColor(Ui.c.text1)
            setHintTextColor(Ui.c.text3)
            setBackgroundColor(Ui.c.card)
            setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12))
            isSingleLine = true
            val saved = App.serverUrl
            if (saved != null) setText(saved)
            showSoftInputOnFocus = false
            applyEditTextFocus()
        }
        layout.addView(serverInput, lp().apply { bottomMargin = Ui.dp(20) })

        // Username input
        val userLabel = label("用户名")
        layout.addView(userLabel)
        val userInput = EditText(this).apply {
            setTextColor(Ui.c.text1)
            setHintTextColor(Ui.c.text3)
            setBackgroundColor(Ui.c.card)
            setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12))
            isSingleLine = true
            val saved = App.username
            if (saved != null) setText(saved)
            showSoftInputOnFocus = false
            applyEditTextFocus()
        }
        layout.addView(userInput, lp().apply { bottomMargin = Ui.dp(20) })

        // Password input
        val passLabel = label("密码")
        layout.addView(passLabel)
        val passInput = EditText(this).apply {
            setTextColor(Ui.c.text1)
            setHintTextColor(Ui.c.text3)
            setBackgroundColor(Ui.c.card)
            setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12))
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            showSoftInputOnFocus = false
            applyEditTextFocus()
        }
        layout.addView(passInput, lp().apply { bottomMargin = Ui.dp(30) })

        // Login button
        val loginBtn = Button(this).apply {
            stateListAnimator = null
            text = "登 录"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(Ui.dp(40), Ui.dp(14), Ui.dp(40), Ui.dp(14))
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
            width = Ui.dp(240)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // D-pad 导航：方向键切换焦点，OK/Enter 弹出键盘
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        serverInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { userInput.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { loginBtn.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        imm.showSoftInput(serverInput, InputMethodManager.SHOW_IMPLICIT); true
                    }
                    else -> false
                }
            } else false
        }
        userInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { passInput.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { serverInput.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        imm.showSoftInput(userInput, InputMethodManager.SHOW_IMPLICIT); true
                    }
                    else -> false
                }
            } else false
        }
        passInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { loginBtn.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { userInput.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        imm.showSoftInput(passInput, InputMethodManager.SHOW_IMPLICIT); true
                    }
                    else -> false
                }
            } else false
        }

        // Status text
        val statusText = TextView(this).apply {
            setTextColor(Ui.c.text3)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(20), 0, 0)
            visibility = View.GONE
        }
        layout.addView(statusText, lp().apply {
            width = LinearLayout.LayoutParams.MATCH_PARENT
        })

        scroll.addView(layout)
        rootLayout.addView(scroll)
        rootLayout.disableClipTree()
        serverInput.post { serverInput.requestFocus() }
    }

    private fun doLogin(username: String, password: String, btn: Button) {
        btn.isEnabled = false
        btn.text = "登录中..."
        try {
            val call = RetrofitClient.getService().login(LoginRequest(username, password))
            call.enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    val body = response.body()

                    if (body != null && body.code == 200 && body.token != null) {
                        App.token = body.token
                        App.userId = body.user?.id
                        App.username = username
                        toast("登录成功")
                        // 登录成功后初始化 SSE 消息连接（首次安装时 onCreate 中 initSSE 因 token 为空会跳过）
                        initSSE()
                        // 拉取护眼屏保配置、壁纸列表、今日播放时长
                        fetchScreensaverConfig()
                        fetchWallpaperFiles()
                        fetchTodayPlayDuration()
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
        stopBackdropRotation()

        val scroll = ScrollView(this).apply {
            clipChildren = false
            clipToPadding = false
        }
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(20), 0, Ui.dp(40))
            clipChildren = false
            clipToPadding = false
        }

        // Top bar
        val topBar = buildTopBar("悠悠TV", showSearch = true, showLogout = true, showUserPages = true, transparentBg = true)
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
            isFocusable = false
        }
        loadingContainer.addView(progressBar)

        val loadingText = TextView(this).apply {
            text = "加载中..."
            setTextColor(Ui.c.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            setPadding(Ui.dp(40), Ui.dp(24), Ui.dp(40), Ui.dp(24))
            // 错误信息会写入这里，统一卡片样式
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(12).toFloat()
                setColor(Ui.c.card)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                topMargin = Ui.dp(60)
            }
        }
        loadingContainer.addView(loadingText)

        layout.addView(loadingContainer)

        scroll.addView(layout)

        // 焦点跟随模糊背景层：双 ImageView 交替淡入淡出 + 暗化层，垫在内容底下
        val frame = FrameLayout(this)
        val backA = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        val backB = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0f
        }
        frame.addView(backA, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(backB, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(View(this).apply { setBackgroundColor(Ui.c.bgGlass) }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(scroll, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.addView(frame, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        rootLayout.disableClipTree()
        // 页面重建：重置背景状态
        backdropA = backA; backdropB = backB
        backdropIsA = false
        lastBackdropUrl = null
        backdropPendingUrl = null
        backdropHandler.removeCallbacks(backdropDebounceRunnable)

        // 优先走内存缓存：返回首页时「秒开」，同时并发刷新
        val cache = lastHomeCache
        if (cache != null) {
            loadingContainer.visibility = View.GONE
            renderHomeData(layout, cache, onlyPriorityRows = true)
            // 下一帧再补画媒体库行（避免一次布局做太多 addView）
            scroll.post { renderRemainingGalleries(layout, cache) }
        }

        // Fetch home data（无论是否命中缓存，都并发拉最新）
        try {
            val service = RetrofitClient.getService()

            service.getHome().enqueue(object : Callback<ApiResponse<HomeData>> {
                override fun onResponse(call: Call<ApiResponse<HomeData>>, response: Response<ApiResponse<HomeData>>) {
                    val body = response.body()
                    if (body == null) {
                        val rawError = try { response.errorBody()?.string() } catch (e: Exception) { null }
                        android.util.Log.e("OneList", "Home API body null! HTTP ${response.code()}, errorBody: $rawError")
                    }

                    if (body != null && body.code == 200 && body.data != null) {
                        val data = body.data!!
                        val isFirstLoad = lastHomeCache == null
                        lastHomeCache = data

                        if (isFirstLoad) {
                            loadingContainer.visibility = View.GONE
                            // 首次加载：先画优先级高的两行（最新电影/电视）→ 首屏立即出卡片
                            renderHomeData(layout, data, onlyPriorityRows = true)
                            // 下一帧再补画各个媒体库行（分摊到两次 onDraw，避免卡顿）
                            scroll.post { renderRemainingGalleries(layout, data) }
                        } else {
                            // 刷新：完整重渲染一次（与缓存不同的媒体库顺序/内容才会更新）
                            // 先去掉 topBar 之后的所有行
                            var childIdx = layout.childCount - 1
                            while (childIdx >= 1) { // index 0 是 topBar
                                val v = layout.getChildAt(childIdx)
                                if (v !== loadingContainer) layout.removeViewAt(childIdx)
                                childIdx--
                            }
                            loadingContainer.visibility = View.GONE
                            renderHomeData(layout, data, onlyPriorityRows = true)
                            scroll.post { renderRemainingGalleries(layout, data) }
                        }
                    } else {
                        // 失败但有缓存：不显示错误（静默沿用旧缓存渲染，用户无感）
                        if (lastHomeCache == null) {
                            val httpCode = response.code()
                            android.util.Log.e("OneList", "Home API failed: HTTP $httpCode, body=$body")
                            val errorMsg = when {
                                httpCode == 401 || body?.code == 403 -> "登录已失效，请重新登录"
                                body?.code == 201 -> "请先登录"
                                httpCode == 500 -> "服务器错误，请稍后重试"
                                else -> "加载失败 (HTTP $httpCode)"
                            }
                            loadingText.text = errorMsg
                        }
                    }
                }
                override fun onFailure(call: Call<ApiResponse<HomeData>>, t: Throwable) {
                    if (lastHomeCache == null) {
                        android.util.Log.e("OneList", "API failure: ${t.message}", t)
                        loadingText.text = "连接失败: ${t.message}"
                    }
                }
            })
        } catch (e: Exception) {
            if (lastHomeCache == null) {
                android.util.Log.e("OneList", "Exception: ${e.message}", e)
                loadingText.text = "错误: ${e.message}"
            }
        }
    }

    /**
     * @param onlyPriorityRows true=只渲染「最新电影+最新电视」两行（返回首页/首次加载先让这两行出来，
     *                         下一帧再渲染媒体库行）；false=全量渲染。
     */
    private fun renderHomeData(parent: LinearLayout, data: HomeData, onlyPriorityRows: Boolean) {
        val ctx = this

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

        if (onlyPriorityRows) return

        renderRemainingGalleries(parent, data)
    }

    /** 重载：保持兼容（旧调用点不传 onlyPriorityRows） */
    private fun renderHomeData(parent: LinearLayout, data: HomeData) {
        renderHomeData(parent, data, onlyPriorityRows = false)
    }

    /** 媒体库行的单独渲染函数，便于 post 到下一帧执行，分摊布局压力 */
    private fun renderRemainingGalleries(parent: LinearLayout, data: HomeData) {
        if (data.galleries == null) return
        val gson = Gson()
        for (gallery in data.galleries) {
            val jsonElement = data.galleryItems?.get(gallery.galleryUid)
            val items: List<GalleryItem> = if (jsonElement != null && jsonElement.isJsonArray) {
                jsonElement.asJsonArray.map { gson.fromJson(it, GalleryItem::class.java) }
            } else emptyList()

            val galleryType = when {
                gallery.galleryType == "tv" -> "tv"
                gallery.galleryType == "movie" -> "movie"
                gallery.isTv == true -> "tv"
                else -> "movie"
            }
            if (items.isNotEmpty()) {
                val row = buildContentRow(gallery.title ?: "媒体库", galleryType, gallery.galleryUid)
                parent.addView(row)
                val mappedItems = items.map { item ->
                    if (item.title != null) {
                        Movie(id = item.id, title = item.title, posterPath = item.posterPath, customPoster = item.customPoster)
                    } else {
                        Tv(id = item.id, name = item.name, posterPath = item.posterPath, customPoster = item.customPoster)
                    } as Any
                }
                val recyclerView = buildHorizontalCardList(mappedItems, "mixed")
                parent.addView(recyclerView)
            }
        }
    }

    private fun buildContentRow(title: String, type: String, galleryId: String?): LinearLayout {
        val ctx = this
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(24), Ui.dp(16), Ui.dp(24), Ui.dp(8))
            clipChildren = true  // 标题行按钮不允许越界绘制，避免色块残留
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(20f))
            setTypeface(null, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8))
            // 预建 StateListDrawable：focused=主题色填充，normal=透明；不再在 onFocusChange 中 new
            val focused = GradientDrawable().apply { setColor(Ui.c.accent) }
            val normal = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
            // 不做 scale，避免 5.1 越界裁剪残留色块
            setOnFocusChangeListener(null)
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
                setTextColor(Ui.c.text3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
                setPadding(Ui.dp(12), Ui.dp(4), Ui.dp(4), Ui.dp(4))
            }
            row.addView(more)
        }

        return row
    }

    private fun buildHorizontalCardList(items: List<Any>, type: String): FrameLayout {
        val adapter = CardAdapter(items, type, { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        }, ::updateHomeBackdrop)
        val cardGap = Ui.dp(12)          // 卡片之间 12dp 水平间距（放大 1.08 倍不会再相互遮挡）
        val containerExtraH = Ui.dp(20)   // 多给 20dp 垂直方向余量（标题 + 放大不会被上下边缘裁剪）

        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(280) + containerExtraH
            )
            clipChildren = false
            clipToPadding = false
        }

        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
            setPadding(Ui.dp(20), Ui.dp(8), Ui.dp(20), Ui.dp(8))
            clipToPadding = false
            clipChildren = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            // 水平卡片：item 之间均匀间距
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(
                    outRect: android.graphics.Rect, view: View, parent: RecyclerView, state: RecyclerView.State
                ) {
                    val pos = parent.getChildAdapterPosition(view)
                    if (pos == RecyclerView.NO_POSITION) return
                    outRect.left = if (pos == 0) 0 else cardGap / 2
                    outRect.right = cardGap / 2
                }
            })
            // 限制 prefetch：首屏只预取 6 个（一行约 6 张），避免一进入首页所有行同时 bind 20+ 卡片
            val lm = this.layoutManager as? LinearLayoutManager
            lm?.setInitialPrefetchItemCount(6)
            setItemViewCacheSize(6)
        }
        container.addView(recyclerView)

        return container
    }

    // ==================== USER COLLECTION PAGES (最爱 / 已播放) ====================

    private enum class UserPageType { HEART, PLAYED }

    // 最爱页状态
    private var heartDataType = "movie"
    private var heartPage = 1
    private var heartTotal = 0
    private var heartItems = listOf<Any>()

    // 已播放页状态
    private var playedDataType = "movie"
    private var playedPage = 1
    private var playedTotal = 0
    private var playedItems = listOf<Any>()

    private fun showHeartPage() {
        pushCurrentToBackStack()
        showUserCollectionPage(UserPageType.HEART)
    }

    private fun showPlayedPage() {
        pushCurrentToBackStack()
        showUserCollectionPage(UserPageType.PLAYED)
    }

    private fun showUserCollectionPage(pageType: UserPageType) {
        currentScreen = if (pageType == UserPageType.HEART) Screen.HEART else Screen.PLAYED
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        stopBackdropRotation()

        val isHeart = pageType == UserPageType.HEART
        val title = if (isHeart) "我的最爱" else "已播放"
        val currentDataType = if (isHeart) heartDataType else playedDataType
        val currentPageNum = if (isHeart) heartPage else playedPage
        val total = if (isHeart) heartTotal else playedTotal
        val items = if (isHeart) heartItems else playedItems

        val scroll = ScrollView(this).apply { fillParent() }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(20), 0, Ui.dp(40))
        }

        val topBar = buildTopBar(title, showSearch = true, showLogout = false)
        layout.addView(topBar)

        // Type tabs: 电影 / 剧集
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(24), 0, Ui.dp(24), Ui.dp(12))
        }
        val movieTab = TextView(this).apply {
            text = "电影"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8))
            isClickable = true
            isFocusable = true
            // 选中=accent 底 / 未选=card 底；焦点=白描边示焦点（统一形态）
            fun refresh(hasFocus: Boolean = isFocused) {
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(16).toFloat()
                    setColor(if (currentDataType == "movie") Ui.c.accent else Ui.c.card)
                    if (hasFocus) setStroke(Ui.dp(2), Ui.c.text1)
                }
                setTextColor(if (hasFocus || currentDataType == "movie") Ui.c.text1 else Ui.c.text3)
            }
            refresh()
            setOnFocusChangeListener { _, hasFocus -> refresh(hasFocus) }
            setOnClickListener {
                if (isHeart) { heartDataType = "movie"; heartPage = 1 } else { playedDataType = "movie"; playedPage = 1 }
                showUserCollectionPage(pageType)
            }
        }
        tabsLayout.addView(movieTab)

        val tvTab = TextView(this).apply {
            text = "剧集"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8))
            isClickable = true
            isFocusable = true
            fun refresh(hasFocus: Boolean = isFocused) {
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(16).toFloat()
                    setColor(if (currentDataType == "tv") Ui.c.accent else Ui.c.card)
                    if (hasFocus) setStroke(Ui.dp(2), Ui.c.text1)
                }
                setTextColor(if (hasFocus || currentDataType == "tv") Ui.c.text1 else Ui.c.text3)
            }
            refresh()
            setOnFocusChangeListener { _, hasFocus -> refresh(hasFocus) }
            setOnClickListener {
                if (isHeart) { heartDataType = "tv"; heartPage = 1 } else { playedDataType = "tv"; playedPage = 1 }
                showUserCollectionPage(pageType)
            }
        }
        tabsLayout.addView(tvTab)
        layout.addView(tabsLayout)

        // Content area (created first so pagination buttons can reference these views)
        val contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
        }

        val loadingContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        loadingContainer.addView(ProgressBar(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            isFocusable = false
        })
        contentContainer.addView(loadingContainer)

        val emptyView = TextView(this).apply {
            text = if (isHeart) "暂无最爱内容" else "暂无播放记录"
            setTextColor(Ui.c.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            gravity = Gravity.CENTER
            visibility = View.GONE
            // 空态卡片样式（统一观感）
            setPadding(Ui.dp(40), Ui.dp(24), Ui.dp(40), Ui.dp(24))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(12).toFloat()
                setColor(Ui.c.card)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        contentContainer.addView(emptyView)

        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(Ui.dp(16), 0, Ui.dp(16), 0)
            visibility = View.GONE
        }
        contentContainer.addView(gridContainer)

        // Page info + pagination (references content views above)
        val pageLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(8), 0, Ui.dp(12))
        }
        val pageInfoText = TextView(this).apply {
            text = "$total 条记录"
            setTextColor(Ui.c.accent)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            setPadding(Ui.dp(16), 0, Ui.dp(16), 0)
        }
        pageLayout.addView(pageInfoText)

        val prevBtn = Button(this).apply {
            stateListAnimator = null
            text = "上一页"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
            setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(4))
            isFocusable = true; isClickable = true
            applyFocusGlow(Ui.c.card)
            setOnClickListener {
                if (isHeart) { if (heartPage > 1) heartPage-- } else { if (playedPage > 1) playedPage-- }
                fetchUserCollectionData(pageType, layout, gridContainer, loadingContainer, emptyView, pageInfoText)
            }
        }
        pageLayout.addView(prevBtn)

        val nextBtn = Button(this).apply {
            stateListAnimator = null
            text = "下一页"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
            setPadding(Ui.dp(16), Ui.dp(4), Ui.dp(16), Ui.dp(4))
            isFocusable = true; isClickable = true
            applyFocusGlow(Ui.c.card)
            setOnClickListener {
                val maxPage = if (total > 0) Math.ceil(total / 30.0).toInt() else 1
                if (isHeart) { if (heartPage < maxPage) heartPage++ } else { if (playedPage < maxPage) playedPage++ }
                fetchUserCollectionData(pageType, layout, gridContainer, loadingContainer, emptyView, pageInfoText)
            }
        }
        pageLayout.addView(nextBtn)

        // Add to layout in visual order: pagination first, then content
        layout.addView(pageLayout)
        layout.addView(contentContainer)
        scroll.addView(layout)
        rootLayout.addView(scroll)
        rootLayout.disableClipTree()

        // Update page info text
        val startIdx = (currentPageNum - 1) * 30 + 1
        val endIdx = minOf(currentPageNum * 30, total)
        pageInfoText.text = if (total > 0) "$total 的 $startIdx-$endIdx" else "暂无数据"

        // Fetch data
        fetchUserCollectionData(pageType, layout, gridContainer, loadingContainer, emptyView, pageInfoText)
    }

    private fun fetchUserCollectionData(
        pageType: UserPageType,
        rootLayout: LinearLayout,
        gridContainer: LinearLayout,
        loadingContainer: FrameLayout,
        emptyView: TextView,
        pageInfoText: TextView
    ) {
        val isHeart = pageType == UserPageType.HEART
        val dataType = if (isHeart) heartDataType else playedDataType
        val page = if (isHeart) heartPage else playedPage
        val isMo = resources.displayMetrics.widthPixels < 800
        val pageSize = if (isMo) 12 else 30

        val service = RetrofitClient.getService()
        val call = if (isHeart) {
            service.getHeartList(dataType, page, pageSize)
        } else {
            service.getPlayedDataList(dataType, page, pageSize)
        }

        loadingContainer.visibility = View.VISIBLE
        gridContainer.visibility = View.GONE
        emptyView.visibility = View.GONE

        call.enqueue(object : Callback<ApiListResponse<com.google.gson.JsonElement>> {
            override fun onResponse(call: Call<ApiListResponse<com.google.gson.JsonElement>>, response: Response<ApiListResponse<com.google.gson.JsonElement>>) {
                val body = response.body()
                loadingContainer.visibility = View.GONE

                if (body != null && body.code == 200 && body.data != null) {
                    val gson = Gson()
                    val parsedItems = body.data!!.mapNotNull { jsonElement ->
                        val obj = jsonElement.asJsonObject
                        when {
                            obj.has("title") && !obj.get("title").isJsonNull -> gson.fromJson(jsonElement, Movie::class.java) as Any
                            obj.has("name") && !obj.get("name").isJsonNull -> gson.fromJson(jsonElement, Tv::class.java) as Any
                            else -> null
                        }
                    }
                    val totalNum = body.num ?: 0

                    if (isHeart) { heartItems = parsedItems; heartTotal = totalNum } else { playedItems = parsedItems; playedTotal = totalNum }

                    // Update page info
                    val startIdx = (page - 1) * pageSize + 1
                    val endIdx = minOf(page * pageSize, totalNum)
                    pageInfoText.text = if (totalNum > 0) "$totalNum 的 $startIdx-$endIdx" else "暂无数据"

                    if (parsedItems.isEmpty()) {
                        emptyView.visibility = View.VISIBLE
                    } else {
                        gridContainer.removeAllViews()
                        val adapter = CardAdapter(parsedItems, if (dataType == "tv") "tv" else "movie", onClick = { item ->
                            when (item) {
                                is Movie -> showMovieDetail(item)
                                is Tv -> showTvDetail(item)
                            }
                        })
                        val gridColumns = calculateGridColumns()
                        val recyclerView = RecyclerView(this@MainActivity).apply {
                            layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
                            addItemDecoration(CardAdapter.GridSpacingItemDecoration(gridColumns, Ui.dp(16), true))
                            this.adapter = adapter
                            setPadding(0, 0, 0, 0)
                            clipToPadding = false
                            clipChildren = false
                        }
                        gridContainer.addView(recyclerView, LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        ))
                        gridContainer.visibility = View.VISIBLE
                        recyclerView.post { recyclerView.requestFocus() }
                    }
                } else {
                    emptyView.visibility = View.VISIBLE
                    if (isHeart) { heartItems = emptyList(); heartTotal = 0 } else { playedItems = emptyList(); playedTotal = 0 }
                    pageInfoText.text = "暂无数据"
                }
            }

            override fun onFailure(call: Call<ApiListResponse<com.google.gson.JsonElement>>, t: Throwable) {
                loadingContainer.visibility = View.GONE
                emptyView.text = "加载失败: ${t.message}"
                emptyView.visibility = View.VISIBLE
                if (isHeart) { heartItems = emptyList(); heartTotal = 0 } else { playedItems = emptyList(); playedTotal = 0 }
                pageInfoText.text = "暂无数据"
            }
        })
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
        stopBackdropRotation()
        detailFromSearch = false

        if (!restoreFromCache) {
            // 首次进入：重置列表状态
            currentPage = 1
            isLoadingMore = false
            // 最新电影/最新电视（galleryId=null）只显示最新30部，不自动加载更多
            hasMorePages = currentGalleryId != null
            listItems.clear()
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Ui.c.bg)
        }

        val topBar = buildTopBar(currentGalleryTitle ?: "浏览", showSearch = true, showLogout = false)
        layout.addView(topBar)

        // Loading indicator with ProgressBar
        val loadingContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
            setBackgroundColor(Ui.c.bg)
        }
        
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            id = android.R.id.progress
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
            visibility = View.VISIBLE
            isFocusable = false
        }
        loadingContainer.addView(progressBar)
        
        val loadingText = TextView(this).apply {
            id = android.R.id.text1
            text = "加载中..."
            setTextColor(Ui.c.text3)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { 
                gravity = Gravity.CENTER
                topMargin = Ui.dp(60)
            }
        }
        loadingContainer.addView(loadingText)
        
        listAdapter = CardAdapter(listItems, currentGalleryType ?: "movie", onClick = { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        })
        val gridColumns = calculateGridColumns()
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
            addItemDecoration(CardAdapter.GridSpacingItemDecoration(gridColumns, Ui.dp(16), true))
            this.adapter = listAdapter
            setPadding(0, 0, 0, 0)
            clipToPadding = false
            isFocusable = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
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

        // 滚动到底部附近时自动加载更多（无按钮，避免 D-pad 焦点问题）
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as GridLayoutManager
                val total = lm.itemCount
                val lastVisible = lm.findLastVisibleItemPosition()
                if (total > 0 && lastVisible >= total - gridColumns * 3) {
                    if (!isLoadingMore && hasMorePages) {
                        currentPage++
                        loadMoreItems(recyclerView)
                    }
                }
            }
        })

        // 当 RecyclerView 自身获得焦点时（说明没有子卡片持有焦点），自动将焦点交给第一个可见卡片
        recyclerView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus && v is RecyclerView) {
                val lm = v.layoutManager as? GridLayoutManager ?: return@OnFocusChangeListener
                val firstPos = lm.findFirstVisibleItemPosition()
                if (firstPos >= 0) {
                    lm.findViewByPosition(firstPos)?.requestFocus()
                }
            }
        }

        rootLayout.addView(layout)
        rootLayout.disableClipTree()

        if (!restoreFromCache) {
            loadListData(recyclerView)
        } else {
            listAdapter?.notifyDataSetChanged()
            recyclerView.visibility = View.VISIBLE
            recyclerView.post { recyclerView.requestFocus() }
            val parent = recyclerView.parent as? FrameLayout
            parent?.findViewById<View>(android.R.id.progress)?.visibility = View.GONE
            parent?.findViewById<TextView>(android.R.id.text1)?.visibility = View.GONE
        }
    }

    private fun loadListData(recyclerView: RecyclerView) {
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

                        // 隐藏加载指示器，显示列表
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.post { recyclerView.requestFocus() }
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

                        // 隐藏加载指示器，显示列表
                        recyclerView.visibility = View.VISIBLE
                        recyclerView.post { recyclerView.requestFocus() }
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

    private fun loadMoreItems(recyclerView: RecyclerView) {
        isLoadingMore = true

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
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val start = listItems.size
                        listItems.addAll(body.data!!)
                        listAdapter?.notifyItemRangeInserted(start, body.data!!.size)
                        hasMorePages = body.data!!.size >= 30
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Tv>>, t: Throwable) {
                    isLoadingMore = false
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
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val start = listItems.size
                        listItems.addAll(body.data!!)
                        currentMovieList = listItems.filterIsInstance<Movie>()
                        listAdapter?.notifyItemRangeInserted(start, body.data!!.size)
                        hasMorePages = body.data!!.size >= 30
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Movie>>, t: Throwable) {
                    isLoadingMore = false
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
        stopBackdropRotation()
        // 清除剧集列表缓存（进入电影详情后不可能还在剧集列表）
        currentSeasonEpisodes = null
        currentSeasonTitle = null
        currentMovie = movie
        currentTv = null

        // 预加载该电影所属 gallery 的完整列表，确保自动连播时能匹配 URL 更新心跳元数据
        if (movie.galleryUid != null) {
            RetrofitClient.getService().getMovieListByGallery(movie.galleryUid!!, 1, 100)
                .enqueue(object : Callback<ApiListResponse<Movie>> {
                    override fun onResponse(call: Call<ApiListResponse<Movie>>, response: Response<ApiListResponse<Movie>>) {
                        val body = response.body()
                        if (body != null && body.code == 200 && body.data != null) {
                            currentMovieList = body.data!!
                            android.util.Log.d("OneList", "Detail: preloaded ${currentMovieList!!.size} movies for gallery '${movie.galleryUid}'")
                        }
                    }
                    override fun onFailure(call: Call<ApiListResponse<Movie>>, t: Throwable) {
                        android.util.Log.w("OneList", "Detail: failed to preload movie list: ${t.message}")
                    }
                })
        }

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(20), 0, Ui.dp(40))
        }

        val topBar = buildTopBar("", showSearch = false, showLogout = false, transparentBg = true)
        layout.addView(topBar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(32), Ui.dp(16), Ui.dp(32), 0)
        }

        val posterUrl = RetrofitClient.posterUrl(movie.posterPath, movie.customPoster, movie.id)
        android.util.Log.d("OneList", "MovieDetail: title='${movie.title}' origTitle='${movie.originalTitle}' posterPath='${movie.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(Ui.dp(180), Ui.dp(270))
            setBackgroundColor(Ui.c.card)
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Ui.c.card)
                    cornerRadius = Ui.dp(4).toFloat()
                }
                val detailReq = Glide.with(this@MainActivity)
                    .load(posterUrl)
                    .override(Ui.dp(180), Ui.dp(270))
                    .placeholder(placeholder)
                    .error(placeholder)
                // custom-image 的 URL 现在带 ?t=SESSION_NONCE：
                //   同启动 → SOURCE 缓存命中；下次启动 → URL 变了，自动拉 picture/ 新内容
                if (posterUrl.contains("/custom-image/", ignoreCase = true)) {
                    // Glide 4.x：SOURCE 更名为 DATA（源字节级缓存）
                    detailReq.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                } else {
                    detailReq.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                }
                detailReq.into(this)
            }
        }
        contentLayout.addView(posterView)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(24), 0, 0, 0)
            // 播放/喜爱按钮聚焦放大时超出本容器左缘，关闭裁剪交给外层 padding 区承载
            clipChildren = false
            // 占满海报右侧剩余宽度，长标题不再被屏幕右缘裁切
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = movie.title ?: movie.originalTitle ?: ""
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(28f))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
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
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setPadding(0, Ui.dp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        if (movie.genres != null && movie.genres.isNotEmpty()) {
            val genreText = movie.genres.joinToString(" / ") { it.name ?: "" }
            val genreView = TextView(this).apply {
                text = genreText
                setTextColor(Ui.c.text3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
                setPadding(0, Ui.dp(8), 0, 0)
            }
            infoLayout.addView(genreView)
        }

        // 按钮行：播放 + 喜爱
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, Ui.dp(20), 0, 0)
            clipChildren = false
        }

        val playBtn = Button(this).apply {
            stateListAnimator = null
            text = "▶  播放"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
            setPadding(Ui.dp(32), Ui.dp(12), Ui.dp(32), Ui.dp(12))
            isFocusable = true
            isClickable = true
            applyFocusGlow(Ui.c.red)
            setOnClickListener {
                if (movie.url != null) {
                    // 设置心跳元数据，使播放统计正常上报
                    currentVideoDataType = "movie"
                    currentVideoDataId = movie.id
                    currentVideoTitle = movie.title
                    currentVideoGalleryUid = movie.galleryUid
                    currentVideoGalleryTitle = currentGalleryTitle
                    android.util.Log.d("OneList", "Movie play: id=${movie.id} title='${movie.title}' galleryUid='${movie.galleryUid}' galleryTitle='$currentGalleryTitle' url='${movie.url}'")
                    val pl = currentMovieList?.filter { it.url != null }?.map { PlayItem(it.url!!, it.galleryUid, it.title) }
                    val idx = pl?.indexOfFirst { it.url == movie.url } ?: 0
                    showPlayer(movie.url!!, movie.galleryUid, pl, if (idx >= 0) idx else 0)
                } else {
                    toast("暂无播放源")
                }
            }
        }
        btnRow.addView(playBtn)

        // 喜爱按钮
        var isHearted = movie.heart == true
        val activeColor = Ui.c.red
        val inactiveColor = Ui.c.cardAlt
        // 同一个 GradientDrawable 实例：点击/失焦只改内部 color/stroke，不再每次 new
        val heartBg = GradientDrawable().apply {
            cornerRadius = Ui.dp(8).toFloat()
            setColor(if (isHearted) activeColor else inactiveColor)
        }
        val heartBtn = Button(this).apply {
            stateListAnimator = null
            text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
            setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(12))
            isFocusable = true
            isClickable = true
            background = heartBg
            setOnFocusChangeListener { v, _ ->
                // 统一焦点形态：1.05 缩放 + accent 描边 + 提到最上层（触发父容器重绘越界区）
                Ui.scaleOnFocus(v)
                heartBg.setStroke(Ui.dp(2), if (v.isFocused) Ui.c.accent else 0)
            }
            setOnClickListener {
                if (movie.id == null) return@setOnClickListener
                isHearted = !isHearted
                text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
                heartBg.setColor(if (isHearted) activeColor else inactiveColor)
                RetrofitClient.getService().toggleHeart(HeartToggleRequest("movie", movie.id)).enqueue(object : Callback<ApiResponse<Any>> {
                    override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {}
                    override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                        // 回滚 UI
                        isHearted = !isHearted
                        runOnUiThread {
                            text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
                            heartBg.setColor(if (isHearted) activeColor else inactiveColor)
                        }
                    }
                })
            }
        }
        val heartLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            leftMargin = Ui.dp(12)
        }
        btnRow.addView(heartBtn, heartLp)

        infoLayout.addView(btnRow)

        contentLayout.addView(infoLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(contentLayout)

        if (movie.desc != null && movie.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(Ui.dp(32), Ui.dp(24), Ui.dp(32), Ui.dp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = movie.desc
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setLineSpacing(0f, 1.4f)
                setPadding(Ui.dp(32), 0, Ui.dp(32), 0)
            }
            layout.addView(descView)
        }

        scroll.addView(layout)

        // 高斯模糊背景容器：封面模糊图（双层呼吸）+ 暗化层 + 内容
        val frame = FrameLayout(this)
        val blurView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val blurSoftView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            alpha = 0f
        }
        frame.addView(blurView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(blurSoftView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(View(this).apply { setBackgroundColor(Ui.c.bgGlass) }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(scroll)
        rootLayout.addView(frame, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        loadBlurBackdrop(posterUrl, blurView, blurSoftView)
        rootLayout.disableClipTree()

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
                                releaseDate = detail.releaseDate, genres = detail.genres, galleryUid = detail.galleryUid,
                                heart = detail.heart
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
        stopBackdropRotation()
        // 进入TV详情后，可能跳到剧集列表，之后返回回来还能正确识别
        currentSeasonEpisodes = null
        currentSeasonTitle = null
        currentMovie = null
        currentTv = tv

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, Ui.dp(20), 0, Ui.dp(40))
        }

        val topBar = buildTopBar("", showSearch = false, showLogout = false, transparentBg = true)
        layout.addView(topBar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(Ui.dp(32), Ui.dp(16), Ui.dp(32), 0)
        }

        val posterUrl = RetrofitClient.posterUrl(tv.posterPath, tv.customPoster, tv.id)
        android.util.Log.d("OneList", "TvDetail: name='${tv.name}' origName='${tv.originalName}' posterPath='${tv.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(Ui.dp(180), Ui.dp(270))
            setBackgroundColor(Ui.c.card)
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Ui.c.card)
                    cornerRadius = Ui.dp(4).toFloat()
                }
                val detailReq = Glide.with(this@MainActivity)
                    .load(posterUrl)
                    .override(Ui.dp(180), Ui.dp(270))
                    .placeholder(placeholder)
                    .error(placeholder)
                // custom-image 的 URL 现在带 ?t=SESSION_NONCE：
                //   同启动 → SOURCE 缓存命中；下次启动 → URL 变了，自动拉 picture/ 新内容
                if (posterUrl.contains("/custom-image/", ignoreCase = true)) {
                    // Glide 4.x：SOURCE 更名为 DATA（源字节级缓存）
                    detailReq.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.DATA)
                } else {
                    detailReq.diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                }
                detailReq.into(this)
            }
        }
        contentLayout.addView(posterView)

        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(24), 0, 0, 0)
            // 喜爱按钮聚焦放大时超出本容器左缘，关闭裁剪交给外层 padding 区承载
            clipChildren = false
            // 占满海报右侧剩余宽度，长标题不再被屏幕右缘裁切
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = tv.name ?: tv.originalName ?: ""
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(28f))
            setTypeface(null, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        infoLayout.addView(titleView)

        val metaText = buildString {
            if (tv.year != null) append(tv.year)
        }
        if (metaText.isNotEmpty()) {
            val metaView = TextView(this).apply {
                text = metaText
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setPadding(0, Ui.dp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        // 喜爱按钮
        var isHearted = tv.heart == true
        val activeColorTv = Ui.c.red
        val inactiveColorTv = Ui.c.cardAlt
        // 同一 GradientDrawable 实例复用，避免每次 new
        val heartBgTv = GradientDrawable().apply {
            cornerRadius = Ui.dp(8).toFloat()
            setColor(if (isHearted) activeColorTv else inactiveColorTv)
        }
        val heartBtn = Button(this).apply {
            stateListAnimator = null
            text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
            setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(12))
            isFocusable = true
            isClickable = true
            background = heartBgTv
            setOnFocusChangeListener { v, _ ->
                // 统一焦点形态：1.05 缩放 + accent 描边 + 提到最上层（触发父容器重绘越界区）
                Ui.scaleOnFocus(v)
                heartBgTv.setStroke(Ui.dp(2), if (v.isFocused) Ui.c.accent else 0)
            }
            setOnClickListener {
                if (tv.id == null) return@setOnClickListener
                isHearted = !isHearted
                text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
                heartBgTv.setColor(if (isHearted) activeColorTv else inactiveColorTv)
                RetrofitClient.getService().toggleHeart(HeartToggleRequest("tv", tv.id)).enqueue(object : Callback<ApiResponse<Any>> {
                    override fun onResponse(call: Call<ApiResponse<Any>>, response: Response<ApiResponse<Any>>) {}
                    override fun onFailure(call: Call<ApiResponse<Any>>, t: Throwable) {
                        isHearted = !isHearted
                        runOnUiThread {
                            text = if (isHearted) "❤ 已喜爱" else "❤ 喜爱"
                            heartBgTv.setColor(if (isHearted) activeColorTv else inactiveColorTv)
                        }
                    }
                })
            }
        }
        infoLayout.addView(heartBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = Ui.dp(16)
        })

        contentLayout.addView(infoLayout)
        layout.addView(contentLayout)

        if (tv.desc != null && tv.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(Ui.dp(32), Ui.dp(24), Ui.dp(32), Ui.dp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = tv.desc
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setLineSpacing(0f, 1.4f)
                setPadding(Ui.dp(32), 0, Ui.dp(32), 0)
            }
            layout.addView(descView)
        }

        val seasonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(32), Ui.dp(24), Ui.dp(32), 0)
            // 季按钮聚焦放大超出行容器边界时不被裁剪
            clipChildren = false
        }
        val seasonsLabel = TextView(this).apply {
            text = "分季"
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        seasonsContainer.addView(seasonsLabel)
        layout.addView(seasonsContainer)

        // 选集网格：默认展开第一季，点季切换；与 Web 端详情页一致
        val episodesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(32), Ui.dp(12), Ui.dp(32), 0)
        }
        layout.addView(episodesContainer)

        scroll.addView(layout)

        // 高斯模糊背景容器：封面模糊图（双层呼吸）+ 暗化层 + 内容
        val frame = FrameLayout(this)
        val blurView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        val blurSoftView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            alpha = 0f
        }
        frame.addView(blurView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(blurSoftView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(View(this).apply { setBackgroundColor(Ui.c.bgGlass) }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(scroll)
        rootLayout.addView(frame, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        loadBlurBackdrop(posterUrl, blurView, blurSoftView)
        rootLayout.disableClipTree()

        heartBtn.post { heartBtn.requestFocus() }

        if (tv.id != null) {
            fetchTvDetail(tv.id.toString(), seasonsContainer, episodesContainer)
        }
    }

    private fun fetchTvDetail(id: String, seasonsContainer: LinearLayout, episodesContainer: LinearLayout) {
        try {
            RetrofitClient.getService().getTvDetail(id).enqueue(object : Callback<TvDetailResponse> {
                override fun onResponse(call: Call<TvDetailResponse>, response: Response<TvDetailResponse>) {
                    val body = response.body()
                    if (body != null && body.code == 200 && body.data != null) {
                        val detail = body.data!!
                        val seasons = detail.allSeasons
                        if (!seasons.isNullOrEmpty()) {
                            // 季按钮横向排列（单季时不占空间）
                            val seasonRow = LinearLayout(this@MainActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                clipChildren = false
                            }
                            for (season in seasons) {
                                val seasonBtn = Button(this@MainActivity).apply {
                                    text = "第 ${season.seasonNumber ?: "?"} 季"
                                    setTextColor(Ui.c.text1)
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                                    setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(8))
                                    isFocusable = true
                                    isClickable = true
                                    applyFocusGlow(Ui.c.card)
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply { marginEnd = Ui.dp(8) }
                                    setOnClickListener {
                                        if (season.id != null) {
                                            loadEpisodesInline(season, episodesContainer)
                                        }
                                    }
                                }
                                seasonRow.addView(seasonBtn)
                            }
                            seasonsContainer.addView(seasonRow)
                            // 默认展开第一季
                            loadEpisodesInline(seasons[0], episodesContainer)
                        } else {
                            seasonsContainer.addView(TextView(this@MainActivity).apply {
                                text = "暂无分季数据"
                                setTextColor(Ui.c.text3)
                                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
                                setPadding(0, Ui.dp(8), 0, 0)
                            })
                        }
                    }
                }
                override fun onFailure(call: Call<TvDetailResponse>, t: Throwable) {
                    toast("加载详情失败")
                }
            })
        } catch (e: Exception) {}
    }

    /** 详情页内嵌加载某季的分集（默认展开第一季，点季按钮切换） */
    private fun loadEpisodesInline(season: Season, container: LinearLayout) {
        if (season.id == null) return
        try {
            RetrofitClient.getService().getSeasonDetail(season.id.toString()).enqueue(object : Callback<ApiResponse<SeasonDetail>> {
                override fun onResponse(call: Call<ApiResponse<SeasonDetail>>, response: Response<ApiResponse<SeasonDetail>>) {
                    val body = response.body()
                    if (body != null && body.code == 200) {
                        renderEpisodesInline(body.data!!.episodes ?: emptyList(), season, container)
                    }
                }
                override fun onFailure(call: Call<ApiResponse<SeasonDetail>>, t: Throwable) {}
            })
        } catch (e: Exception) {}
    }

    /** 渲染内嵌分集网格（两列紧凑卡片，遥控器方向键直接导航） */
    private fun renderEpisodesInline(episodes: List<Episode>, season: Season, container: LinearLayout) {
        container.removeAllViews()
        currentSeasonEpisodes = episodes
        currentSeasonTitle = "第 ${season.seasonNumber ?: 1} 季"
        if (episodes.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "该季暂无分集"
                setTextColor(Ui.c.text3)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(14f))
                setPadding(0, Ui.dp(8), 0, 0)
            })
            return
        }
        val rv = RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 2)
            setNestedScrollingEnabled(false)
            // 焦点放大 1.05 会超出卡片 bounds：关闭裁剪，允许绘制到父容器 padding 区
            clipToPadding = false
            clipChildren = false
            // 焦点进入网格子项逐个移动，而不是把整个网格当一个焦点块
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            // 两列网格加间距，焦点放大时不与相邻卡片粘连
            addItemDecoration(CardAdapter.GridSpacingItemDecoration(2, Ui.dp(8), true))
            adapter = EpisodeAdapter(episodes, { ep -> playEpisodeFromList(ep, episodes) }, currentVideoUrl)
        }
        container.addView(rv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
    }

    /** TV 播放统计/显示标题：细化到集，格式「剧名 S1E3」；拿不到集号时回退单集名 */
    private fun tvStatTitle(tvName: String?, ep: Episode): String {
        val base = tvName ?: ""
        val season = ep.seasonNumber
            ?: Regex("\\d+").find(currentSeasonTitle ?: "")?.value?.toIntOrNull()
        val epNum = ep.episodeNumber
        val tag = when {
            season != null && epNum != null -> " S${season}E$epNum"
            epNum != null -> " E$epNum"
            else -> ""
        }
        return if (tag.isEmpty()) (ep.title ?: base).ifEmpty { "未知" } else "$base$tag"
    }

    /** 分集点击播放（内嵌网格与选集页共用）；Episode 无 gallery_uid 时回退剧级 gallery_uid */
    private fun playEpisodeFromList(ep: Episode, episodes: List<Episode>) {
        if (ep.url == null) {
            toast("暂无播放源")
            return
        }
        currentVideoDataType = "tv"
        currentVideoDataId = currentTv?.id
        currentVideoTitle = tvStatTitle(currentTv?.name, ep)
        currentVideoGalleryUid = ep.galleryUid ?: currentTv?.galleryUid
        currentVideoGalleryTitle = currentGalleryTitle
        val pl = episodes.filter { it.url != null }.map {
            PlayItem(it.url!!, it.galleryUid ?: currentTv?.galleryUid, tvStatTitle(currentTv?.name, it))
        }
        val idx = episodes.indexOf(ep)
        showPlayer(ep.url!!, currentVideoGalleryUid, pl, if (idx >= 0) idx else 0)
    }

    private fun showEpisodeList(episodes: List<Episode>, seasonTitle: String, restoreFromCache: Boolean = false) {
        if (!restoreFromCache) pushCurrentToBackStack()
        currentScreen = Screen.DETAIL
        stopHeartbeat()
        player?.release(); player = null
        rootLayout.removeAllViews()
        stopBackdropRotation()
        currentSeasonEpisodes = episodes
        currentSeasonTitle = seasonTitle

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Ui.c.bg)
        }

        val topBar = buildTopBar(seasonTitle, showSearch = false, showLogout = false)
        layout.addView(topBar)

        // 用 RecyclerView 替代 ScrollView + for 循环 addView：ViewHolder 复用，
        // 避免 50+ 集的长篇动漫/美剧首次进入时创建数百个 View
        val recyclerView = RecyclerView(this).apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@MainActivity, 2)
            clipToPadding = false
            // 焦点放大部分允许超出自身 bounds 绘制（外层容器有 24dp padding 提供空间）
            clipChildren = false
            setPadding(Ui.dp(24), Ui.dp(8), Ui.dp(24), Ui.dp(16))
            (layoutManager as androidx.recyclerview.widget.GridLayoutManager).setInitialPrefetchItemCount(10)
            setItemViewCacheSize(12)
            addItemDecoration(CardAdapter.GridSpacingItemDecoration(2, Ui.dp(8), true))
            adapter = EpisodeAdapter(episodes, { ep -> playEpisodeFromList(ep, episodes) }, currentVideoUrl)
        }

        layout.addView(recyclerView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })

        rootLayout.addView(layout)
        rootLayout.disableClipTree()
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
        stopBackdropRotation()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Ui.c.bg)
        }

        val topBar = buildTopBar("搜索", showSearch = false, showLogout = false)
        layout.addView(topBar)

        // Search input row
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(8))
        }

        val searchInput = EditText(this).apply {
            hint = "输入关键词..."
            setTextColor(Ui.c.text1)
            setHintTextColor(Ui.c.text3)
            setBackgroundColor(Ui.c.card)
            setPadding(Ui.dp(16), Ui.dp(12), Ui.dp(16), Ui.dp(12))
            isSingleLine = true
            showSoftInputOnFocus = false
            applyEditTextFocus()
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            layoutParams = lp
        }
        searchRow.addView(searchInput)

        val searchBtn = Button(this).apply {
            stateListAnimator = null
            text = "搜索"
            setTextColor(Ui.c.text1)
            isFocusable = true
            isClickable = true
            applyFocusGlow()
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = Ui.dp(12)
            layoutParams = lp
            setOnClickListener {
                val q = searchInput.text.toString().trim()
                if (q.isNotEmpty()) doSearch(q, layout)
            }
        }
        searchRow.addView(searchBtn)
        layout.addView(searchRow)

        // 搜索框 D-pad 导航
        val immSearch = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        searchInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_DOWN -> { searchBtn.requestFocus(); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        immSearch.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT); true
                    }
                    else -> false
                }
            } else false
        }

        // Results container
        val resultsScroll = ScrollView(this)
        val resultsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(24), Ui.dp(8), Ui.dp(24), Ui.dp(16))
            id = View.generateViewId()
        }
        resultsScroll.addView(resultsLayout)
        layout.addView(resultsScroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0
        ).apply { weight = 1f })

        rootLayout.addView(layout)
        rootLayout.disableClipTree()

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
            setTextColor(Ui.c.text3)
            gravity = Gravity.CENTER
            setPadding(0, Ui.dp(40), 0, Ui.dp(40))
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
                setPadding(Ui.dp(32), Ui.dp(60), Ui.dp(32), Ui.dp(60))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            val emptyIcon = TextView(this).apply {
                text = "🔍"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(48f))
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, Ui.dp(16))
            }
            emptyLayout.addView(emptyIcon)
            
            val empty = TextView(this).apply {
                text = "未找到结果\n请尝试其他关键词"
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
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
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, Ui.dp(8), 0, Ui.dp(8))
            }
            layout.addView(label)
            val gridColumns = calculateGridColumns()
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
                // 卡片聚焦放大超界部分不被裁剪（外层 padding 32dp 承载）
                clipToPadding = false
                clipChildren = false
                addItemDecoration(CardAdapter.GridSpacingItemDecoration(gridColumns, Ui.dp(16), true))
                adapter = CardAdapter(movies.map { it as Any }, "movie", onClick = { item ->
                    if (item is Movie) showMovieDetail(item, fromSearch = true)
                })
                // 预取：一次取 3 行，减少翻页时的 bind 抖动
                (layoutManager as GridLayoutManager).setInitialPrefetchItemCount(gridColumns * 3)
                setItemViewCacheSize(gridColumns * 3)
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(280) * ((movies.size + gridColumns - 1) / gridColumns)
            ))
        }

        if (tvs.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "电视 (${tvs.size})"
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(18f))
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, Ui.dp(16), 0, Ui.dp(8))
            }
            layout.addView(label)
            val gridColumns = calculateGridColumns()
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, gridColumns)
                // 卡片聚焦放大超界部分不被裁剪（外层 padding 32dp 承载）
                clipToPadding = false
                clipChildren = false
                addItemDecoration(CardAdapter.GridSpacingItemDecoration(gridColumns, Ui.dp(16), true))
                adapter = CardAdapter(tvs.map { it as Any }, "tv", onClick = { item ->
                    if (item is Tv) showTvDetail(item, fromSearch = true)
                })
                (layoutManager as GridLayoutManager).setInitialPrefetchItemCount(gridColumns * 3)
                setItemViewCacheSize(gridColumns * 3)
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(280) * ((tvs.size + gridColumns - 1) / gridColumns)
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
        stopBackdropRotation()

        // 保存播放列表状态（用于遥控器上下键切换）
        currentPlaylist = playlist
        currentPlayIndex = playIndex
        currentVideoUrl = url

        android.util.Log.d("OneList", "Player: original url='$url' galleryUid='$galleryUid' playlist=${playlist?.size ?: 0} index=$playIndex")
        if (url.isEmpty() || galleryUid == null) {
            toast("无效的播放地址")
            showHome()
            return
        }

        // 渲染播放器框架 + loading 提示
        val playerContainer = FrameLayout(this).apply { fillParent() }

        val playerView = LayoutInflater.from(this).inflate(R.layout.player_view, playerContainer, false) as PlayerView
        playerContainer.addView(playerView)

        val loadingText = TextView(this).apply {
            text = "正在加载视频源..."
            setTextColor(Ui.c.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        val loadingLP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        playerContainer.addView(loadingText, loadingLP)

        // 连播提示（右上角显示"下一个"）
        val nextHint = TextView(this).apply {
            setTextColor(Ui.c.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8))
            setBackgroundColor(Ui.c.scrim)
            visibility = View.GONE
        }
        val nextHintLP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = Ui.dp(16); rightMargin = Ui.dp(16) }
        playerContainer.addView(nextHint, nextHintLP)

        // 遥控器操作提示（屏幕中央半透明浮层，按键时短暂显示操作反馈）
        val opHint = TextView(this).apply {
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(12))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(8).toFloat()
                setColor(Ui.c.scrim)
            }
            visibility = View.GONE
        }
        val opHintLP = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        playerContainer.addView(opHint, opHintLP)
        operationHintView = opHint

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
            (mu.startsWith("/file/") && mu.substring(5) == nextUrl.trimStart('/')) ||
            mu.endsWith(nextUrl) || nextUrl.endsWith(mu) ||
            mu.substringAfterLast('/') == nextUrl.substringAfterLast('/')
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
                (p.url.startsWith("/file/") && p.url.substring(5) == nextUrl.trimStart('/')) ||
                p.url.endsWith(nextUrl) || nextUrl.endsWith(p.url) ||
                p.url.substringAfterLast('/') == nextUrl.substringAfterLast('/')
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

        // 配置渲染工厂：启用解码器回退，硬件解码器出错时自动尝试其他解码器
        // 配合 PlayerView 使用 TextureView（颜色通过 GPU 转换），共同解决绿屏问题
        val renderersFactory = com.google.android.exoplayer2.DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
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
                // 每次播放前刷新屏保配置和今日时长，确保服务端修改能及时同步
                fetchScreensaverConfig()
                fetchTodayPlayDuration()
                // 启动护眼屏保计时器（配置拉取是异步的，先用当前值启动，下次播放会用新值）
                if (screensaverEnabled) {
                    startScreensaverTracker()
                    // 防止绕过：如果今日已播放超标，立即触发锁定屏保
                    if (todayTotalSeconds >= screensaverDailyLimit && screensaverDailyLimit > 0) {
                        triggerScreensaver("locked")
                    }
                }
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
                            stopScreensaverTracker()

                            // 护眼屏保：达到每日限额时触发锁定屏保，不自动播放下一个
                            if (screensaverEnabled && todayTotalSeconds >= screensaverDailyLimit && screensaverDailyLimit > 0) {
                                triggerScreensaver("locked")
                            } else {
                                val played = playNextInPlaylist(playerView, loadingText, nextHint, galleryUid)
                                if (!played) {
                                    toast("播放完毕")
                                }
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

    /**
     * 顶栏图标按钮：图标+文字，normal=透明底，focused=cardHover+accent 描边（统一焦点形态）。
     * 顶栏按钮不做 scale（5.1 上越界裁剪会导致色块残留）。
     */
    private fun buildTopBtn(iconRes: Int?, label: String, defaultColor: Int = Ui.c.text2, onClick: () -> Unit): LinearLayout {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6))
            isClickable = true
            isFocusable = true
            background = Ui.rowFocusBg(Color.TRANSPARENT)
            setOnClickListener { onClick() }
        }
        if (iconRes != null) {
            btn.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(defaultColor)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(16), Ui.dp(16)).apply { marginEnd = Ui.dp(6) }
            })
        }
        btn.addView(TextView(this).apply {
            text = label
            setTextColor(defaultColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
        })
        // 焦点：背景由 StateListDrawable 自动切换；图标/文字 tint 切换为白色
        btn.setOnFocusChangeListener { _, hasFocus ->
            val color = if (hasFocus) Ui.c.text1 else defaultColor
            for (i in 0 until btn.childCount) {
                when (val child = btn.getChildAt(i)) {
                    is ImageView -> child.setColorFilter(color)
                    is TextView -> child.setTextColor(color)
                }
            }
        }
        return btn
    }

    private fun buildTopBar(title: String, showSearch: Boolean, showLogout: Boolean, showUserPages: Boolean = false, transparentBg: Boolean = false): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(24), Ui.dp(12), Ui.dp(24), Ui.dp(12))
            if (transparentBg) {
                // 详情页顶栏融入高斯模糊背景，避免实色条与半透明内容区形成色差
                background = null
            } else {
                setBackgroundColor(Ui.c.bg)
            }
            // 顶栏明确裁剪子 View（避免按钮 scale 越界造成色块残留）
            clipChildren = true
        }

        // Back button (if not on home)
        if (currentScreen != Screen.HOME && currentScreen != Screen.LOGIN) {
            bar.addView(buildTopBtn(R.drawable.ic_back, "返回", Ui.c.accent) { navigateBack() })
        }

        // Title（详情页不传标题：内容区已有大标题，避免重复）
        if (title.isNotEmpty()) {
            val titleView = TextView(this).apply {
                text = title
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(22f))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            titleLp.leftMargin = Ui.dp(8)
            bar.addView(titleView, titleLp)
        } else if (showSearch || showLogout || showUserPages) {
            // 无标题但有右侧按钮时补一个弹性占位，把按钮推到右边
            bar.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }

        // User pages buttons (最爱 / 已播放)
        if (showUserPages) {
            bar.addView(buildTopBtn(R.drawable.ic_heart, "最爱") { showHeartPage() })
            bar.addView(buildTopBtn(R.drawable.ic_history, "已播放") { showPlayedPage() })
        }

        // Search button
        if (showSearch) {
            bar.addView(buildTopBtn(R.drawable.ic_search, "搜索") { showSearch() })
        }

        // Logout button
        if (showLogout) {
            bar.addView(buildTopBtn(null, "退出", Ui.c.text3) {
                App.logout()
                showLogin()
            })
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
            Screen.HEART -> screenBackStack.addLast(ScreenState.HeartScreen)
            Screen.PLAYED -> screenBackStack.addLast(ScreenState.PlayedScreen)
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
            is ScreenState.HeartScreen -> showHeartPage()
            is ScreenState.PlayedScreen -> showPlayedPage()
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

    /**
     * 遥控器上下键切集时更新心跳元数据。
     * 电影：通过 URL 在 currentMovieList 中查找以获取正确的 dataId。
     * 电视剧：dataId（剧集 ID）不变，只更新集标题。
     */
    private fun updateHeartbeatMetadataForItem(item: PlayItem) {
        if (currentVideoDataType == "movie") {
            val movie = currentMovieList?.firstOrNull { m ->
                val mu = m.url ?: return@firstOrNull false
                mu == item.url || mu == item.url.trimStart('/') ||
                (mu.startsWith("/file/") && mu.substring(5) == item.url.trimStart('/')) ||
                (item.url.startsWith("/file/") && item.url.substring(5) == mu.trimStart('/'))
            }
            if (movie != null) {
                currentVideoDataId = movie.id
                currentVideoTitle = movie.title
                currentVideoGalleryUid = movie.galleryUid
                android.util.Log.d("OneList", "Nav heartbeat updated (movie): id=${movie.id} title='${movie.title}'")
            } else {
                android.util.Log.w("OneList", "Nav: movie not found for url='${item.url}', keeping old metadata")
            }
        } else if (currentVideoDataType == "tv") {
            currentVideoTitle = item.title ?: currentVideoTitle
            android.util.Log.d("OneList", "Nav heartbeat updated (tv): title='${currentVideoTitle}'")
        }
    }

    private fun playNext() {
        val pl = currentPlaylist ?: return
        if (currentPlayIndex >= pl.size - 1) {
            toast("已经是最后一个了")
            return
        }
        currentPlayIndex++
        val next = pl[currentPlayIndex]
        android.util.Log.d("OneList", "playNext: index=$currentPlayIndex title='${next.title}'")
        updateHeartbeatMetadataForItem(next)
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
        updateHeartbeatMetadataForItem(prev)
        toast("上一集: ${prev.title ?: ""}")
        showPlayer(prev.url, prev.galleryUid, pl, currentPlayIndex, pushToBackStack = false)
    }

    // ==================== KEY EVENTS ====================

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 护眼屏保激活时拦截按键：
        //  - locked 模式：全部吞掉（防绕过每日限额）
        //  - rest 模式：按任意键提前结束休息、恢复播放（原实现吞键导致用户只能干等倒计时）
        if (screensaverActive) {
            if (screensaverMode == "rest" && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                onScreensaverEnd()
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            // ESC 或 BACK 键：返回上一页
            if (event.keyCode == KeyEvent.KEYCODE_ESCAPE || event.keyCode == KeyEvent.KEYCODE_BACK) {
                navigateBack()
                return true
            }
            
            // 播放器页面的方向键控制
            if (currentScreen == Screen.PLAYER && player != null) {
                when (event.keyCode) {
                    // OK键 / 确认键 / 媒体播放暂停键：切换播放/暂停
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        if (player!!.isPlaying) {
                            player!!.pause()
                            showOperationHint("⏸ 暂停")
                            android.util.Log.d("OneList", "Pause by remote key")
                        } else {
                            player!!.play()
                            showOperationHint("▶ 播放")
                            android.util.Log.d("OneList", "Play by remote key")
                        }
                        return true
                    }
                    // 左方向键：快退 5 秒
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        val newPos = (player!!.currentPosition - 5000).coerceAtLeast(0)
                        player!!.seekTo(newPos)
                        showOperationHint("◀ 快退 5s")
                        android.util.Log.d("OneList", "Seek backward 5s to ${newPos}ms")
                        return true
                    }
                    // 右方向键：快进 5 秒
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        val duration = player!!.duration
                        val newPos = if (duration > 0) (player!!.currentPosition + 5000).coerceAtMost(duration) else player!!.currentPosition + 5000
                        player!!.seekTo(newPos)
                        showOperationHint("快进 5s ▶")
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
        stopScreensaverTracker()
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
            // 对称 onStop() 的 stopScreensaverTracker()：恢复护眼计时器
            if (screensaverEnabled) {
                startScreensaverTracker()
            }
        }
    }

    override fun onDestroy() {
        isDestroying = true
        // 注销 token 失效广播接收器
        try { unregisterReceiver(tokenInvalidReceiver) } catch (_: Exception) {}
        // 清理 SSE 资源
        try { sseEventSource?.cancel() } catch (_: Exception) {}
        sseEventSource = null
        try { sseClient?.dispatcher?.executorService?.shutdown() } catch (_: Exception) {}
        try { sseClient?.connectionPool?.evictAll() } catch (_: Exception) {}
        sseClient = null
        // 清理心跳资源
        stopHeartbeat()
        // 清理护眼屏保资源
        cleanupScreensaver()
        // 停止详情页模糊背景旋转
        stopBackdropRotation()
        // 释放播放器
        player?.release()
        player = null
        super.onDestroy()
    }

    // ==================== HELPERS ====================

    // ====== 焦点样式 Drawable 缓存 ======
    private val editTextBgCache = hashMapOf<Int, android.graphics.drawable.StateListDrawable>()

    /**
     * 统一焦点形态（全 app 一种观感）：
     *  - normal：defaultColor 圆角底
     *  - focused：cardHover 高亮底 + accent 描边（Ui.rowFocusBg）
     * 缩放统一 1.05（顶栏按钮由调用方不设 scale）
     */
    private fun View.applyFocusGlow(defaultColor: Int = Ui.c.accent) {
        background = Ui.rowFocusBg(defaultColor)
        setOnFocusScale(1.05f, 120)
    }

    /**
     * 为 EditText 输入框设置焦点高亮：StateListDrawable 自动加/去 3dp 白边
     */
    private fun EditText.applyEditTextFocus() {
        val r = Ui.dp(4).toFloat()
        val strokeW = Ui.dp(4)
        val sld = editTextBgCache.getOrPut(0) {
            val defaultBg = Ui.c.card
            val focused = GradientDrawable().apply {
                cornerRadius = r; setColor(defaultBg); setStroke(strokeW, Ui.c.text1)
            }
            val normal = GradientDrawable().apply { cornerRadius = r; setColor(defaultBg) }
            android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }
        background = sld
        // 缩放动画很小，仅作视觉反馈；不需要 1.12x 那么大（输入框太大影响排版）
        setOnFocusScale(1.03f, 100)
    }

    /** 统一的 scale 动画：只有目标值变化才启动，避免重复 cancel+start 的调度抖动 */
    private fun View.setOnFocusScale(focusedScale: Float, durationMs: Long) {
        this.setOnFocusChangeListener { v, _ ->
            try {
                val target = if (v.isFocused) focusedScale else 1f
                if (v.scaleX != target) {
                    v.animate().cancel()
                    v.animate().scaleX(target).scaleY(target).setDuration(durationMs).start()
                }
                // translationZ 只改绘制层级（解决放大越界被相邻 view 盖住），
                // 不能用 bringToFront——它会重排 LinearLayout children 导致按钮互换位置
                androidx.core.view.ViewCompat.setTranslationZ(v, if (v.isFocused) 10f else 0f)
            } catch (_: Exception) {}
        }
    }

    private fun label(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Ui.c.text2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 0, 0, Ui.dp(4))
        }
    }

    // ==================== HELPERS ====================

    /**
     * 统一空态/错误态卡片：警告图标 + 信息 + 可选重试按钮。
     * 用于列表页加载失败等场景；样式与全 app 焦点形态一致。
     */
    private fun buildStateView(message: String, retry: (() -> Unit)? = null): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            setPadding(Ui.dp(32), Ui.dp(32), Ui.dp(32), Ui.dp(32))
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_error)
                setColorFilter(Ui.c.warn)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(40), Ui.dp(40)).apply { bottomMargin = Ui.dp(16) }
            })
            addView(TextView(this@MainActivity).apply {
                text = message
                setTextColor(Ui.c.text2)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                gravity = Gravity.CENTER
            })
            if (retry != null) {
                addView(TextView(this@MainActivity).apply {
                    text = "按 OK 重试"
                    setTextColor(Ui.c.text1)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                    setPadding(Ui.dp(32), Ui.dp(12), Ui.dp(32), Ui.dp(12))
                    isFocusable = true
                    isClickable = true
                    applyFocusGlow(Ui.c.red)
                    setOnClickListener { retry() }
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = Ui.dp(24) })
            }
        }
    }

    private fun showErrorInLoadingContainer(recyclerView: RecyclerView, errorMsg: String) {
        val parent = recyclerView.parent as? FrameLayout ?: return
        parent.removeAllViews()
        val retry: () -> Unit = {
            currentPage = 1
            isLoadingMore = false
            hasMorePages = true
            listItems.clear()
            loadListData(recyclerView)
        }
        val errorLayout = buildStateView(errorMsg, retry)
        parent.addView(errorLayout)
        // 焦点自动落到重试按钮（最后一个子 View）
        val focusTarget = errorLayout.getChildAt(errorLayout.childCount - 1)
        focusTarget.post { focusTarget.requestFocus() }
    }

    /**
     * 根据屏幕宽度动态计算网格列数（TV适配）
     * 每列最小宽度150dp，确保卡片在不同分辨率下都有合适大小且足够紧凑
     */
    private fun calculateGridColumns(): Int {
        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val minColumnWidthDp = 150f
        val minColumnWidthPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            minColumnWidthDp,
            resources.displayMetrics
        )
        val columns = (screenWidth / minColumnWidthPx).toInt().coerceIn(3, 8)
        android.util.Log.d("OneList", "Screen width: ${screenWidth.toInt()}px, grid columns: $columns")
        return columns
    }

    private fun lp(w: Int = LinearLayout.LayoutParams.MATCH_PARENT, h: Int = LinearLayout.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(w, h)
    }

    /**
     * 递归关闭整棵 view 树的 clipChildren 与 clipToPadding。
     * 焦点放大（scale 1.05）的绘制要完整显示：
     *  - clipChildren=false：父容器不把子 view 裁到子自身 bounds
     *  - clipToPadding=false：父容器有 padding 时，默认（true）会把子绘制
     *    裁到 padding 框内（dispatchDraw 的画布级裁剪），与 clipChildren 无关
     * 两者必须沿祖先链全部关闭才生效；每次页面渲染完静态树后调用，
     * 之后异步加入的子树（季按钮行/网格）已在各自构建处单独关闭。
     */
    private fun ViewGroup.disableClipTree() {
        clipChildren = false
        clipToPadding = false
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c is ViewGroup) c.disableClipTree()
        }
    }

    /** 焦点移动入口：防抖 200ms，快速滑过不触发加载 */
    private fun updateHomeBackdrop(url: String?) {
        if (url == null) return
        backdropPendingUrl = url
        backdropHandler.removeCallbacks(backdropDebounceRunnable)
        backdropHandler.postDelayed(backdropDebounceRunnable, 200)
    }

    /** 加载海报 → 模糊 → 显示（命中内存缓存直接显示，无网络/模糊开销） */
    private fun showHomeBackdrop(url: String) {
        if (url == lastBackdropUrl) return
        if (backdropA == null || backdropB == null) return
        lastBackdropUrl = url
        val cached = backdropCache[url]
        if (cached != null) {
            crossfadeBackdrop(cached)
            return
        }
        try {
            Glide.with(this)
                .asBitmap()
                .load(url)
                .override(120, 180)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                    ) {
                        // 回调时页面可能已切换：showHomeBackdrop 重新取当前 URL 再入缓存
                        val bitmap = blurBitmap(resource) ?: resource
                        backdropCache[url] = bitmap
                        // 仅当仍是最新请求时才显示（连发时只显示最后一张）
                        if (url == lastBackdropUrl) crossfadeBackdrop(bitmap)
                    }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        } catch (_: Exception) {}
    }

    /** 双层交叉淡入淡出：新图淡入 350ms，旧图同步淡出 */
    private fun crossfadeBackdrop(bitmap: Bitmap) {
        val a = backdropA ?: return
        val b = backdropB ?: return
        val showView = if (backdropIsA) b else a
        val hideView = if (backdropIsA) a else b
        showView.setImageBitmap(bitmap)
        showView.alpha = 0f
        showView.animate().cancel()
        hideView.animate().cancel()
        showView.animate().alpha(1f).setDuration(350).start()
        hideView.animate().alpha(0f).setDuration(350).start()
        backdropIsA = !backdropIsA
    }

    private fun View.fillParent() {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    /**
     * 详情页高斯模糊背景：封面小图 → RenderScript 高斯模糊 → 全屏 centerCrop 放大。
     * 呼吸感动效：两张不同模糊半径的图（清晰模糊 8 / 强模糊 25）叠放，
     * 上层 alpha 0↔1 缓慢循环 → 视觉上虚化度高低变化，纯 GPU 合成，开销近零。
     */
    private fun loadBlurBackdrop(posterUrl: String?, baseView: ImageView, softView: ImageView) {
        if (posterUrl == null) return
        try {
            Glide.with(this)
                .asBitmap()
                .load(posterUrl)
                .override(120, 180)
                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                .into(object : com.bumptech.glide.request.target.CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap,
                        transition: com.bumptech.glide.request.transition.Transition<in Bitmap>?
                    ) {
                        val base = blurBitmap(resource, 25f) ?: resource
                        val soft = blurBitmap(resource, 8f) ?: base
                        baseView.setImageBitmap(base)
                        softView.setImageBitmap(soft)
                        softView.visibility = View.VISIBLE
                        startBackdropBreath(softView)
                    }
                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                })
        } catch (_: Exception) {}
    }

    /** 虚化度呼吸：上层清晰模糊图透明度 6 秒淡入→6 秒淡出循环 */
    private fun startBackdropBreath(target: View) {
        backdropBreathAnim?.cancel()
        backdropBreathAnim = ObjectAnimator.ofFloat(target, View.ALPHA, 0f, 1f).apply {
            duration = 6_000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopBackdropRotation() {
        backdropBreathAnim?.cancel()
        backdropBreathAnim = null
        // 一并清理首页焦点背景：防抖回调与 detached view 引用
        backdropHandler.removeCallbacks(backdropDebounceRunnable)
        backdropA = null
        backdropB = null
    }

    private fun blurBitmap(src: Bitmap, radius: Float = 25f): Bitmap? = try {
        val rs = android.renderscript.RenderScript.create(this)
        val input = android.renderscript.Allocation.createFromBitmap(rs, src)
        val output = android.renderscript.Allocation.createFromBitmap(rs, src)
        val script = android.renderscript.ScriptIntrinsicBlur.create(
            rs, android.renderscript.Element.U8_4(rs)
        )
        script.setRadius(radius)
        script.setInput(input)
        script.forEach(output)
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        output.copyTo(result)
        rs.destroy()
        result
    } catch (_: Throwable) { null }

    // ==================== 护眼屏保逻辑 ====================

    /** 从服务端获取屏保配置 */
    private fun fetchScreensaverConfig() {
        android.util.Log.d("OneList", "Fetching screensaver config...")
        RetrofitClient.getService().getConfigs().enqueue(object : Callback<ApiResponse<Config>> {
            override fun onResponse(call: Call<ApiResponse<Config>>, response: Response<ApiResponse<Config>>) {
                val d = response.body()?.data
                if (d == null) {
                    android.util.Log.w("OneList", "Screensaver config response data is null, using defaults")
                    return
                }
                screensaverEnabled = d.screensaverEnabled != "否"
                // 服务端存秒；旧版本 Web 端可能存过分钟数，小于 60 的值视为配置异常，
                // 钳制到最低 60 秒，防止"播几秒→屏保→播几秒"狂循环
                screensaverPlayDuration = (d.screensaverPlayDuration?.toIntOrNull() ?: 3600).coerceAtLeast(60)
                screensaverDuration = (d.screensaverDuration?.toIntOrNull() ?: 180).coerceAtLeast(60)
                screensaverDailyLimit = (d.screensaverDailyLimit?.toIntOrNull() ?: 7200).coerceAtLeast(0)
                android.util.Log.d("OneList", "Screensaver config loaded: enabled=$screensaverEnabled play=${screensaverPlayDuration}s dur=${screensaverDuration}s daily=${screensaverDailyLimit}s")
            }
            override fun onFailure(call: Call<ApiResponse<Config>>, t: Throwable) {
                android.util.Log.e("OneList", "Screensaver config fetch failed: ${t.message}, using defaults")
            }
        })
    }

    /** 获取壁纸素材列表 */
    private fun fetchWallpaperFiles() {
        RetrofitClient.getService().getWallpaperList().enqueue(object : Callback<ApiResponse<List<WallpaperFile>>> {
            override fun onResponse(call: Call<ApiResponse<List<WallpaperFile>>>, response: Response<ApiResponse<List<WallpaperFile>>>) {
                val files = response.body()?.data
                if (files != null) {
                    wallpaperFiles = files
                    android.util.Log.d("OneList", "Wallpaper files: ${files.size}")
                }
            }
            override fun onFailure(call: Call<ApiResponse<List<WallpaperFile>>>, t: Throwable) {
                android.util.Log.e("OneList", "Wallpaper fetch failed: ${t.message}")
            }
        })
    }

    /** 获取今日已播放总秒数 */
    private fun fetchTodayPlayDuration() {
        RetrofitClient.getService().getTodayDuration().enqueue(object : Callback<ApiResponse<Int>> {
            override fun onResponse(call: Call<ApiResponse<Int>>, response: Response<ApiResponse<Int>>) {
                if (response.body()?.code == 200) {
                    todayTotalSeconds = response.body()?.data ?: 0
                    android.util.Log.d("OneList", "Today play duration: ${todayTotalSeconds}s")
                }
            }
            override fun onFailure(call: Call<ApiResponse<Int>>, t: Throwable) {
                android.util.Log.e("OneList", "Today play duration fetch failed: ${t.message}")
            }
        })
    }

    /** 启动播放时长追踪器（每秒 +1，仅在视频播放时累计） */
    private fun startScreensaverTracker() {
        stopScreensaverTracker()
        android.util.Log.d("OneList", "Screensaver tracker started: enabled=$screensaverEnabled playDur=${screensaverPlayDuration}s dailyLimit=${screensaverDailyLimit}s cumulative=${cumulativePlaySeconds}s todayTotal=${todayTotalSeconds}s")
        screensaverHandler.post(screensaverTrackerRunnable)
    }

    private val screensaverTrackerRunnable = object : Runnable {
        override fun run() {
            if (screensaverActive) {
                screensaverHandler.postDelayed(this, 1000)
                return
            }
            // 仅在视频正在播放时累计
            val p = player
            if (p != null && p.playWhenReady && p.playbackState == com.google.android.exoplayer2.Player.STATE_READY) {
                cumulativePlaySeconds++
                todayTotalSeconds++

                // 每30秒输出一次日志帮助诊断
                if (cumulativePlaySeconds % 30 == 0) {
                    android.util.Log.d("OneList", "Screensaver tracker: cumulative=${cumulativePlaySeconds}s today=${todayTotalSeconds}s threshold=${screensaverPlayDuration}s")
                }

                val remaining = screensaverDailyLimit - todayTotalSeconds

                // 达到每日上限：画面变暗，等当前集播完再锁（对齐 v1.4.2）。
                // 不立即锁定——锁定→服务端检查解锁→恢复播放→又达到限额→再锁
                // 会构成自激循环（暂停期间服务端不累计，解锁检查永远通过）
                if (remaining <= 0) {
                    android.util.Log.d("OneList", "Screensaver: daily limit reached ($todayTotalSeconds >= $screensaverDailyLimit), dimming until episode ends")
                    showDailyLimitDimmed(true)
                    showDailyLimitWarning(false)
                    screensaverHandler.postDelayed(this, 1000)
                    return
                }

                // 接近每日上限（剩余 ≤ 10 分钟）：友好提示
                if (remaining <= 600) {
                    showDailyLimitWarning(true)
                } else {
                    showDailyLimitWarning(false)
                }

                // 检查连续播放阈值
                if (cumulativePlaySeconds >= screensaverPlayDuration) {
                    android.util.Log.d("OneList", "Screensaver: cumulative play reached threshold, starting warning")
                    startScreensaverWarning()
                }
            }
            screensaverHandler.postDelayed(this, 1000)
        }
    }

    private fun stopScreensaverTracker() {
        screensaverHandler.removeCallbacks(screensaverTrackerRunnable)
    }

    /** 创建预警横幅（首次调用时） */
    private fun ensureWarningBanner() {
        if (warningBanner != null) return
        warningBanner = TextView(this).apply {
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14))
            setBackgroundColor(Ui.c.warn)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        rootLayout.addView(warningBanner, lp)
    }

    /** 创建每日上限预警横幅 */
    private fun ensureDailyLimitWarningBanner() {
        if (dailyLimitWarningBanner != null) return
        dailyLimitWarningBanner = TextView(this).apply {
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setPadding(Ui.dp(20), Ui.dp(14), Ui.dp(20), Ui.dp(14))
            setBackgroundColor(Ui.c.red)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP }
        rootLayout.addView(dailyLimitWarningBanner, lp)
    }

    /** 创建每日上限半透明遮罩 */
    private fun ensureDailyLimitDimOverlay() {
        if (dailyLimitDimOverlay != null) return
        dailyLimitDimOverlay = View(this).apply {
            setBackgroundColor(Ui.c.scrim)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        rootLayout.addView(dailyLimitDimOverlay, lp)
    }

    private fun showDailyLimitDimmed(show: Boolean) {
        ensureDailyLimitDimOverlay()
        dailyLimitDimOverlay?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showDailyLimitWarning(show: Boolean) {
        ensureDailyLimitWarningBanner()
        val banner = dailyLimitWarningBanner ?: return
        if (show) {
            val remainingMin = ((screensaverDailyLimit - todayTotalSeconds) / 60f).toInt().coerceAtLeast(1)
            banner.text = "今天的时间快用完啦，还剩约 $remainingMin 分钟"
            banner.visibility = View.VISIBLE
        } else {
            banner.visibility = View.GONE
        }
    }

    /** 10 秒预警 → 然后触发休息屏保 */
    private fun startScreensaverWarning() {
        if (screensaverActive) return
        // 防止重复调用：如果预警已在运行，直接返回
        if (warningRunnableRef != null) return
        ensureWarningBanner()
        val banner = warningBanner ?: return
        var countdown = 10
        banner.text = "护眼屏保将在 ${countdown} 秒后启动，请准备休息"
        banner.visibility = View.VISIBLE
        val warningRunnable = object : Runnable {
            override fun run() {
                countdown--
                if (countdown <= 0) {
                    banner.visibility = View.GONE
                    triggerScreensaver("rest")
                    return
                }
                banner.text = "护眼屏保将在 ${countdown} 秒后启动，请准备休息"
                screensaverHandler.postDelayed(this, 1000)
            }
        }
        screensaverHandler.post(warningRunnable)
        // 保存引用以便取消
        warningRunnableRef = warningRunnable
    }

    private var warningRunnableRef: Runnable? = null

    private fun stopScreensaverWarning() {
        warningRunnableRef?.let { screensaverHandler.removeCallbacks(it) }
        warningRunnableRef = null
        warningBanner?.visibility = View.GONE
    }

    /** 触发屏保（rest 或 locked） */
    private fun triggerScreensaver(mode: String) {
        stopScreensaverWarning()
        showDailyLimitWarning(false)
        showDailyLimitDimmed(false)
        screensaverMode = mode
        screensaverActive = true

        // 暂停视频
        player?.pause()

        // 创建屏保 UI
        showScreensaverOverlay()

        if (mode == "rest") {
            screensaverCountdown = screensaverDuration
            updateCountdownDisplay()
            // 开始倒计时
            screensaverHandler.post(screensaverCountdownRunnable)
        } else if (mode == "locked") {
            // locked 模式：每 60 秒检查一次是否已跨天解锁
            screensaverHandler.postDelayed({ onScreensaverEnd() }, 60000)
        }
        android.util.Log.d("OneList", "Screensaver triggered: mode=$mode")
    }

    private val screensaverCountdownRunnable = object : Runnable {
        override fun run() {
            if (!screensaverActive) return
            screensaverCountdown--
            if (screensaverCountdown <= 0) {
                onScreensaverEnd()
                return
            }
            updateCountdownDisplay()
            screensaverHandler.postDelayed(this, 1000)
        }
    }

    private fun updateCountdownDisplay() {
        val tv = countdownText ?: return
        val todayMin = todayTotalSeconds / 60
        if (screensaverMode == "rest") {
            val m = screensaverCountdown / 60
            val s = screensaverCountdown % 60
            val timeStr = if (m > 0) "${m}分${s.toString().padStart(2, '0')}秒" else "${s}秒"
            tv.text = "休息中 ${timeStr}\n今天已看 ${todayMin} 分钟"
        } else {
            tv.text = "今日播放时间已到\n明天再继续吧\n今天已看 ${todayMin} 分钟"
        }
    }

    /** 创建并显示屏保覆盖层 */
    private fun showScreensaverOverlay() {
        // 清理旧的
        dismissScreensaverOverlay()

        val overlay = FrameLayout(this).apply {
            fillParent()
            setBackgroundColor(Color.BLACK)
            // 拦截所有按键
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }

        // 壁纸内容区域（先添加，确保倒计时角标在最上层）
        val wallpaper = pickRandomWallpaper()
        if (wallpaper != null) {
            when (wallpaper.type) {
                "image" -> {
                    val imageView = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        fillParent()
                    }
                    val imgUrl = buildWallpaperUrl(wallpaper.url)
                    com.bumptech.glide.Glide.with(this@MainActivity)
                        .load(imgUrl)
                        // 屏保壁纸按屏幕真实尺寸下采样，避免 4K 原图直接解码
                        .override(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
                        .centerCrop()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .into(imageView)
                    overlay.addView(imageView)
                }
                "video" -> {
                    val pView = PlayerView(this@MainActivity).apply {
                        fillParent()
                        useController = false
                    }
                    overlay.addView(pView)
                    wallpaperPlayerView = pView
                    // 创建壁纸播放器
                    val wp = ExoPlayer.Builder(this@MainActivity).build().also { exo ->
                        pView.player = exo
                        val imgUrl = buildWallpaperUrl(wallpaper.url)
                        exo.setMediaItem(MediaItem.fromUri(imgUrl))
                        exo.repeatMode = com.google.android.exoplayer2.Player.REPEAT_MODE_ALL
                        exo.volume = 0f
                        exo.playWhenReady = true
                        exo.prepare()
                    }
                    wallpaperPlayer = wp
                }
                "html" -> {
                    val webView = WebView(this@MainActivity).apply {
                        fillParent()
                        webViewClient = WebViewClient()
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = false
                        settings.allowFileAccess = false
                        settings.mediaPlaybackRequiresUserGesture = false
                        setBackgroundColor(Color.BLACK)
                    }
                    overlay.addView(webView)
                    wallpaperWebView = webView
                    val htmlUrl = buildWallpaperUrl(wallpaper.url)
                    webView.loadUrl(htmlUrl)
                }
                else -> {
                    // 其他未知类型：显示默认界面
                    addDefaultScreensaverContent(overlay)
                }
            }
        } else {
            addDefaultScreensaverContent(overlay)
        }

        // 倒计时角标（右上角，最后添加以确保在最上层）
        val countdown = TextView(this).apply {
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(12))
            background = GradientDrawable().apply {
                cornerRadius = Ui.dp(8).toFloat()
                setColor(Ui.c.scrim)
            }
            gravity = Gravity.CENTER
            setLineSpacing(Ui.dp(4).toFloat(), 1f)
        }
        val countdownLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.TOP or Gravity.END; topMargin = Ui.dp(24); rightMargin = Ui.dp(24) }
        overlay.addView(countdown, countdownLp)
        countdownText = countdown

        rootLayout.addView(overlay)
        screensaverOverlay = overlay
    }

    /** 构建壁纸完整 URL（含 token） */
    private fun buildWallpaperUrl(relativeUrl: String): String {
        val base = (App.serverUrl ?: "").trimEnd('/')
        val token = App.token
        return if (token != null) {
            "$base$relativeUrl?token=$token"
        } else {
            "$base$relativeUrl"
        }
    }

    /** 随机选取一个壁纸素材 */
    private fun pickRandomWallpaper(): WallpaperFile? {
        if (wallpaperFiles.isEmpty()) return null
        // 支持 video、image、html 三种类型
        val supported = wallpaperFiles.filter { it.type == "video" || it.type == "image" || it.type == "html" }
        if (supported.isEmpty()) return null
        return supported.random()
    }

    /** 添加默认屏保内容（绿色渐变 + 文字） */
    private fun addDefaultScreensaverContent(container: FrameLayout) {
        val defaultView = LinearLayout(this).apply {
            fillParent()
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#A8E6CF"), Color.parseColor("#DCEDC1"), Color.parseColor("#C5E1A5"))
            )
        }
        val icon = TextView(this).apply {
            text = "\uD83C\uDF33"  // 🌳 tree emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 48f)
            gravity = Gravity.CENTER
            setTextColor(Ui.c.success)
        }
        val msg = TextView(this).apply {
            text = "爱护眼睛，先休息一会吧"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            gravity = Gravity.CENTER
            setTextColor(Ui.c.success)
            setPadding(0, Ui.dp(16), 0, 0)
        }
        defaultView.addView(icon)
        defaultView.addView(msg)
        defaultView.fillParent()
        container.addView(defaultView)
    }

    /** 屏保结束：休息模式恢复播放，锁定模式检查解锁 */
    private fun onScreensaverEnd() {
        if (screensaverMode == "rest") {
            cumulativePlaySeconds = 0
            dismissScreensaver()
            player?.play()
            // 重新开始屏保计时，并与服务端重新对齐今日时长（防本地累计漂移导致误判锁定）
            if (screensaverEnabled) {
                fetchTodayPlayDuration()
                startScreensaverTracker()
            }
        } else if (screensaverMode == "locked") {
            // 重新查询今日时长，确认是否已跨天
            // 把检查逻辑放到回调中，确保用最新数据而非竞态旧值
            RetrofitClient.getService().getTodayDuration().enqueue(object : Callback<ApiResponse<Int>> {
                override fun onResponse(call: Call<ApiResponse<Int>>, response: Response<ApiResponse<Int>>) {
                    val serverToday = response.body()?.data ?: 0
                    todayTotalSeconds = serverToday
                    android.util.Log.d("OneList", "Lock check: server today=$serverToday limit=$screensaverDailyLimit")
                    screensaverHandler.post {
                        if (serverToday < screensaverDailyLimit) {
                            // 已跨天解锁
                            cumulativePlaySeconds = 0
                            dismissScreensaver()
                            player?.play()
                            if (screensaverEnabled) startScreensaverTracker()
                        } else {
                            // 仍然锁定，1 分钟后再检查
                            screensaverHandler.postDelayed({ onScreensaverEnd() }, 60000)
                        }
                    }
                }
                override fun onFailure(call: Call<ApiResponse<Int>>, t: Throwable) {
                    android.util.Log.e("OneList", "Lock check failed: ${t.message}, stay locked")
                    screensaverHandler.postDelayed({ onScreensaverEnd() }, 60000)
                }
            })
        }
    }

    /** 关闭屏保（清理 UI + 状态） */
    private fun dismissScreensaver() {
        screensaverActive = false
        screensaverHandler.removeCallbacks(screensaverCountdownRunnable)
        dismissScreensaverOverlay()
        showDailyLimitDimmed(false)
        showDailyLimitWarning(false)
    }

    /** 清理屏保覆盖层和壁纸播放器 */
    private fun dismissScreensaverOverlay() {
        // 释放壁纸播放器
        wallpaperPlayer?.release()
        wallpaperPlayer = null
        wallpaperPlayerView = null
        // 清理 WebView
        wallpaperWebView?.let {
            it.stopLoading()
            it.destroy()
        }
        wallpaperWebView = null
        // 移除覆盖层
        screensaverOverlay?.let { rootLayout.removeView(it) }
        screensaverOverlay = null
        countdownText = null
    }

    /** 清理所有屏保相关资源（离开播放器页面或销毁时调用） */
    private fun cleanupScreensaver() {
        stopScreensaverTracker()
        stopScreensaverWarning()
        dismissScreensaver()
        showDailyLimitDimmed(false)
        showDailyLimitWarning(false)
        dailyLimitDimOverlay?.let { rootLayout.removeView(it); dailyLimitDimOverlay = null }
        warningBanner?.let { rootLayout.removeView(it); warningBanner = null }
        dailyLimitWarningBanner?.let { rootLayout.removeView(it); dailyLimitWarningBanner = null }
    }


    // 顶部横幅提示（替代 Toast：位置可控、样式统一、连发不叠加）
    private var toastBanner: TextView? = null
    private val toastHandler = Handler(Looper.getMainLooper())

    private fun toast(msg: String) {
        runOnUiThread {
            if (isDestroying) return@runOnUiThread
            toastHandler.removeCallbacksAndMessages(null)
            toastBanner?.let { rootLayout.removeView(it) }
            val banner = TextView(this).apply {
                text = msg
                setTextColor(Ui.c.text1)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, Ui.sp(16f))
                setPadding(Ui.dp(24), Ui.dp(8), Ui.dp(24), Ui.dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(8).toFloat()
                    setColor(Ui.c.cardHover)
                    setStroke(Ui.dp(1), Ui.c.accent)
                }
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = Ui.dp(16)
            }
            rootLayout.addView(banner, lp)
            toastBanner = banner
            toastHandler.postDelayed({
                if (isDestroying) return@postDelayed
                rootLayout.removeView(banner)
                if (toastBanner === banner) toastBanner = null
            }, 2000)
        }
    }

    /** 在播放器屏幕中央短暂显示操作提示（1.2 秒后自动隐藏） */
    private fun showOperationHint(text: String) {
        val hint = operationHintView ?: return
        hint.text = text
        hint.visibility = View.VISIBLE
        operationHintHandler.removeCallbacksAndMessages(null)
        operationHintHandler.postDelayed({ hint.visibility = View.GONE }, 1200)
    }

    // ==================== 剧集列表专用 RecyclerView Adapter（ViewHolder 复用） ====================
    private inner class EpisodeAdapter(
        private val episodes: List<Episode>,
        private val onClick: (Episode) -> Unit,
        private val currentUrl: String? = null
    ) : RecyclerView.Adapter<EpisodeAdapter.EpisodeVH>() {

        inner class EpisodeVH(val row: LinearLayout) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeVH {
            val ctx = parent.context
            val epBtn = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    Ui.dp(52)
                ).apply {
                    topMargin = Ui.dp(8)
                }
                setPadding(Ui.dp(12), Ui.dp(8), Ui.dp(12), Ui.dp(8))
                isClickable = true
                isFocusable = true
                // 统一焦点形态：normal=行底色圆角，focused=cardHover+accent 描边（bind 时按当前集再设置）
                background = Ui.rowFocusBg(Ui.c.card)
                setOnFocusChangeListener { v, _ ->
                    try { Ui.scaleOnFocus(v) } catch (_: Exception) {}
                }
            }
            val epNum = TextView(ctx).apply {
                textSize = Ui.sp(14f)
                setTextColor(Ui.c.accent)
                setTypeface(null, android.graphics.Typeface.BOLD)
                id = android.R.id.text1
            }
            epBtn.addView(epNum)
            val epTitle = TextView(ctx).apply {
                textSize = Ui.sp(13f)
                setTextColor(Ui.c.text1)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                id = android.R.id.text2
            }
            epBtn.addView(epTitle, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f; marginStart = Ui.dp(8) })
            return EpisodeVH(epBtn)
        }

        override fun onBindViewHolder(holder: EpisodeVH, position: Int) {
            val ep = episodes[position]
            val row = holder.row
            val isCurrent = currentUrl != null && ep.url != null && ep.url == currentUrl
            val epNumView = row.findViewById<TextView>(android.R.id.text1)
            epNumView.text = if (isCurrent) "▶ E${ep.episodeNumber ?: "?"}" else "E${ep.episodeNumber ?: "?"}"
            epNumView.setTextColor(if (isCurrent) Ui.c.cyan else Ui.c.accent)
            (row.findViewById<TextView>(android.R.id.text2)).text = ep.title ?: ""
            // 正在播放的集：常驻青色底，焦点态由 rowFocusBg 自动切换（cardHover+accent 描边）
            row.background = Ui.rowFocusBg(if (isCurrent) Ui.c.teal else Ui.c.card)
            row.setOnClickListener { onClick(ep) }
        }

        override fun getItemCount(): Int = episodes.size
    }
}
