package com.iosstyle.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Status bar gaya iOS: jam di kiri; sinyal, Wi-Fi, dan baterai (dengan persen di dalam) di kanan. */
class StatusBarView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private var time = ""
    private var level = 100
    private var charging = false
    private var bars = 4
    private var wifi = false
    private var phoneListener: PhoneStateListener? = null
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) readBattery(i) else refreshTime()
            invalidate()
        }
    }

    private fun readBattery(i: Intent) {
        val l = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        level = (l * 100f / sc).roundToInt().coerceIn(0, 100)
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun refreshTime() {
        val pat = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        time = SimpleDateFormat(pat, Locale.getDefault()).format(Date())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val f = IntentFilter()
        f.addAction(Intent.ACTION_BATTERY_CHANGED)
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(receiver, f)?.let { readBattery(it) }
        refreshTime()
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val l = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(s: SignalStrength) {
                    bars = s.level
                    postInvalidate()
                }
            }
            phoneListener = l
            tm.listen(l, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
        } catch (e: Exception) { /* perangkat tanpa telepon */ }
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
                    wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    postInvalidate()
                }
                override fun onLost(n: Network) {
                    wifi = false
                    postInvalidate()
                }
            }
            netCallback = cb
            cm.registerDefaultNetworkCallback(cb)
        } catch (e: Exception) { /* tanpa izin jaringan */ }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try { context.unregisterReceiver(receiver) } catch (e: Exception) { }
        try {
            phoneListener?.let {
                (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager)
                    .listen(it, PhoneStateListener.LISTEN_NONE)
            }
        } catch (e: Exception) { }
        try {
            netCallback?.let {
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .unregisterNetworkCallback(it)
            }
        } catch (e: Exception) { }
    }

    private fun baseline(cy: Float): Float {
        val fm = p.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f

        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.typeface = medium
        p.textSize = 15f * d
        p.textAlign = Paint.Align.LEFT
        c.drawText(time, 26f * d, baseline(cy), p)

        // Baterai
        val bw = 26f * d
        val bh = 12.5f * d
        val tip = 2f * d
        val right = width - 24f * d - tip - 1.2f * d
        val body = RectF(right - bw, cy - bh / 2f, right, cy + bh / 2f)
        val r = 4f * d
        p.color = 0x59FFFFFF
        c.drawRoundRect(body, r, r, p)
        c.save()
        val clip = Path()
        clip.addRoundRect(body, r, r, Path.Direction.CW)
        c.clipPath(clip)
        p.color = when {
            charging -> 0xFF30D158.toInt()
            level <= 20 -> 0xFFFF453A.toInt()
            else -> Color.WHITE
        }
        c.drawRect(body.left, body.top, body.left + bw * level / 100f, body.bottom, p)
        c.restore()
        p.color = 0x66FFFFFF
        c.drawRoundRect(RectF(right + 1.2f * d, cy - 2.3f * d, right + 1.2f * d + tip, cy + 2.3f * d), d, d, p)
        p.color = Color.BLACK
        p.typeface = Typeface.DEFAULT_BOLD
        p.textSize = 9.5f * d
        p.textAlign = Paint.Align.CENTER
        c.drawText(level.toString(), body.centerX(), baseline(cy), p)

        // Wi-Fi
        var x = body.left - 9f * d
        if (wifi) {
            val wx = x - 8f * d
            val wy = cy + 4f * d
            p.typeface = medium
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * d
            p.strokeCap = Paint.Cap.ROUND
            p.color = Color.WHITE
            for (rad in floatArrayOf(4f, 7.5f, 11f)) {
                c.drawArc(RectF(wx - rad * d, wy - rad * d, wx + rad * d, wy + rad * d), 225f, 90f, false, p)
            }
            p.style = Paint.Style.FILL
            c.drawCircle(wx, wy, 1.5f * d, p)
            x = wx - 8f * d
        }

        // Sinyal seluler
        p.style = Paint.Style.FILL
        val barW = 3f * d
        val gap = 1.5f * d
        var bx = x - 5f * d - (4 * barW + 3 * gap)
        for (i in 0 until 4) {
            val hh = (4f + i * 2.5f) * d
            p.color = if (i < bars) Color.WHITE else 0x66FFFFFF
            c.drawRoundRect(RectF(bx, cy + 5.5f * d - hh, bx + barW, cy + 5.5f * d), d, d, p)
            bx += barW + gap
        }
    }
}
