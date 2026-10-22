package com.iosstyle.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.max
import kotlin.math.min

class HomeActivity : Activity() {
    private lateinit var root: GestureFrame
    private lateinit var status: StatusBarView
    private lateinit var pager: ViewPager2
    private lateinit var dots: LinearLayout
    private lateinit var dock: LinearLayout
    private lateinit var nc: NotificationCenter
    private lateinit var search: SearchOverlay

    private var apps: List<AppItem> = emptyList()
    private var topInset = 0
    private var bottomInset = 0
    private val handler = Handler(Looper.getMainLooper())
    private val reloadTask = Runnable { reload() }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            // Update aplikasi memicu beberapa broadcast berurutan; satukan jadi satu reload.
            handler.removeCallbacks(reloadTask)
            handler.postDelayed(reloadTask, 600)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Ui.fullscreen(window)
        root = GestureFrame(this)
        setContentView(root)

        pager = ViewPager2(this)
        (pager.getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { updateDots(position) }
        })
        dots = LinearLayout(this)
        dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            weightSum = 4f
            background = Ui.glass(this@HomeActivity, 32f)
            setPadding(dp(10f), 0, dp(10f), 0)
        }
        status = StatusBarView(this)

        root.addView(pager, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(dots, FrameLayout.LayoutParams(WRAP, dp(10f), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        root.addView(
            dock,
            FrameLayout.LayoutParams(MATCH, dp(DOCK_H), Gravity.BOTTOM).apply {
                leftMargin = dp(14f)
                rightMargin = dp(14f)
            }
        )
        root.addView(status, FrameLayout.LayoutParams(MATCH, dp(24f), Gravity.TOP))

        nc = NotificationCenter(this, root)
        search = SearchOverlay(this, root, { apps }, this::launch)

        // Tarik status bar ke bawah = Pusat Notifikasi.
        status.setOnTouchListener(object : View.OnTouchListener {
            var y0 = 0f
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> y0 = e.y
                    MotionEvent.ACTION_MOVE -> if (e.y - y0 > dp(40f)) {
                        y0 = Float.MAX_VALUE
                        if (!nc.isOpen && !search.isOpen) {
                            nc.open(max(topInset, dp(24f)))
                            status.bringToFront()
                        }
                    }
                }
                return true
            }
        })
        // Tarik layar utama ke bawah = pencarian.
        root.canPull = { !nc.isOpen && !search.isOpen }
        root.onPullDown = { search.open(max(topInset, dp(24f))) }

        Ui.insets(root) { t, bt ->
            topInset = t
            bottomInset = bt
            applyInsets()
        }
        applyInsets()

        val f = IntentFilter()
        f.addAction(Intent.ACTION_PACKAGE_ADDED)
        f.addAction(Intent.ACTION_PACKAGE_REMOVED)
        f.addAction(Intent.ACTION_PACKAGE_REPLACED)
        f.addDataScheme("package")
        registerReceiver(pkgReceiver, f)

        if (Prefs.lockEnabled(this)) LockService.start(this)
        reload()
    }

    private fun applyInsets() {
        val t = max(topInset, dp(24f))
        val dockBottom = bottomInset + dp(10f)
        (status.layoutParams as FrameLayout.LayoutParams).height = t
        (dock.layoutParams as FrameLayout.LayoutParams).bottomMargin = dockBottom
        (dots.layoutParams as FrameLayout.LayoutParams).bottomMargin = dockBottom + dp(DOCK_H + 8f)
        (pager.layoutParams as FrameLayout.LayoutParams).apply {
            topMargin = t + dp(6f)
            bottomMargin = dockBottom + dp(DOCK_H + 26f)
        }
        root.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        Ui.fullscreen(window)
        root.background = Ui.wallpaper(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) Ui.fullscreen(window)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Tombol Home ditekan: tutup panel, kembali ke halaman pertama.
        if (search.isOpen) search.close()
        if (nc.isOpen) nc.close()
        if (pager.currentItem != 0) pager.setCurrentItem(0, true)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            search.isOpen -> search.close()
            nc.isOpen -> nc.close()
            else -> pager.setCurrentItem(0, true)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) { }
        super.onDestroy()
    }

    private fun reload() {
        val px = dp(60f)
        Thread {
            val list = AppRepository.load(this, px)
            runOnUiThread { apps = list; bind() }
        }.start()
    }

    private fun bind() {
        val dockKinds = listOf(
            IosIcons.Kind.PHONE, IosIcons.Kind.MESSAGES, IosIcons.Kind.BROWSER, IosIcons.Kind.CAMERA
        )
        val dockApps = dockKinds.mapNotNull { k -> apps.firstOrNull { it.kind == k } }
        val pages = apps.filter { it !in dockApps }
            .chunked(PageAdapter.ROWS * PageAdapter.COLS)
            .ifEmpty { listOf(emptyList()) }

        val cur = pager.currentItem
        pager.adapter = PageAdapter(pages, this::launch, this::showMenu)
        pager.setCurrentItem(min(cur, pages.size - 1), false)

        dock.removeAllViews()
        for (a in dockApps) {
            dock.addView(appCell(this, a, false, this::launch, this::showMenu), LinearLayout.LayoutParams(0, MATCH, 1f))
        }

        dots.removeAllViews()
        if (pages.size > 1) {
            for (i in pages.indices) {
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }
                }
                dots.addView(dot, LinearLayout.LayoutParams(dp(7f), dp(7f)).apply {
                    leftMargin = dp(4f)
                    rightMargin = dp(4f)
                })
            }
            updateDots(pager.currentItem)
        }
    }

    private fun updateDots(sel: Int) {
        for (i in 0 until dots.childCount) dots.getChildAt(i).alpha = if (i == sel) 1f else 0.4f
    }

    private fun launch(a: AppItem, v: View) {
        try {
            val o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height)
            startActivity(a.intent, o.toBundle())
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka ${a.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(a: AppItem) {
        AlertDialog.Builder(this)
            .setTitle(a.label)
            .setItems(arrayOf("Info aplikasi", "Hapus aplikasi")) { _, which ->
                val u = Uri.parse("package:${a.pkg}")
                try {
                    if (which == 0) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, u))
                    else startActivity(Intent(Intent.ACTION_DELETE, u))
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak bisa membuka", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    companion object {
        private const val DOCK_H = 92f
    }
}
