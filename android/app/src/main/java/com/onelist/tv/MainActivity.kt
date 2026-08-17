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

    // State
    private var currentGalleryId: String? = null
    private var currentGalleryTitle: String? = null
    private var currentGalleryType: String? = null // "movie" or "tv"
    private var currentPage = 1
    private var isLoadingMore = false
    private var hasMorePages = true
    private val listItems = mutableListOf<Any>() // Movie or Tv objects
    private var listAdapter: CardAdapter? = null

    // Playlist for episode/movie navigation during playback
    private data class PlayItem(val url: String, val galleryUid: String?, val title: String? = null)
    private var currentPlaylist: List<PlayItem>? = null
    private var currentPlayIndex: Int = 0
    private var currentMovieList: List<Movie>? = null // Track movie list for player navigation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rootLayout = FrameLayout(this)
        rootLayout.setBackgroundColor(Color.parseColor("#0d0d1a"))
        setContentView(rootLayout)

        if (App.isLoggedIn()) {
            showHome()
        } else {
            showLogin()
        }
    }

    // ==================== SCREEN MANAGEMENT ====================

    private fun showScreen(screen: Screen) {
        currentScreen = screen
        rootLayout.removeAllViews()
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
        rootLayout.removeAllViews()

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(40))
        }

        // Top bar
        val topBar = buildTopBar("Onelist", showSearch = true, showLogout = true)
        layout.addView(topBar)

        // Loading indicator
        val loading = TextView(this).apply {
            text = "加载中..."
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(60), 0, dp(60))
        }
        layout.addView(loading)

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
                        loading.visibility = View.GONE
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
                        loading.text = errorMsg
                    }
                }
                override fun onFailure(call: Call<ApiResponse<HomeData>>, t: Throwable) {
                    android.util.Log.e("OneList", "API failure: ${t.message}", t)
                    loading.text = "连接失败: ${t.message}"
                }
            })
        } catch (e: Exception) {
            android.util.Log.e("OneList", "Exception: ${e.message}", e)
            loading.text = "错误: ${e.message}"
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
            currentMovieList = data.latestMovies?.filter { it.url != null }
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
            setPadding(dp(24), dp(16), dp(24), dp(8))
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            isClickable = true
            isFocusable = true
            setPadding(dp(8), dp(4), dp(8), dp(4))
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dp(12), dp(4), dp(4), dp(4))
            }
            row.addView(more)
        }

        return row
    }

    private fun buildHorizontalCardList(items: List<Any>, type: String): RecyclerView {
        val adapter = CardAdapter(items, type) { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        }
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            this.adapter = adapter
            setPadding(dp(16), 0, dp(16), 0)
            clipToPadding = false
            clipChildren = false
        }
        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(280)
        )
        recyclerView.layoutParams = lp
        return recyclerView
    }

    // ==================== LIST SCREEN ====================

    private fun showList() {
        currentScreen = Screen.LIST
        rootLayout.removeAllViews()
        currentPage = 1
        isLoadingMore = false
        hasMorePages = true
        listItems.clear()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fillParent()
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        // Top bar
        val topBar = buildTopBar(currentGalleryTitle ?: "浏览", showSearch = true, showLogout = false)
        layout.addView(topBar)

        // RecyclerView grid
        listAdapter = CardAdapter(listItems, currentGalleryType ?: "movie") { item ->
            when (item) {
                is Movie -> showMovieDetail(item)
                is Tv -> showTvDetail(item)
            }
        }
        val recyclerView = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 5)
            this.adapter = listAdapter
            setPadding(dp(16), dp(8), dp(16), dp(16))
            clipToPadding = false
        }
        layout.addView(recyclerView, lp(LinearLayout.LayoutParams.MATCH_PARENT, 0).apply {
            weight = 1f
        })

        // Load more button
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

        // Load first page
        loadListData(recyclerView, loadMoreBtn)
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
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Tv>>, t: Throwable) {
                    toast("加载失败: ${t.message}")
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
                        // Track movie list for player next/previous navigation
                        currentMovieList = listItems.filterIsInstance<Movie>().filter { it.url != null }
                        hasMorePages = body.data != null && body.data!!.size >= 30
                        listAdapter?.notifyDataSetChanged()
                        loadMoreBtn.visibility = if (hasMorePages) View.VISIBLE else View.GONE
                    }
                }
                override fun onFailure(call: Call<ApiListResponse<Movie>>, t: Throwable) {
                    toast("加载失败: ${t.message}")
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
                        listAdapter?.notifyItemRangeInserted(start, body.data!!.size)
                        // Update movie list for player navigation
                        currentMovieList = listItems.filterIsInstance<Movie>().filter { it.url != null }
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

    private fun showMovieDetail(movie: Movie) {
        currentScreen = Screen.DETAIL
        rootLayout.removeAllViews()

        val scroll = ScrollView(this)
        scroll.fillParent()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(20), 0, dp(40))
        }

        // Top bar with back button
        val topBar = buildTopBar(movie.title ?: "详情", showSearch = false, showLogout = false)
        layout.addView(topBar)

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(32), dp(16), dp(32), 0)
        }

        // Poster
        val posterUrl = RetrofitClient.imageUrl(movie.posterPath)
        android.util.Log.d("OneList", "MovieDetail: title='${movie.title}' origTitle='${movie.originalTitle}' posterPath='${movie.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(270))
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a2e"))
                    cornerRadius = dp(4).toFloat()
                }
                Glide.with(this@MainActivity).load(posterUrl).placeholder(placeholder).error(placeholder).into(this)
            }
        }
        contentLayout.addView(posterView)

        // Info panel
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
        }

        // Title
        val titleView = TextView(this).apply {
            text = movie.title ?: movie.originalTitle ?: ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        infoLayout.addView(titleView)

        // Year + rating
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        // Genres
        if (movie.genres != null && movie.genres.isNotEmpty()) {
            val genreText = movie.genres.joinToString(" / ") { it.name ?: "" }
            val genreView = TextView(this).apply {
                text = genreText
                setTextColor(Color.parseColor("#888888"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(6), 0, 0)
            }
            infoLayout.addView(genreView)
        }

        // Play button
        val playBtn = Button(this).apply {
            text = "▶  播放"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setPadding(dp(32), dp(12), dp(32), dp(12))
            isFocusable = true
            isClickable = true
            applyFocusGlow(Color.parseColor("#e50914"), Color.parseColor("#ff3b4f"), Color.WHITE) // 红色播放按钮，聚焦更亮 + 白描边
            setOnClickListener {
                if (movie.url != null) {
                    // Build playlist from current movie list if available
                    val playlist = currentMovieList?.map { PlayItem(it.url!!, it.galleryUid, it.title) }
                    val index = playlist?.indexOfFirst { it.url == movie.url } ?: 0
                    showPlayer(movie.url!!, movie.galleryUid, playlist, if (index >= 0) index else 0)
                } else {
                    toast("暂无播放源")
                }
            }
        }
        infoLayout.addView(playBtn, LinearLayout.LayoutParams.WRAP_CONTENT.let {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(20)
            }
        })

        contentLayout.addView(infoLayout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        layout.addView(contentLayout)

        // Description
        if (movie.desc != null && movie.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(32), dp(24), dp(32), dp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = movie.desc
                setTextColor(Color.parseColor("#cccccc"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.4f)
                setPadding(dp(32), 0, dp(32), 0)
            }
            layout.addView(descView)
        }

        scroll.addView(layout)
        rootLayout.addView(scroll)

        // 进入详情页自动聚焦播放按钮，让用户一眼看到焦点位置
        playBtn.post { playBtn.requestFocus() }

        // Fetch full detail if needed
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
                            // Re-render with full detail
                            showMovieDetail(Movie(
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

    private fun showTvDetail(tv: Tv) {
        currentScreen = Screen.DETAIL
        rootLayout.removeAllViews()

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

        // Poster
        val posterUrl = RetrofitClient.imageUrl(tv.posterPath)
        android.util.Log.d("OneList", "TvDetail: name='${tv.name}' origName='${tv.originalName}' posterPath='${tv.posterPath}' -> url='$posterUrl'")
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(270))
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            if (posterUrl != null) {
                val placeholder = android.graphics.drawable.GradientDrawable().apply {
                    setColor(Color.parseColor("#1a1a2e"))
                    cornerRadius = dp(4).toFloat()
                }
                Glide.with(this@MainActivity).load(posterUrl).placeholder(placeholder).error(placeholder).into(this)
            }
        }
        contentLayout.addView(posterView)

        // Info
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
        }

        val titleView = TextView(this).apply {
            text = tv.name ?: tv.originalName ?: ""
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setPadding(0, dp(8), 0, 0)
            }
            infoLayout.addView(metaView)
        }

        contentLayout.addView(infoLayout)
        layout.addView(contentLayout)

        // Description
        if (tv.desc != null && tv.desc!!.isNotEmpty()) {
            val descLabel = TextView(this).apply {
                text = "简介"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(dp(32), dp(24), dp(32), dp(8))
            }
            layout.addView(descLabel)
            val descView = TextView(this).apply {
                text = tv.desc
                setTextColor(Color.parseColor("#cccccc"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setLineSpacing(0f, 1.4f)
                setPadding(dp(32), 0, dp(32), 0)
            }
            layout.addView(descView)
        }

        // Seasons container
        val seasonsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), 0)
        }
        val seasonsLabel = TextView(this).apply {
            text = "分季"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        seasonsContainer.addView(seasonsLabel)
        layout.addView(seasonsContainer)

        scroll.addView(layout)
        rootLayout.addView(scroll)

        // Fetch TV detail with seasons
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
                                    setPadding(dp(16), dp(10), dp(16), dp(10))
                                    isFocusable = true
                                    isClickable = true
                                    applyFocusGlow(Color.parseColor("#1a1a2e"), Color.parseColor("#6366f1"), Color.WHITE)
                                    val lp = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    )
                                    lp.topMargin = dp(8)
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

    private fun showEpisodeList(episodes: List<Episode>, seasonTitle: String) {
        currentScreen = Screen.DETAIL
        rootLayout.removeAllViews()

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

        // Build playlist from all episodes for navigation
        val playlist = episodes.filter { it.url != null }.map {
            PlayItem(it.url!!, it.galleryUid, "E${it.episodeNumber ?: "?"} ${it.title ?: ""}")
        }

        for (ep in episodes) {
            val epBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1a1a2e"))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                applyCardFocus()
                setOnClickListener {
                    if (ep.url != null) {
                        val index = playlist.indexOfFirst { it.url == ep.url }
                        showPlayer(ep.url!!, ep.galleryUid, playlist, if (index >= 0) index else 0)
                    } else {
                        toast("暂无播放源")
                    }
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(6)

            val epNum = TextView(this).apply {
                text = "E${ep.episodeNumber ?: "?"}"
                setTextColor(Color.parseColor("#6366f1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            epBtn.addView(epNum)

            val epTitle = TextView(this).apply {
                text = "  ${ep.title ?: ""}"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
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

    private fun showSearch() {
        currentScreen = Screen.SEARCH
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
            val empty = TextView(this).apply {
                text = "未找到结果"
                setTextColor(Color.GRAY)
                gravity = Gravity.CENTER
                setPadding(0, dp(40), 0, dp(40))
            }
            layout.addView(empty)
            return
        }

        if (movies.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "电影 (${movies.size})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(8), 0, dp(8))
            }
            layout.addView(label)
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, 5)
                adapter = CardAdapter(movies.map { it as Any }, "movie") { item ->
                    if (item is Movie) showMovieDetail(item)
                }
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280 * ((movies.size + 4) / 5))
            ))
        }

        if (tvs.isNotEmpty()) {
            val label = TextView(this).apply {
                text = "电视 (${tvs.size})"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(16), 0, dp(8))
            }
            layout.addView(label)
            val recyclerView = RecyclerView(this).apply {
                layoutManager = GridLayoutManager(this@MainActivity, 5)
                adapter = CardAdapter(tvs.map { it as Any }, "tv") { item ->
                    if (item is Tv) showTvDetail(item)
                }
            }
            layout.addView(recyclerView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(280 * ((tvs.size + 4) / 5))
            ))
        }
    }

    // ==================== PLAYER SCREEN ====================

    private fun showPlayer(url: String, galleryUid: String?, playlist: List<PlayItem>? = null, playIndex: Int = 0) {
        currentScreen = Screen.PLAYER
        rootLayout.removeAllViews()

        // Store playlist context for next/previous navigation
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

        rootLayout.addView(playerContainer)

        val base = App.serverUrl
        if (base == null || base.isEmpty()) {
            loadingText.text = "请先配置服务器地址"
            return
        }
        val normalizedBase = if (base.endsWith("/")) base.dropLast(1) else base

        // 直接 HTTP/HTTPS 直链，跳过接口判断
        if (url.startsWith("http")) {
            loadingText.visibility = View.GONE
            startExoPlayer(playerView, url)
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
                                            // 取最高清晰度（数组最后一个）
                                            val bestUrl = tasks.lastOrNull()?.url
                                            if (bestUrl != null) {
                                                loadingText.visibility = View.GONE
                                                startExoPlayer(playerView, bestUrl)
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
                        // alist 代理模式: /alist/proxy/{uid} + url
                        if (url.startsWith("/alist/proxy/")) "$normalizedBase$url"
                        else "$normalizedBase/alist/proxy/$galleryUid$url"
                    } else {
                        // 本地直链模式: /file/ + url（去前导 /）
                        if (url.startsWith("/file/")) "$normalizedBase$url"
                        else "$normalizedBase/file/${url.trimStart('/')}"
                    }
                    android.util.Log.d("OneList", "Player resolved: alistHost='$alistHost' videoSrc='$videoSrc'")
                    loadingText.text = "正在播放: $videoSrc"
                    startExoPlayer(playerView, videoSrc)
                }
                override fun onFailure(call: retrofit2.Call<GalleryHostResponse>, t: Throwable) {
                    loadingText.text = "获取媒体库信息失败: ${t.message}"
                }
            })
        } catch (e: Exception) {
            loadingText.text = "错误: ${e.message}"
        }
    }

    private fun startExoPlayer(playerView: PlayerView, videoUrl: String) {
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

    // ==================== PLAYLIST NAVIGATION ====================

    private fun playNext() {
        val playlist = currentPlaylist ?: return
        if (currentPlayIndex >= playlist.size - 1) {
            toast("已经是最后一个了")
            return
        }
        currentPlayIndex++
        val next = playlist[currentPlayIndex]
        android.util.Log.d("OneList", "playNext: index=$currentPlayIndex title='${next.title}'")
        toast("下一集: ${next.title ?: ""}")
        showPlayer(next.url, next.galleryUid, playlist, currentPlayIndex)
    }

    private fun playPrevious() {
        val playlist = currentPlaylist ?: return
        if (currentPlayIndex <= 0) {
            toast("已经是第一个了")
            return
        }
        currentPlayIndex--
        val prev = playlist[currentPlayIndex]
        android.util.Log.d("OneList", "playPrevious: index=$currentPlayIndex title='${prev.title}'")
        toast("上一集: ${prev.title ?: ""}")
        showPlayer(prev.url, prev.galleryUid, playlist, currentPlayIndex)
    }

    // ==================== TOP BAR ====================

    private fun buildTopBar(title: String, showSearch: Boolean, showLogout: Boolean): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(12), dp(24), dp(12))
            setBackgroundColor(Color.parseColor("#0d0d1a"))
        }

        // Back button (if not on home)
        if (currentScreen != Screen.HOME && currentScreen != Screen.LOGIN) {
            val backBtn = TextView(this).apply {
                text = "← 返回"
                setTextColor(Color.parseColor("#6366f1"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setPadding(dp(8), dp(4), dp(8), dp(4))
                applyTextFocus(Color.WHITE, Color.parseColor("#6366f1"))
                setOnClickListener { navigateBack() }
            }
            bar.addView(backBtn)
        }

        // Title
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val titleLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        titleLp.leftMargin = dp(8)
        bar.addView(titleView, titleLp)

        // Search button
        if (showSearch) {
            val searchBtn = TextView(this).apply {
                text = "🔍 搜索"
                setTextColor(Color.parseColor("#aaaaaa"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                isClickable = true
                isFocusable = true
                setPadding(dp(12), dp(4), dp(12), dp(4))
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                isClickable = true
                isFocusable = true
                setPadding(dp(12), dp(4), dp(12), dp(4))
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

    private fun navigateBack() {
        when (currentScreen) {
            Screen.LOGIN -> {} // Can't go back from login
            Screen.HOME -> finish()
            Screen.LIST -> showHome()
            Screen.DETAIL -> showHome() // Simplified; could track back stack
            Screen.SEARCH -> showHome()
            Screen.PLAYER -> showHome()
        }
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

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // ==================== HELPERS ====================

    /**
     * 为按钮设置焦点高亮样式（TV 遥控器模式）
     * - 聚焦后：放大 + 描边 + 背景变亮（默认放大 1.08x，3dp 白色描边）
     */
    private fun View.applyFocusGlow(defaultColor: Int = Color.parseColor("#6366f1"), focusedColor: Int = Color.parseColor("#8b8ef7"), strokeColor: Int = Color.WHITE) {
        val gd = GradientDrawable().apply {
            setColor(defaultColor)
            cornerRadius = dp(4).toFloat()
        }
        this.background = gd
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.08f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                val bg = (v.background as? GradientDrawable) ?: gd.also { v.background = it }
                bg.setColor(if (hasFocus) focusedColor else defaultColor)
                if (hasFocus) bg.setStroke(dp(3), strokeColor) else bg.setStroke(0, 0)
            } catch (e: Exception) {}
        }
    }

    /**
     * 卡片/列表项焦点高亮：放大 + 背景变色
     */
    private fun View.applyCardFocus() {
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.06f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                if (hasFocus) v.setBackgroundColor(Color.parseColor("#6366f1"))
                else v.setBackgroundColor(Color.parseColor("#1a1a2e"))
            } catch (e: Exception) {}
        }
    }

    /**
     * TextView 焦点样式（顶部导航文本按钮等）：聚焦后反色 + 描边 + 轻微放大
     */
    private fun TextView.applyTextFocus(focusedTextColor: Int = Color.WHITE, defaultTextColor: Int = Color.parseColor("#6366f1")) {
        this.setOnFocusChangeListener { v, hasFocus ->
            try {
                val scale = if (hasFocus) 1.05f else 1.0f
                v.animate().cancel()
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
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
