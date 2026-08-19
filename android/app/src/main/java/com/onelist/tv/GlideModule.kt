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
 * Glide 使用与 Retrofit 相同的 OkHttpClient（含 auth/logging 拦截器），
 * 确保 /custom-image/{id} 等端点的 302 重定向和响应头被正确处理。
 */
@GlideModule
class GlideModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val factory = OkHttpUrlLoader.Factory(RetrofitClient.okHttpClient)
        registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }

    // 不调用 super.clearLogCategory()，保留 Glide 默认日志级别
}
