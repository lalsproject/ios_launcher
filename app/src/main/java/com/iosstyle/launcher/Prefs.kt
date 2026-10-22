package com.iosstyle.launcher

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ios_launcher", Context.MODE_PRIVATE)

    fun lockEnabled(c: Context) = sp(c).getBoolean("lock", false)
    fun setLockEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("lock", v).apply()

    fun builtinWallpaper(c: Context) = sp(c).getBoolean("wall", true)
    fun setBuiltinWallpaper(c: Context, v: Boolean) = sp(c).edit().putBoolean("wall", v).apply()

    fun compactClock(c: Context) = sp(c).getBoolean("compact", false)
    fun setCompactClock(c: Context, v: Boolean) = sp(c).edit().putBoolean("compact", v).apply()
}
