package com.wavebox.app

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("wavebox", Context.MODE_PRIVATE)

    var playQuality: Int
        get() = sp.getInt("pq", 160)
        set(v) = sp.edit().putInt("pq", v).apply()

    var dlQuality: Int
        get() = sp.getInt("dq", 320)
        set(v) = sp.edit().putInt("dq", v).apply()

    /** null = default (Music/Wavebox on internal storage) */
    var folder: String?
        get() = sp.getString("folder", null)
        set(v) = sp.edit().putString("folder", v).apply()

    var embed: Boolean
        get() = sp.getBoolean("embed", true)
        set(v) = sp.edit().putBoolean("embed", v).apply()

    var animateBg: Boolean
        get() = sp.getBoolean("animbg", true)
        set(v) = sp.edit().putBoolean("animbg", v).apply()

    /** 0 = follow system, 1 = light, 2 = dark */
    var themeMode: Int
        get() = sp.getInt("theme", 0)
        set(v) = sp.edit().putInt("theme", v).apply()

    /** "m4a" or "mp3" */
    var dlFormat: String
        get() = sp.getString("dlfmt", "m4a") ?: "m4a"
        set(v) = sp.edit().putString("dlfmt", v).apply()

    /** notification permission is asked only once */
    var askedNotif: Boolean
        get() = sp.getBoolean("asked_notif", false)
        set(v) = sp.edit().putBoolean("asked_notif", v).apply()
}
