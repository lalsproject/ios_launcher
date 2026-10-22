package com.iosstyle.launcher

import android.app.Activity
import android.app.KeyguardManager
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
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.notification.StatusBarNotification
import android.text.format.DateFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Layar kunci gaya iOS. Tampil di atas keyguard sistem; geser ke atas untuk membuka.
 * Jika perangkat memakai PIN/pola/sidik jari, sistem tetap memintanya setelah geser ke atas.
 */
class LockActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var notifBox: LinearLayout
    private lateinit var torchBtn: GlassButton
    private var pending: StatusBarNotification? = null
    private var torchOn = false

    private val listener: () -> Unit = { fillNotifs() }
    private val tick = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { updateTime() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        Ui.fullscreen(window)
        build()
    }

    private fun label(t: String, size: Float, color: Int): TextView = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setShadowLayer(dp(6f).toFloat(), 0f, dp(1f).toFloat(), 0x33000000)
    }

    private fun build() {
        val frame = Frame(this)
        frame.background = Ui.wallpaper(this)
        content = FrameLayout(this)
        frame.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(frame)

        val status = StatusBarView(this)
        content.addView(status, FrameLayout.LayoutParams(MATCH, dp(24f), Gravity.TOP))

        // Tanggal + jam besar. Mode ringkas mengecilkan jam, seperti opsi jam kecil di iOS 27.
        date = label("", 20f, 0xE6FFFFFF.toInt()).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        clock = label("", if (Prefs.compactClock(this)) 46f else 100f, 0xF2FFFFFF.toInt()).apply {
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            includeFontPadding = false
        }
        notifBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(date)
        col.addView(clock)
        col.addView(notifBox, LinearLayout.LayoutParams(MATCH, WRAP).apply {
            topMargin = dp(28f)
            leftMargin = dp(16f)
            rightMargin = dp(16f)
        })
        content.addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP).apply { topMargin = dp(48f) })

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        bottom.addView(label("Geser ke atas untuk membuka", 13f, 0xB3FFFFFF.toInt()))
        val bar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xE6FFFFFF.toInt())
                cornerRadius = dp(3f).toFloat()
            }
        }
        bottom.addView(bar, LinearLayout.LayoutParams(dp(134f), dp(5f)).apply { topMargin = dp(10f) })
        content.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM).apply { bottomMargin = dp(8f) })

        torchBtn = GlassButton(this, false)
        torchBtn.setOnClickListener { toggleTorch() }
        val cam = GlassButton(this, true)
        cam.setOnClickListener { openCamera() }
        content.addView(torchBtn, FrameLayout.LayoutParams(dp(52f), dp(52f), Gravity.BOTTOM or Gravity.START).apply {
            leftMargin = dp(40f)
            bottomMargin = dp(80f)
        })
        content.addView(cam, FrameLayout.LayoutParams(dp(52f), dp(52f), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = dp(40f)
            bottomMargin = dp(80f)
        })

        Ui.insets(frame) { t, bt ->
            status.layoutParams.height = t
            (col.layoutParams as FrameLayout.LayoutParams).topMargin = t + dp(24f)
            (bottom.layoutParams as FrameLayout.LayoutParams).bottomMargin = bt + dp(8f)
            (torchBtn.layoutParams as FrameLayout.LayoutParams).bottomMargin = bt + dp(72f)
            (cam.layoutParams as FrameLayout.LayoutParams).bottomMargin = bt + dp(72f)
            content.requestLayout()
        }
        updateTime()
    }

    /** Geser ke atas: konten ikut jari, lepas melewati 20% tinggi layar = buka. */
    private inner class Frame(ctx: Context) : FrameLayout(ctx) {
        private var y0 = 0f
        private var dragging = false

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                y0 = e.y
                dragging = false
            } else if (e.actionMasked == MotionEvent.ACTION_MOVE && y0 - e.y > context.dp(14f)) {
                dragging = true
            }
            return dragging
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> y0 = e.y
                MotionEvent.ACTION_MOVE -> {
                    val dy = max(0f, y0 - e.y)
                    content.translationY = -dy
                    content.alpha = 1f - 0.8f * min(1f, dy / height)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    if (y0 - e.y > height * 0.2f) unlock(null)
                    else content.animate().translationY(0f).alpha(1f).setDuration(180).start()
                }
            }
            return true
        }
    }

    private fun unlock(thenOpen: StatusBarNotification?) {
        pending = thenOpen
        content.animate()
            .translationY(-content.height.toFloat())
            .alpha(0f)
            .setDuration(160)
            .withEndAction { dismiss() }
            .start()
    }

    private fun dismiss() {
        if (Build.VERSION.SDK_INT >= 26) {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    pending?.let { fireNotif(it) }
                    finish()
                }
                override fun onDismissCancelled() { restore() }
                override fun onDismissError() { finish() }
            })
        } else {
            // Android 7.x: keyguard tanpa kunci dibuka langsung; yang diamankan muncul setelah activity ini ditutup.
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
            pending?.let { fireNotif(it) }
            finish()
        }
    }

    private fun restore() {
        pending = null
        content.animate().translationY(0f).alpha(1f).setDuration(180).start()
    }

    private fun updateTime() {
        val now = Date()
        val pat = if (DateFormat.is24HourFormat(this)) "H:mm" else "h:mm"
        clock.text = SimpleDateFormat(pat, Locale.getDefault()).format(now)
        date.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    }

    private fun fillNotifs() {
        notifBox.removeAllViews()
        val all = NotifService.current()
        // Di perangkat berkunci, isi notifikasi disembunyikan.
        val secure = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardSecure
        for (s in all.take(3)) {
            notifBox.addView(
                makeNotifCard(this, s, secure) { unlock(s) },
                LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(8f) }
            )
        }
        if (all.size > 3) {
            notifBox.addView(label("+${all.size - 3} notifikasi lainnya", 14f, 0xCCFFFFFF.toInt()))
        }
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cm.cameraIdList.firstOrNull {
                val ch = cm.getCameraCharacteristics(it)
                ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: return
            torchOn = !torchOn
            cm.setTorchMode(id, torchOn)
            torchBtn.active = torchOn
        } catch (e: Exception) {
            torchOn = false
            torchBtn.active = false
        }
    }

    private fun openCamera() {
        try {
            startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) { }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter()
        f.addAction(Intent.ACTION_TIME_TICK)
        f.addAction(Intent.ACTION_TIME_CHANGED)
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(tick, f)
        NotifService.listeners.add(listener)
        updateTime()
        fillNotifs()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(tick) } catch (e: Exception) { }
        NotifService.listeners.remove(listener)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        pending = null
        content.translationY = 0f
        content.alpha = 1f
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { /* layar kunci tidak bisa ditutup dengan Back */ }
}

