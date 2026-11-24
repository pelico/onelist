package com.onelist.tv

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/**
 * 设计 Token —— 全 app 视觉参数的唯一来源，调风格只改这个文件。
 *
 * 尺寸约定：
 *  - Ui.dp()/Ui.sp() 等价于原 MainActivity 的 tvDp/tvSp（TV 放大 1.5/1.25 倍）
 *  - CardAdapter 等真实像素场景继续用本地 dp()，取值仍引用本文件常量
 */
object Ui {

    // ==================== 颜色 ====================
    object c {
        // 背景 / 容器
        val bg        = Color.parseColor("#0d0d1a")   // 页面背景
        val bgGlass   = Color.parseColor("#cc0d0d1a") // 模糊背景暗化层（80% bg）
        val card      = Color.parseColor("#1a1a2e")   // 卡片 / 输入框 / 行底
        val cardHover = Color.parseColor("#2d2d55")   // 行 / 按钮焦点高亮底
        val cardAlt   = Color.parseColor("#333355")   // 次级面板 / 图片占位
        val cardGlass = Color.parseColor("#cc1a1a2e") // 半透明卡片浮层
        val scrim     = Color.parseColor("#B0000000") // 全屏遮罩

        // 品牌色
        val accent      = Color.parseColor("#6366f1")
        val accentLight = Color.parseColor("#8b8ef7")
        val cyan        = Color.parseColor("#22d3ee") // 正在播放强调
        val teal        = Color.parseColor("#0e7490") // 正在播放底色

        // 语义色
        val red     = Color.parseColor("#e50914")
        val warn    = Color.parseColor("#EA9600")
        val success = Color.parseColor("#2E7D32")

        // 文字（三级灰）
        val text1 = Color.parseColor("#FFFFFF")
        val text2 = Color.parseColor("#aaaaaa")
        val text3 = Color.parseColor("#888888")
    }

    // ==================== 尺寸 ====================
    /** TV 放大间距（×1.5），等价原 tvDp */
    fun dp(base: Int): Int = (base * 1.5f).toInt()

    /** TV 放大字号（×1.25），等价原 tvSp */
    fun sp(base: Float): Float = base * 1.25f

    // 间距刻度：只允许 4 的倍数
    const val SPACE_XS  = 4
    const val SPACE_SM  = 8
    const val SPACE_MD  = 12
    const val SPACE_LG  = 16
    const val SPACE_XL  = 24
    const val SPACE_XXL = 32

    // 字号层级（设计值）
    const val FONT_CAPTION  = 13f
    const val FONT_BODY     = 16f
    const val FONT_SUBTITLE = 18f
    const val FONT_TITLE    = 22f
    const val FONT_HEADLINE = 28f
    const val FONT_HERO     = 48f

    // 圆角
    const val RADIUS_SM = 4
    const val RADIUS_MD = 8
    const val RADIUS_LG = 12

    // ==================== 焦点效果（全 app 统一） ====================
    private val rowFocusCache = HashMap<Int, android.graphics.drawable.Drawable.ConstantState>()

    private fun makeRowFocusBg(normalBg: Int): StateListDrawable {
        val r = dp(RADIUS_MD).toFloat()
        val focused = GradientDrawable().apply {
            cornerRadius = r
            setColor(c.cardHover)
            setStroke(dp(2), c.accent)
        }
        val normal = GradientDrawable().apply {
            cornerRadius = r
            setColor(normalBg)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focused)
            addState(intArrayOf(), normal)
        }
    }

    /**
     * 列表行 / 卡片统一焦点形态：
     *  - normal：normalBg 圆角底
     *  - focused：cardHover 高亮底 + accent 描边
     * 缩放统一 1.05（网格间距已加大，不会互相遮挡）
     * 每个调用方拿到的都是 mutate 后的独立实例，避免 RecyclerView 复用时状态串扰
     */
    fun rowFocusBg(normalBg: Int): android.graphics.drawable.Drawable {
        val cs = rowFocusCache.getOrPut(normalBg) { makeRowFocusBg(normalBg).constantState!! }
        return cs.newDrawable()?.mutate() ?: makeRowFocusBg(normalBg)
    }

    /** 统一焦点缩放：1.05 缩放；translationZ 提层（不能用 bringToFront——会重排
     *  LinearLayout children 导致详情页按钮互换位置；translationZ 只改绘制层级） */
    fun scaleOnFocus(v: android.view.View) {
        val target = if (v.isFocused) 1.05f else 1f
        if (v.scaleX != target) {
            v.animate().cancel()
            v.animate().scaleX(target).scaleY(target).setDuration(120).start()
        }
        androidx.core.view.ViewCompat.setTranslationZ(v, if (v.isFocused) 10f else 0f)
    }
}
