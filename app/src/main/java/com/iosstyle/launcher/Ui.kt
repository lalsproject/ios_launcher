package com.iosstyle.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window

const val MATCH = -1
const val WRAP = -2

fun Context.dp(v: Float): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(v: Float): Int = context.dp(v)

object Ui {
    /** Permukaan kaca ala Liquid Glass: isi transparan dan garis tepi tipis. */
    fun glass(ctx: Context, radiusDp: Float, fill: Int = 0x33FFFFFF): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = ctx.dp(radiusDp).toFloat()
            setStroke(ctx.dp(1f), 0x59FFFFFF)
        }

    /** Wallpaper bawaan. Null = biarkan wallpaper sistem terlihat (windowShowWallpaper). */
    fun wallpaper(ctx: Context): GradientDrawable? =
        if (!Prefs.builtinWallpaper(ctx)) null
        else GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF2B1B6B.toInt(), 0xFF4B6CF0.toInt(), 0xFFFF7EB3.toInt(), 0xFFFFB86B.toInt())
        )

    /** Status bar asli disembunyikan (diganti StatusBarView); layout tetap memanjang di bawah nav bar. */
    @Suppress("DEPRECATION")
    fun fullscreen(w: Window) {
        w.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        if (Build.VERSION.SDK_INT >= 29) w.isNavigationBarContrastEnforced = false
    }

    /** Callback menerima tinggi status bar (min 24dp) dan tinggi nav bar bawah. */
    @Suppress("DEPRECATION")
    fun insets(v: View, cb: (Int, Int) -> Unit) {
        v.setOnApplyWindowInsetsListener { _, i ->
            cb(maxOf(i.stableInsetTop, v.dp(24f)), i.systemWindowInsetBottom)
            i
        }
        v.requestApplyInsets()
    }
}
