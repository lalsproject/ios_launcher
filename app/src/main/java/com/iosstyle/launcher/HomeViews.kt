package com.iosstyle.launcher

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/** Menangkap gestur tarik-ke-bawah di layar utama (untuk pencarian) tanpa mengganggu geser horizontal. */
class GestureFrame(ctx: Context) : FrameLayout(ctx) {
    var canPull: () -> Boolean = { true }
    var onPullDown: () -> Unit = {}
    private var x0 = 0f
    private var y0 = 0f
    private var fired = false

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { x0 = e.x; y0 = e.y; fired = false }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - y0
                if (!fired && canPull() && dy > context.dp(90f) && abs(e.x - x0) < dy / 2) {
                    fired = true
                    onPullDown()
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean = true
}

fun appCell(
    ctx: Context,
    a: AppItem,
    showLabel: Boolean,
    onClick: (AppItem, View) -> Unit,
    onLong: (AppItem) -> Unit
): View {
    val cell = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(ctx.dp(2f), ctx.dp(4f), ctx.dp(2f), ctx.dp(2f))
    }
    val iv = ImageView(ctx).apply {
        setImageBitmap(a.icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    cell.addView(iv, LinearLayout.LayoutParams(ctx.dp(60f), ctx.dp(60f)))
    if (showLabel) {
        val label = TextView(ctx).apply {
            text = a.label
            textSize = 11.5f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(ctx.dp(2f).toFloat(), 0f, ctx.dp(1f).toFloat(), 0x99000000.toInt())
        }
        cell.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(5f) })
    }
    cell.setOnClickListener { onClick(a, iv) }
    cell.setOnLongClickListener { onLong(a); true }
    cell.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
    return cell
}

/** Satu halaman = grid 4 x 6. Slot kosong diisi view kosong agar tinggi baris tetap sama. */
class PageAdapter(
    private val pages: List<List<AppItem>>,
    private val onClick: (AppItem, View) -> Unit,
    private val onLong: (AppItem) -> Unit
) : RecyclerView.Adapter<PageAdapter.VH>() {

    class VH(v: LinearLayout) : RecyclerView.ViewHolder(v)

    override fun getItemCount() = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val p = LinearLayout(parent.context)
        p.orientation = LinearLayout.VERTICAL
        p.weightSum = ROWS.toFloat()
        p.layoutParams = RecyclerView.LayoutParams(MATCH, MATCH)
        return VH(p)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val page = h.itemView as LinearLayout
        page.removeAllViews()
        val ctx = page.context
        for (r in 0 until ROWS) {
            val row = LinearLayout(ctx)
            row.weightSum = COLS.toFloat()
            for (c in 0 until COLS) {
                val a = pages[position].getOrNull(r * COLS + c)
                val v = if (a != null) appCell(ctx, a, true, onClick, onLong) else View(ctx)
                row.addView(v, LinearLayout.LayoutParams(0, MATCH, 1f))
            }
            page.addView(row, LinearLayout.LayoutParams(MATCH, 0, 1f))
        }
    }

    companion object {
        const val ROWS = 6
        const val COLS = 4
    }
}
