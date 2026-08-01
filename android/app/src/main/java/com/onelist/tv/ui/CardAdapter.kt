package com.onelist.tv

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.onelist.tv.data.Movie
import com.onelist.tv.data.RetrofitClient
import com.onelist.tv.data.Tv

class CardAdapter(
    private val items: List<Any>,
    private val type: String, // "movie" or "tv"
    private val onClick: (Any) -> Unit
) : RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

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

        // Poster image
        val poster = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(cardWidth, cardHeight)
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }
        card.addView(poster)

        // Title text
        val title = TextView(ctx).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            maxLines = 2
            setPadding(0, dp(ctx, 4), 0, 0)
        }
        card.addView(title)

        // Focus effect
        card.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundColor(Color.parseColor("#6366f1"))
                v.scaleX = 1.05f
                v.scaleY = 1.05f
            } else {
                v.setBackgroundColor(Color.TRANSPARENT)
                v.scaleX = 1f
                v.scaleY = 1f
            }
        }

        return CardViewHolder(card)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val item = items[position]
        val card = holder.itemView as LinearLayout
        val poster = card.getChildAt(0) as ImageView
        val title = card.getChildAt(1) as TextView

        val itemTitle: String?
        val posterPath: String?

        when (item) {
            is Movie -> {
                itemTitle = item.title
                posterPath = item.poster
            }
            is Tv -> {
                itemTitle = item.title
                posterPath = item.poster
            }
            else -> {
                itemTitle = "?"
                posterPath = null
            }
        }

        title.text = itemTitle ?: ""
        val url = RetrofitClient.imageUrl(posterPath)
        if (url != null) {
            Glide.with(poster.context).load(url).into(poster)
        } else {
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
