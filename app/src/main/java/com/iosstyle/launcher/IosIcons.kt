package com.iosstyle.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Semua ikon dipotong ke bentuk squircle (superellipse n=5) seperti ikon iOS.
 * Aplikasi sistem yang umum (telepon, pesan, kamera, dll.) digambar ulang dengan glyph buatan sendiri;
 * aplikasi lain memakai ikon aslinya (layer adaptive dipakai full-bleed).
 */
object IosIcons {
    enum class Kind { CALENDAR, PHOTOS, CAMERA, CLOCK, MAIL, CALC, SETTINGS, PHONE, MESSAGES, BROWSER, SELF }

    fun detect(pm: PackageManager): Map<String, Kind> {
        val out = HashMap<String, Kind>()
        fun add(i: Intent, k: Kind) {
            val pkg = pm.resolveActivity(i, 0)?.activityInfo?.packageName ?: return
            if (pkg != "android") out.putIfAbsent(pkg, k)
        }
        fun cat(c: String, k: Kind) = add(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c), k)
        add(Intent(Intent.ACTION_DIAL), Kind.PHONE)
        cat(Intent.CATEGORY_APP_MESSAGING, Kind.MESSAGES)
        cat(Intent.CATEGORY_APP_BROWSER, Kind.BROWSER)
        add(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), Kind.CAMERA)
        cat(Intent.CATEGORY_APP_GALLERY, Kind.PHOTOS)
        cat(Intent.CATEGORY_APP_CALENDAR, Kind.CALENDAR)
        cat(Intent.CATEGORY_APP_EMAIL, Kind.MAIL)
        cat(Intent.CATEGORY_APP_CALCULATOR, Kind.CALC)
        add(Intent(AlarmClock.ACTION_SHOW_ALARMS), Kind.CLOCK)
        add(Intent(Settings.ACTION_SETTINGS), Kind.SETTINGS)
        return out
    }

    private val masks = HashMap<Int, Bitmap>()

    private fun squircle(s: Int): Path {
        val p = Path()
        val r = s / 2.0
        val steps = 160
        for (i in 0..steps) {
            val t = i * 2 * PI / steps
            val cs = cos(t)
            val sn = sin(t)
            val x = r + r * sign(cs) * abs(cs).pow(0.4)
            val y = r + r * sign(sn) * abs(sn).pow(0.4)
            if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat())
        }
        p.close()
        return p
    }

    private fun mask(s: Int): Bitmap = masks.getOrPut(s) {
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(b).drawPath(squircle(s), Paint(Paint.ANTI_ALIAS_FLAG))
        b
    }

    @Synchronized
    fun render(icon: Drawable, kind: Kind?, s: Int): Bitmap {
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (kind != null) glyph(c, kind, s) else app(c, icon, s)
        // DST_IN dengan bitmap mask supaya tepi squircle halus (clipPath pada bitmap tidak anti-alias).
        c.drawBitmap(mask(s), 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
        return bmp
    }

    private fun app(c: Canvas, d: Drawable, s: Int) {
        c.drawColor(Color.WHITE)
        if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
            // Area terlihat adaptive icon = 2/3 layer, jadi layer diperbesar 1.5x agar memenuhi squircle.
            val o = (s * 0.25f).toInt()
            val b = Rect(-o, -o, s + o, s + o)
            d.background?.let { it.setBounds(b.left, b.top, b.right, b.bottom); it.draw(c) }
            d.foreground?.let { it.setBounds(b.left, b.top, b.right, b.bottom); it.draw(c) }
        } else {
            val i = (s * 0.13f).toInt()
            d.setBounds(i, i, s - i, s - i)
            d.draw(c)
        }
    }

    private fun paint(color: Int, style: Paint.Style = Paint.Style.FILL, w: Float = 0f) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.style = style
            strokeWidth = w
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private fun bg(c: Canvas, s: Int, top: Int, bottom: Int) {
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, s.toFloat(), top, bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), p)
    }

    private fun hand(c: Canvas, m: Float, deg: Float, len: Float, w: Float, col: Int) {
        c.save()
        c.rotate(deg, m, m)
        c.drawLine(m, m, m, m - len, paint(col, Paint.Style.STROKE, w))
        c.restore()
    }

    private fun glyph(c: Canvas, k: Kind, s: Int) {
        val f = s.toFloat()
        val m = f / 2
        val white = paint(Color.WHITE)
        when (k) {
            Kind.MESSAGES -> {
                bg(c, s, 0xFF6EEB83.toInt(), 0xFF1DB954.toInt())
                c.drawOval(RectF(f * .16f, f * .2f, f * .84f, f * .7f), white)
                val tail = Path()
                tail.moveTo(f * .3f, f * .6f); tail.lineTo(f * .2f, f * .8f); tail.lineTo(f * .46f, f * .68f); tail.close()
                c.drawPath(tail, white)
            }
            Kind.PHONE -> {
                bg(c, s, 0xFF6EEB83.toInt(), 0xFF1DB954.toInt())
                val cx = f * .78f; val cy = f * .22f; val r = f * .55f
                c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 90f, 90f, false, paint(Color.WHITE, Paint.Style.STROKE, f * .15f))
                c.drawCircle(cx - r, cy, f * .1f, white)
                c.drawCircle(cx, cy + r, f * .1f, white)
            }
            Kind.CAMERA -> {
                bg(c, s, 0xFFE5E5EA.toInt(), 0xFF8E8E93.toInt())
                val dark = paint(0xFF1C1C1E.toInt())
                c.drawRoundRect(RectF(f * .34f, f * .22f, f * .62f, f * .34f), f * .03f, f * .03f, dark)
                c.drawRoundRect(RectF(f * .14f, f * .3f, f * .86f, f * .76f), f * .09f, f * .09f, dark)
                c.drawCircle(m, f * .53f, f * .17f, paint(0xFF3A3A3C.toInt()))
                c.drawCircle(m, f * .53f, f * .17f, paint(0xFF8E8E93.toInt(), Paint.Style.STROKE, f * .02f))
                c.drawCircle(m, f * .53f, f * .09f, paint(0xFF0A1A3A.toInt()))
            }
            Kind.SETTINGS -> {
                bg(c, s, 0xFFD1D1D6.toInt(), 0xFF8E8E93.toInt())
                val g = paint(0xFF3A3A3C.toInt())
                for (i in 0 until 8) {
                    c.save()
                    c.rotate(i * 45f, m, m)
                    c.drawRoundRect(RectF(m - f * .055f, f * .15f, m + f * .055f, f * .32f), f * .02f, f * .02f, g)
                    c.restore()
                }
                c.drawCircle(m, m, f * .26f, g)
                c.drawCircle(m, m, f * .1f, paint(0xFFA8A8AD.toInt()))
            }
            Kind.CLOCK -> {
                c.drawColor(Color.BLACK)
                c.drawCircle(m, m, f * .42f, white)
                val tick = paint(0xFF1C1C1E.toInt(), Paint.Style.STROKE, f * .02f)
                for (i in 0 until 12) {
                    c.save()
                    c.rotate(i * 30f, m, m)
                    c.drawLine(m, m - f * .38f, m, m - f * (if (i % 3 == 0) .31f else .34f), tick)
                    c.restore()
                }
                val now = Calendar.getInstance()
                val min = now.get(Calendar.MINUTE)
                hand(c, m, (now.get(Calendar.HOUR) + min / 60f) * 30f, f * .2f, f * .035f, 0xFF1C1C1E.toInt())
                hand(c, m, min * 6f, f * .31f, f * .025f, 0xFF1C1C1E.toInt())
                hand(c, m, now.get(Calendar.SECOND) * 6f, f * .33f, f * .012f, 0xFFFF9F0A.toInt())
                c.drawCircle(m, m, f * .025f, paint(0xFFFF9F0A.toInt()))
            }
            Kind.CALC -> {
                c.drawColor(0xFF1C1C1E.toInt())
                val sz = f * .17f
                val gap = f * .045f
                val x0 = (f - (4 * sz + 3 * gap)) / 2
                for (r in 0 until 4) for (q in 0 until 4) {
                    val col = when {
                        q == 3 -> 0xFFFF9F0A.toInt()
                        r == 0 -> 0xFFA5A5A5.toInt()
                        else -> 0xFF333333.toInt()
                    }
                    val l = x0 + q * (sz + gap)
                    val t = x0 + r * (sz + gap)
                    c.drawRoundRect(RectF(l, t, l + sz, t + sz), sz / 2, sz / 2, paint(col))
                }
            }
            Kind.BROWSER -> {
                bg(c, s, 0xFF64D2FF.toInt(), 0xFF0A84FF.toInt())
                c.drawCircle(m, m, f * .36f, paint(Color.WHITE, Paint.Style.STROKE, f * .03f))
                c.save()
                c.rotate(45f, m, m)
                val red = Path()
                red.moveTo(m, m - f * .3f); red.lineTo(m - f * .075f, m); red.lineTo(m + f * .075f, m); red.close()
                val tailP = Path()
                tailP.moveTo(m, m + f * .3f); tailP.lineTo(m - f * .075f, m); tailP.lineTo(m + f * .075f, m); tailP.close()
                c.drawPath(red, paint(0xFFFF3B30.toInt()))
                c.drawPath(tailP, white)
                c.restore()
            }
            Kind.PHOTOS -> {
                c.drawColor(Color.WHITE)
                val cols = longArrayOf(0xFFFFCC00, 0xFFFF9500, 0xFFFF3B30, 0xFFFF2D92, 0xFFAF52DE, 0xFF5856D6, 0xFF32ADE6, 0xFF34C759)
                for (i in 0 until 8) {
                    val p = paint(cols[i].toInt())
                    p.alpha = 215
                    p.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                    c.save()
                    c.rotate(i * 45f, m, m)
                    c.drawOval(RectF(m - f * .1f, m - f * .36f, m + f * .1f, m - f * .02f), p)
                    c.restore()
                }
            }
            Kind.CALENDAR -> {
                c.drawColor(Color.WHITE)
                val now = Date()
                val loc = Locale.getDefault()
                val dow = SimpleDateFormat("EEE", loc).format(now).uppercase(loc)
                val day = SimpleDateFormat("d", loc).format(now)
                val tp = paint(0xFFFF3B30.toInt())
                tp.textAlign = Paint.Align.CENTER
                tp.textSize = f * .16f
                tp.typeface = Typeface.DEFAULT_BOLD
                c.drawText(dow, m, f * .3f, tp)
                tp.color = 0xFF1C1C1E.toInt()
                tp.textSize = f * .56f
                tp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                c.drawText(day, m, f * .8f, tp)
            }
            Kind.MAIL -> {
                bg(c, s, 0xFF64D2FF.toInt(), 0xFF0A84FF.toInt())
                c.drawRoundRect(RectF(f * .16f, f * .28f, f * .84f, f * .72f), f * .06f, f * .06f, white)
                val v = Path()
                v.moveTo(f * .2f, f * .34f); v.lineTo(m, f * .56f); v.lineTo(f * .8f, f * .34f)
                c.drawPath(v, paint(0xFF0A84FF.toInt(), Paint.Style.STROKE, f * .035f))
            }
            Kind.SELF -> {
                bg(c, s, 0xFF5E5CE6.toInt(), 0xFF0A84FF.toInt())
                val a = f * .2f
                val g = f * .06f
                val x0 = (f - (2 * a + g)) / 2
                for (r in 0..1) for (q in 0..1) {
                    val l = x0 + q * (a + g)
                    val t = x0 + r * (a + g)
                    c.drawRoundRect(RectF(l, t, l + a, t + a), a * .3f, a * .3f, white)
                }
            }
        }
    }
}
