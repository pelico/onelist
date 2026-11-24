package com.onelist.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
import com.onelist.tv.Ui
import com.onelist.tv.data.Movie
import com.onelist.tv.data.RetrofitClient
import com.onelist.tv.data.Tv

class CardAdapter(
    private val items: List<Any>,
    private val type: String,
    private val onClick: (Any) -> Unit,
    /** 卡片获得焦点时回调其海报 URL（首页模糊背景跟随用；其他页面传 null 不回调） */
    private val onFocused: ((String?) -> Unit)? = null
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    // ---- 焦点背景统一使用 Ui.rowFocusBg（cardHover+accent 描边），无状态串扰 ----
    companion object {
        /** 占位图：纯色圆角矩形（与 TMDB 卡片尺寸无关，bind 时共享同一个对象引用） */
        private fun makePlaceholder(): GradientDrawable {
            return GradientDrawable().apply {
                setColor(Ui.c.card)
                cornerRadius = 4f
            }
        }
    }

    /**
     * GridLayoutManager 均匀间距装饰器
     * 替代 RecyclerView.LayoutParams.setMargins()（在 GridLayoutManager 下不可靠）
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
            // 统一焦点形态：normal=透明，focused=cardHover+accent 描边
            background = Ui.rowFocusBg(Color.TRANSPARENT)
        }

        val poster = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, cardHeight
            )
            setBackgroundColor(Ui.c.card)
        }
        card.addView(poster)

        val title = TextView(ctx).apply {
            setTextColor(Ui.c.text1)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        card.addView(title)

        return CardViewHolder(card)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        val card = holder.itemView as LinearLayout
        val poster = card.getChildAt(0) as ImageView
        val titleView = card.getChildAt(1) as TextView

        val itemTitle: String?
        val posterPath: String?
        val customPoster: String?
        val itemId: Int?

        when (item) {
            is Movie -> {
                itemTitle = item.title ?: item.originalTitle
                posterPath = item.posterPath
                customPoster = item.customPoster
                itemId = item.id
            }
            is Tv -> {
                itemTitle = item.name ?: item.originalName
                posterPath = item.posterPath
                customPoster = item.customPoster
                itemId = item.id
            }
            else -> {
                itemTitle = "?"
                posterPath = null
                customPoster = null
                itemId = null
                Log.w("OneList", "Card[$position] Unknown item type: ${item::class.java.name}")
            }
        }

        titleView.text = if (itemTitle.isNullOrEmpty()) "(未知)" else itemTitle

        // 封面优先级与 Web 端一致：文件夹自定义封面(cover.jpg) → TMDB 刮削海报 → 随机占位图
        val url: String? = RetrofitClient.posterUrl(posterPath, customPoster, itemId)

        val placeholder = makePlaceholder()
        val targetW = (card.layoutParams as? RecyclerView.LayoutParams)?.width
            ?: poster.layoutParams?.width ?: dp(poster.context, 140)
        val targetH = poster.layoutParams?.height ?: dp(poster.context, 210)

        if (url != null) {
            // 自定义封面：URL 现在带 ?t=SESSION_NONCE（启动级时间戳）
            //   - 同一次启动内命中 SOURCE 缓存（磁盘+内存），返回首页时瞬间回显
            //   - 下次启动 URL 变了，自动走 picture/ 目录里的新文件 → 不会残留旧图
            // TMDB 刮削封面（/t/p/w220_...）：文件名即内容哈希，继续用 DiskCacheStrategy.ALL
            val isCustom = url.contains("/custom-image/", ignoreCase = true)
            val reqBuilder = Glide.with(poster)
                .load(url)
                .override(targetW, targetH)
                .centerCrop()
                .placeholder(placeholder)
                .error(placeholder)
            // Glide 4.x: SOURCE 在 4.x 更名为 DATA（按源文件字节缓存，不做 transform 的磁盘缓存）
            if (isCustom) reqBuilder.diskCacheStrategy(DiskCacheStrategy.DATA)
            else reqBuilder.diskCacheStrategy(DiskCacheStrategy.ALL)
            reqBuilder.into(poster)
        } else {
            Glide.with(poster).clear(poster)
            poster.setBackgroundColor(Ui.c.cardAlt)
            poster.setImageDrawable(null)
        }

        card.setOnClickListener { onClick(item) }

        // 焦点变化：统一 Ui.scaleOnFocus（1.05 缩放 + bringToFront）；
        // 获得焦点时回调海报 URL（bind 时闭包持有当前 item 的 url）
        card.setOnFocusChangeListener { v, hasFocus ->
            Ui.scaleOnFocus(v)
            if (hasFocus) onFocused?.invoke(url)
        }
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
