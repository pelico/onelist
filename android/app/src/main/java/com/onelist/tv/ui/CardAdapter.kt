package com.onelist.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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

class CardAdapter(
    private val items: List<Any>,
    private val type: String,
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    companion object {
        // 共享 OkHttpClient，复用 Retrofit 的客户端（含拦截器）
        private val okHttpClient: OkHttpClient by lazy { RetrofitClient.okHttpClient }
    }

    class CardViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val ctx = parent.context
        val cardWidth = dp(ctx, 140)
        val cardHeight = dp(ctx, 210)

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6))
            isClickable = true
            isFocusable = true
        }

        val poster = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
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

        // Focus background color block (matching search button style)
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
            v.scaleX = if (hasFocus) 1.05f else 1f
            v.scaleY = if (hasFocus) 1.05f else 1f
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
                android.util.Log.w("OneList", "Card[$position] Unknown item type: ${item::class.java.name}")
            }
        }

        val displayTitle = if (itemTitle.isNullOrEmpty()) "(未知)" else itemTitle
        titleView.text = displayTitle

        // Try scraped poster first, fall back to custom image (same logic as detail page)
        val url = RetrofitClient.imageUrl(posterPath) ?: RetrofitClient.customImageUrl(itemId)

        val placeholder = GradientDrawable().apply {
            setColor(Color.parseColor("#1a1a2e"))
            cornerRadius = 4f
        }

        if (!url.isNullOrEmpty()) {
            try {
                // 优先用 OkHttp 直接取字节，绕过 Glide HTTP 层对重定向/响应头的处理问题
                okHttpClient.newCall(okhttp3.Request.Builder().url(url).get().build()).enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        android.util.Log.e("OneList", "Card OkHttp fetch failed url=$url: ${e.message}")
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                            poster.setImageDrawable(null)
                        }
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        val body = response.body?.bytes()
                        if (body != null && body.isNotEmpty()) {
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                try {
                                    Glide.with(poster)
                                        .load(ByteArrayInputStream(body))
                                        .placeholder(placeholder)
                                        .error(placeholder)
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                        .into(poster)
                                } catch (ex: Exception) {
                                    android.util.Log.e("OneList", "Card Glide decode failed: ${ex.message}")
                                    poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                                    poster.setImageDrawable(null)
                                }
                            }
                        } else {
                            android.util.Log.w("OneList", "Card OkHttp empty body url=$url code=${response.code}")
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                                poster.setImageDrawable(null)
                            }
                        }
                    }
                })
            } catch (e: Exception) {
                android.util.Log.e("OneList", "Card image load failed: ${e.message}")
                poster.setBackgroundColor(Color.parseColor("#1a1a2e"))
                poster.setImageDrawable(null)
            }
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
