package com.onelist.tv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences

class App : Application() {

    companion object {
        private lateinit var instance: App
        private lateinit var prefs: SharedPreferences

        var serverUrl: String?
            get() = prefs.getString("server_url", "http://192.168.3.66:5245")
            set(value) { prefs.edit().putString("server_url", value).apply() }

        var token: String?
            get() = prefs.getString("auth_token", null)
            set(value) { prefs.edit().putString("auth_token", value).apply() }

        var username: String?
            get() = prefs.getString("username", "qqqq@qq.com")
            set(value) { prefs.edit().putString("username", value).apply() }

        var userId: Int?
            get() {
                val id = prefs.getInt("user_id", -1)
                return if (id == -1) null else id
            }
            set(value) {
                if (value != null) prefs.edit().putInt("user_id", value).apply()
                else prefs.edit().remove("user_id").apply()
            }

        fun isLoggedIn(): Boolean = token != null && serverUrl != null

        fun logout() {
            prefs.edit()
                .remove("auth_token")
                .remove("username")
                .remove("user_id")
                .apply()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences("onelist_tv", Context.MODE_PRIVATE)
    }
}
