package com.mitvremote.app

import android.content.Context
import com.mitvremote.app.tv.TvDevice

/** Remembers paired TVs and settings. */
class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("mitv", Context.MODE_PRIVATE)

    fun paired(): List<TvDevice> =
        (p.getStringSet("paired", emptySet()) ?: emptySet()).mapNotNull {
            val i = it.indexOf('|')
            if (i < 0) null else TvDevice(it.substring(0, i), it.substring(i + 1))
        }.sortedBy { it.name }

    fun isPaired(host: String): Boolean = paired().any { it.host == host }

    fun addPaired(tv: TvDevice) {
        val set = (p.getStringSet("paired", emptySet()) ?: emptySet())
            .filterNot { it.startsWith(tv.host + "|") }.toMutableSet()
        set.add(tv.host + "|" + tv.name)
        p.edit().putStringSet("paired", set).apply()
    }

    fun forget(host: String) {
        val set = (p.getStringSet("paired", emptySet()) ?: emptySet())
            .filterNot { it.startsWith("$host|") }.toSet()
        p.edit().putStringSet("paired", set).apply()
        if (lastHost() == host) p.edit().remove("last").apply()
    }

    fun setLast(host: String) = p.edit().putString("last", host).apply()
    fun lastHost(): String? = p.getString("last", null)

    var volumeKeys: Boolean
        get() = p.getBoolean("volkeys", true)
        set(v) = p.edit().putBoolean("volkeys", v).apply()
}
