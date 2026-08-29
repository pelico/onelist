package com.onelist.tv

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.onelist.tv.data.RetrofitClient
import java.io.InputStream

/**
 * Glide 使用图片专用 OkHttpClient：带 Authorization 头（/custom-image 等私有端点需要 token），
 * 但不带 BODY 日志 / token-refresh JSON 解析，减少每一次图片请求的开销。
 * 302 重定向由 OkHttp 默认自动跟随。
 */
@GlideModule
class GlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val factory = OkHttpUrlLoader.Factory(RetrofitClient.imageOkHttpClient)
        registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }
}
