package com.onelist.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.onelist.tv.data.Movie
import com.onelist.tv.data.RetrofitClient
import com.onelist.tv.data.Tv
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

class CardAdapter(
    private val items: List<Any>,
    private val type: String,
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    companion object {
        // 专用图片客户端：无拦截器，避免 authInterceptor 给图片请求加 Authorization 头
        private val imageClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
        }
        private val mainHandler = Handler(Looper.getMainLooper())
    }

    /**
     * GridLayoutManager 均匀间距装饰器
     * 替代 RecyclerView.LayoutParams.setMargins()（在 GridLayoutManager 下不可靠）
     * @param spanCount 列数
     * @param spacingPx 间距像素值（item 之间 + 边缘）
     * @param includeEdge 是否在 RecyclerView 边缘也加间距
     */
    class GridSpacingItemDecoration(
        private val spanCount: Int,
        private val spacingPx: Int,
        private val includeEdge: Boolean
    ) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return
            val column = position % spanCount
            if (includeEdge) {
                outRect.left = spacingPx - column * spacingPx / spanCount
                outRect.right = (column + 1) * spacingPx / spanCount
                if (position < spanCount) outRect.top = spacingPx
                outRect.bottom = spacingPx
            } else {
                outRect.left = column * spacingPx / spanCount
                outRect.right = spacingPx - (column + 1) * spacingPx / spanCount
                if (position >= spanCount) outRect.top = spacingPx
            }
        }
    }

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val ctx = parent.context
        val cardWidth = dp(ctx, 140)
        val cardHeight = dp(ctx, 210)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
            isClickable = true
            isFocusable = true
        }

        val poster = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cardHeight
            )
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }
        card.addView(poster)

        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        card.addView(title)

        val normalBg = GradientDrawable().apply {
            cornerRadius = dp(ctx, 8).toFloat()
            setColor(Color.TRANSPARENT)
        }
        card.background = normalBg

        card.setOnFocusChangeListener { v, hasFocus ->
            val bg = GradientDrawable().apply {
                cornerRadius = dp(ctx, 8).toFloat()
                setColor(if (hasFocus) Color.parseColor("#6366f1") else Color.TRANSPARENT)
            }
            v.background = bg
            v.scaleX = if (hasFocus) 1.08f else 1f
            v.scaleY = if (hasFocus) 1.08f else 1f
            v.bringToFront()
        }

        return CardViewHolder(card)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        val card = holder.itemView as LinearLayout
        val poster = card.getChildAt(0) as ImageView
        val titleView = card.getChildAt(1) as TextView

        val itemTitle: String?
        val posterPath: String?
        val itemId: Int?

        when (item) {
            is Movie -> {
                itemTitle = item.title ?: item.originalTitle
                posterPath = item.posterPath
                itemId = item.id
            }
            is Tv -> {
                itemTitle = item.name ?: item.originalName
                posterPath = item.posterPath
                itemId = item.id
            }
            else -> {
                itemTitle = "?"
                posterPath = null
                itemId = null
                Log.w("OneList", "Card[$position] Unknown item type: ${item::class.java.name}")
            }
        }

        val displayTitle = if (itemTitle.isNullOrEmpty()) "(未知)" else itemTitle
        titleView.text = displayTitle

        val scrapedUrl = RetrofitClient.imageUrl(posterPath)
        val customUrl = RetrofitClient.customImageUrl(itemId)

        val placeholder = GradientDrawable().apply {
            setColor(Color.parseColor("#1a1a2e"))
            cornerRadius = 4f
        }

        // 刮削封面：Glide 直接加载 URL（原始方案，稳定可靠）
        if (!scrapedUrl.isNullOrEmpty()) {
            Glide.with(poster)
                .load(scrapedUrl)
                .placeholder(placeholder)
                .error(placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(poster)
        } else if (!customUrl.isNullOrEmpty()) {
            // 自定义封面：OkHttp 取字节 + BitmapFactory 直接解码，完全绕过 Glide
            Log.d("OneList", "Card custom image loading url=$customUrl")
            imageClient.newCall(okhttp3.Request.Builder().url(customUrl).get().build())
                .enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        Log.e("OneList", "Card custom image fetch failed url=$customUrl: ${e.message}")
                        mainHandler.post {
                            poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                            poster.setImageDrawable(null)
                        }
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val code = response.code
                        val contentType = response.header("Content-Type") ?: "null"
                        Log.d("OneList", "Card custom image response code=$code contentType=$contentType url=$customUrl")
                        val body = response.body?.bytes()
                        Log.d("OneList", "Card custom image body size=${body?.size ?: 0} url=$customUrl")
                        if (body != null && body.isNotEmpty()) {
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(body, 0, body.size)
                            Log.d("OneList", "Card custom image bitmap=${if (bitmap != null) "${bitmap.width}x${bitmap.height}" else "null"} url=$customUrl")
                            mainHandler.post {
                                if (bitmap != null) {
                                    poster.setImageBitmap(bitmap)
                                    poster.setBackgroundColor(Color.TRANSPARENT)
                                } else {
                                    Log.e("OneList", "Card custom image decode returned null url=$customUrl")
                                    poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                                    poster.setImageDrawable(null)
                                }
                            }
                        } else {
                            Log.w("OneList", "Card custom image empty body url=$customUrl code=$code")
                            mainHandler.post {
                                poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                                poster.setImageDrawable(null)
                            }
                        }
                    }
                })
        } else {
            poster.setBackgroundColor(Color.parseColor("#2a2a4e"))
            poster.setImageDrawable(null)
        }

        card.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    private fun dp(ctx: android.content.Context, value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()
    }
}
