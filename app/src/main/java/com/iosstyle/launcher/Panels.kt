package com.iosstyle.launcher

import android.app.Activity
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

fun tv(ctx: Context, t: String, sizeSp: Float, color: Int, bold: Boolean): TextView =
    TextView(ctx).apply {
        text = t
        textSize = sizeSp
        setTextColor(color)
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun fireNotif(sbn: StatusBarNotification) {
    try { sbn.notification.contentIntent?.send() } catch (e: PendingIntent.CanceledException) { }
    if ((sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0) {
        NotifService.instance?.cancelNotification(sbn.key)
    }
}

/** Kartu notifikasi kaca. hideText=true menyembunyikan isi (dipakai di layar kunci yang diamankan PIN/pola). */
fun makeNotifCard(ctx: Context, sbn: StatusBarNotification, hideText: Boolean, onClick: () -> Unit): View {
    val pm = ctx.packageManager
    val ex = sbn.notification.extras
    val label = try {
        pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
    } catch (e: Exception) {
        sbn.packageName
    }
    val title = if (hideText) "Notifikasi" else (ex.getCharSequence(Notification.EXTRA_TITLE) ?: "").toString()
    val body = if (hideText) "" else (ex.getCharSequence(Notification.EXTRA_TEXT) ?: "").toString()

    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Ui.glass(ctx, 22f, 0x40FFFFFF)
        setPadding(ctx.dp(12f), ctx.dp(10f), ctx.dp(14f), ctx.dp(10f))
        setOnClickListener { onClick() }
    }
    val iv = ImageView(ctx)
    try {
        iv.setImageBitmap(IosIcons.render(pm.getApplicationIcon(sbn.packageName), null, ctx.dp(38f)))
    } catch (e: Exception) { }
    row.addView(iv, LinearLayout.LayoutParams(ctx.dp(38f), ctx.dp(38f)))

    val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(12f), 0, 0, 0)
    }
    col.addView(tv(ctx, title.ifBlank { label }, 15f, Color.WHITE, true))
    if (body.isNotBlank()) col.addView(tv(ctx, body, 14f, 0xCCFFFFFF.toInt(), false))
    row.addView(col, LinearLayout.LayoutParams(0, WRAP, 1f))
    row.addView(tv(ctx, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(sbn.postTime)), 12f, 0x99FFFFFF.toInt(), false))
    return row
}

/** Pusat Notifikasi: turun dari atas saat status bar ditarik ke bawah. */
class NotificationCenter(private val act: Activity, private val root: FrameLayout) {
    private var view: FrameLayout? = null
    private var list: LinearLayout? = null
    private val listener: () -> Unit = { fill() }
    val isOpen: Boolean get() = view != null

