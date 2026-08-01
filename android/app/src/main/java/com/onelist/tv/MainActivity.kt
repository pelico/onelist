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
import com.google.android.exoplayer2.ui.PlayerView
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
            setBackgroundColor(Color.parseColor("#6366f1"))
            setPadding(dp(40), dp(14), dp(40), dp(14))
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
                    if (body != null && body.code == 200 && body.token != null) {
                        App.token = body.token
                        App.userId = body.user?.id
                        App.username = username
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
                        android.util.Log.d("OneList", "Body is null")
                    }
                    
                    if (body != null && body.code == 200 && body.data != null) {
                        loading.visibility = View.GONE
                        renderHomeData(layout, body.data!!)
                    } else {
                        val errorMsg = buildString {
                            append("加载失败\n")
                            append("HTTP: ${response.code()}\n")
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

        // Gallery rows
        if (data.galleries != null) {
            for (gallery in data.galleries) {
                val type = if (gallery.isTv == true) "tv" else "movie"
                val items = data.galleryItems?.get(gallery.galleryUid) ?: emptyList()
                if (items.isNotEmpty()) {
                    val row = buildContentRow(gallery.title ?: "媒体库", type, gallery.galleryUid)
                    parent.addView(row)
                    val mappedItems = items.map { item ->
                        if (type == "tv") {
                            Tv(id = item.id, name = item.displayTitle, posterPath = item.posterPath)
                        } else {
                            Movie(id = item.id, title = item.displayTitle, posterPath = item.posterPath)
                        } as Any
                    }
                    val recyclerView = buildHorizontalCardList(mappedItems, type)
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
            dp(220)
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
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            visibility = View.GONE
            setOnClickListener {
                if (!isLoadingMore && hasMorePages) {
                    currentPage++
                    loadMoreItems(recyclerView, it as Button)
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
        val posterUrl = RetrofitClient.imageUrl(movie.poster)
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(270))
            if (posterUrl != null) {
                Glide.with(this@MainActivity).load(posterUrl).into(this)
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
            text = movie.title ?: ""
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
            setBackgroundColor(Color.parseColor("#6366f1"))
            setPadding(dp(32), dp(12), dp(32), dp(12))
            setOnClickListener {
                if (movie.url != null) {
                    showPlayer(movie.url!!, movie.galleryUid)
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
        val posterUrl = RetrofitClient.imageUrl(tv.poster)
        val posterView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(dp(180), dp(270))
            if (posterUrl != null) {
                Glide.with(this@MainActivity).load(posterUrl).into(this)
            }
        }
        contentLayout.addView(posterView)

        // Info
        val infoLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, 0, 0)
        }

        val titleView = TextView(this).apply {
            text = tv.title ?: ""
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
                                    setBackgroundColor(Color.parseColor("#1a1a2e"))
                                    setPadding(dp(16), dp(10), dp(16), dp(10))
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

        for (ep in episodes) {
            val epBtn = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(Color.parseColor("#1a1a2e"))
                setPadding(dp(16), dp(12), dp(16), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (ep.url != null) {
                        showPlayer(ep.url!!, ep.galleryUid)
                    } else {
                        toast("暂无播放源")
                    }
                }
                setOnFocusChangeListener { v, hasFocus ->
                    v.setBackgroundColor(if (hasFocus) Color.parseColor("#6366f1") else Color.parseColor("#1a1a2e"))
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
            setBackgroundColor(Color.parseColor("#6366f1"))
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
                dp(220 * ((movies.size + 4) / 5))
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
                dp(220 * ((tvs.size + 4) / 5))
            ))
        }
    }

    // ==================== PLAYER SCREEN ====================

    private fun showPlayer(url: String, galleryUid: String?) {
        currentScreen = Screen.PLAYER
        rootLayout.removeAllViews()

        val videoUrl = RetrofitClient.videoUrl(url, galleryUid)
        if (videoUrl == null) {
            toast("无效的播放地址")
            showHome()
            return
        }

        val playerView = PlayerView(this).apply {
            fillParent()
            useController = true
        }

        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val mediaItem = MediaItem.fromUri(videoUrl)
            exo.setMediaItem(mediaItem)
            exo.playWhenReady = true
            exo.prepare()
        }

        rootLayout.addView(playerView)
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
                setOnClickListener { navigateBack() }
                setOnFocusChangeListener { v, hasFocus ->
                    (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#6366f1"))
                }
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
                setOnClickListener { showSearch() }
                setOnFocusChangeListener { v, hasFocus ->
                    (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#aaaaaa"))
                }
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
                setOnClickListener {
                    App.logout()
                    showLogin()
                }
                setOnFocusChangeListener { v, hasFocus ->
                    (v as TextView).setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#888888"))
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            navigateBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    // ==================== HELPERS ====================

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