/** Tombol bulat kaca di sudut bawah layar kunci: senter dan kamera. */
class GlassButton(ctx: Context, private val camera: Boolean) : View(ctx) {
    var active = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        p.style = Paint.Style.FILL
        p.color = if (active) 0xF2FFFFFF.toInt() else 0x4DFFFFFF
        c.drawCircle(cx, cy, width / 2f, p)
        p.color = if (active) 0xFF1C1C1E.toInt() else Color.WHITE
        if (camera) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2f * d
            c.drawRoundRect(RectF(cx - 12 * d, cy - 7 * d, cx + 12 * d, cy + 9 * d), 4 * d, 4 * d, p)
            c.drawCircle(cx, cy + d, 4.5f * d, p)
            p.style = Paint.Style.FILL
            c.drawRoundRect(RectF(cx - 4 * d, cy - 11 * d, cx + 4 * d, cy - 7 * d), 1.5f * d, 1.5f * d, p)
        } else {
            val head = Path()
            head.moveTo(cx - 8 * d, cy - 12 * d)
            head.lineTo(cx + 8 * d, cy - 12 * d)
            head.lineTo(cx + 5 * d, cy - 5 * d)
            head.lineTo(cx - 5 * d, cy - 5 * d)
            head.close()
            c.drawPath(head, p)
            c.drawRoundRect(RectF(cx - 5 * d, cy - 4 * d, cx + 5 * d, cy + 13 * d), 2.5f * d, 2.5f * d, p)
        }
    }
}