    fun open(topInset: Int) {
        if (view != null) return
        val v = FrameLayout(act)
        v.setBackgroundColor(0xCC0A0A14.toInt())
        v.isClickable = true

        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(16f), topInset + act.dp(12f), act.dp(16f), act.dp(72f))
        }
        val head = LinearLayout(act).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(act, "Notifikasi", 30f, Color.WHITE, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        val clear = tv(act, "Hapus semua", 14f, Color.WHITE, true).apply {
            background = Ui.glass(act, 16f)
            setPadding(act.dp(14f), act.dp(7f), act.dp(14f), act.dp(7f))
            setOnClickListener { NotifService.instance?.cancelAllNotifications() }
        }
        head.addView(clear)
        col.addView(head)

        val l = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(act).apply {
            addView(l)
            isVerticalScrollBarEnabled = false
        }
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = act.dp(14f) })
        v.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))

        // Pegangan di bawah: geser ke atas atau ketuk untuk menutup.
        val handle = FrameLayout(act)
        handle.addView(
            View(act).apply {
                background = GradientDrawable().apply {
                    setColor(0x99FFFFFF.toInt())
                    cornerRadius = act.dp(3f).toFloat()
                }
            },
            FrameLayout.LayoutParams(act.dp(44f), act.dp(5f), Gravity.CENTER)
        )
        var y0 = 0f
        handle.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) y0 = e.y
            if (e.actionMasked == MotionEvent.ACTION_UP && (y0 - e.y > act.dp(30f) || abs(y0 - e.y) < act.dp(8f))) close()
            true
        }
        v.addView(handle, FrameLayout.LayoutParams(MATCH, act.dp(64f), Gravity.BOTTOM))

        root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        view = v
        list = l
        NotifService.listeners.add(listener)
        fill()
        v.translationY = -root.height.toFloat()
        v.animate().translationY(0f).setDuration(260).start()
    }

    private fun fill() {
        val l = list ?: return
        l.removeAllViews()
        val items = NotifService.current()
        if (NotifService.instance == null) {
            l.addView(tv(act, "Aktifkan akses notifikasi agar notifikasi tampil di sini.", 15f, 0xCCFFFFFF.toInt(), false))
            val btn = tv(act, "Buka pengaturan", 15f, Color.WHITE, true).apply {
                background = Ui.glass(act, 16f)
                setPadding(act.dp(16f), act.dp(9f), act.dp(16f), act.dp(9f))
                setOnClickListener {
                    try { act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) { }
                    close()
                }
            }
            l.addView(btn, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = act.dp(12f) })
        } else if (items.isEmpty()) {
            l.addView(tv(act, "Tidak ada notifikasi", 16f, 0x99FFFFFF.toInt(), false))
        } else {
            for (s in items) {
                l.addView(
                    makeNotifCard(act, s, false) { fireNotif(s); close() },
                    LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = act.dp(8f) }
                )
            }
        }
    }

    fun close() {
        val v = view ?: return
        view = null
        NotifService.listeners.remove(listener)
        v.animate().translationY(-root.height.toFloat()).setDuration(220).withEndAction { root.removeView(v) }.start()
    }
}

/** Pencarian aplikasi (mirip Spotlight): muncul saat layar utama ditarik ke bawah. */
class SearchOverlay(
    private val act: Activity,
    private val root: FrameLayout,
    private val apps: () -> List<AppItem>,
    private val launch: (AppItem, View) -> Unit
) {
    private var view: FrameLayout? = null
    val isOpen: Boolean get() = view != null

    fun open(topInset: Int) {
        if (view != null) return
        val v = FrameLayout(act)
        v.setBackgroundColor(0xE6101018.toInt())
        v.isClickable = true

        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(act.dp(16f), topInset + act.dp(12f), act.dp(16f), 0)
        }
        val input = EditText(act).apply {
            hint = "Cari"
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 17f
            isSingleLine = true
            background = Ui.glass(act, 14f)
            setPadding(act.dp(14f), act.dp(11f), act.dp(14f), act.dp(11f))
        }
        val l = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

        fun fill(q: String) {
            l.removeAllViews()
            val hits = apps().filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }.take(40)
            for (a in hits) {
                val r = LinearLayout(act).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, act.dp(8f), 0, act.dp(8f))
                }
                val iv = ImageView(act).apply { setImageBitmap(a.icon) }
                r.addView(iv, LinearLayout.LayoutParams(act.dp(44f), act.dp(44f)))
                r.addView(
                    tv(act, a.label, 17f, Color.WHITE, false),
                    LinearLayout.LayoutParams(WRAP, WRAP).apply { leftMargin = act.dp(14f) }
                )
                r.setOnClickListener { launch(a, iv); close() }
                l.addView(r)
            }
        }
        input.doAfterTextChanged { fill(it?.toString() ?: "") }

        col.addView(input, LinearLayout.LayoutParams(MATCH, WRAP))
        val scroll = ScrollView(act).apply {
            addView(l)
            isVerticalScrollBarEnabled = false
        }
        col.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = act.dp(10f) })
        v.addView(col, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        view = v
        fill("")
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(180).start()
        input.requestFocus()
        input.postDelayed({
            (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 200)
    }

    fun close() {
        val v = view ?: return
        view = null
        (act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(v.windowToken, 0)
        v.animate().alpha(0f).setDuration(150).withEndAction { root.removeView(v) }.start()
    }
}
