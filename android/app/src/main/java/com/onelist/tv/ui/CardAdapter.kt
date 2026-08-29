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
import com.onelist.tv.data.Movie
import com.onelist.tv.data.RetrofitClient
import com.onelist.tv.data.Tv

class CardAdapter(
    private val items: List<Any>,
    private val type: String,
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    // ---- 焦点/正常背景 StateListDrawable：按状态自动切换，避免 onFocusChange 每次 new ----
    companion object {
        /** 构造卡片背景：focused=主题色填充，normal=透明；统一圆角 8dp，构造一次复用 */
        private fun makeCardBg(ctx: android.content.Context): StateListDrawable {
            val r = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 8f, ctx.resources.displayMetrics
            )
            val focused = GradientDrawable().apply {
                cornerRadius = r
                setColor(Color.parseColor("#6366f1"))
            }
            val normal = GradientDrawable().apply {
                cornerRadius = r
                setColor(Color.TRANSPARENT)
            }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), focused)
                addState(intArrayOf(), normal)
            }
        }

        /** 占位图：纯色圆角矩形（与 TMDB 卡片尺寸无关，bind 时共享同一个对象引用） */
        private fun makePlaceholder(): GradientDrawable {
            return GradientDrawable().apply {
                setColor(Color.parseColor("#1a1a2e"))
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
            // StateListDrawable：系统按 state_focused 自动切换，不再每次 new GradientDrawable
            background = makeCardBg(ctx)
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

        // 焦点变化：只做 scale + bringToFront（不再创建新 Drawable）
        card.setOnFocusChangeListener { v, hasFocus ->
            val target = if (hasFocus) 1.08f else 1f
            if (v.scaleX != target) {
                v.animate().cancel()
                v.animate().scaleX(target).scaleY(target).setDuration(120).start()
            }
            if (hasFocus) v.bringToFront()
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

        titleView.text = if (itemTitle.isNullOrEmpty()) "(未知)" else itemTitle

        // TMDB 刮削封面优先，其次自定义封面（/custom-image/{id}）
        val url: String? = RetrofitClient.imageUrl(posterPath)
            ?: RetrofitClient.customImageUrl(itemId)

        val placeholder = makePlaceholder()
        val targetW = (card.layoutParams as? RecyclerView.LayoutParams)?.width
            ?: poster.layoutParams?.width ?: dp(poster.context, 140)
        val targetH = poster.layoutParams?.height ?: dp(poster.context, 210)

        if (url != null) {
            // 全部统一走 Glide：
            //   - override(width,height) → 解码前按目标尺寸下采样，省内存/省时间
            //   - DiskCacheStrategy.ALL → 源文件+下采样都缓存，滚动更流畅
            //   - custom-image 走 GlideModule 的 imageOkHttpClient，带 Authorization
            Glide.with(poster)
                .load(url)
                .override(targetW, targetH)
                .centerCrop()
                .placeholder(placeholder)
                .error(placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(poster)
        } else {
            Glide.with(poster).clear(poster)
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
