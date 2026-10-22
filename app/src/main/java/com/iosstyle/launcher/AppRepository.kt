package com.iosstyle.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap

class AppItem(
    val label: String,
    val pkg: String,
    val intent: Intent,
    val icon: Bitmap,
    val kind: IosIcons.Kind?
)

object AppRepository {
    /** Berat (render ikon): panggil dari thread latar. */
    fun load(ctx: Context, iconPx: Int): List<AppItem> {
        val pm = ctx.packageManager
        val kinds = IosIcons.detect(pm)
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val out = ArrayList<AppItem>()
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            val launch = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            val kind = if (pkg == ctx.packageName) IosIcons.Kind.SELF else kinds[pkg]
            val icon = IosIcons.render(ri.loadIcon(pm), kind, iconPx)
            out.add(AppItem(ri.loadLabel(pm).toString(), pkg, launch, icon, kind))
        }
        return out.sortedWith(compareBy<AppItem>({ sortKey(it) }, { it.label.lowercase() }))
    }

    private fun sortKey(a: AppItem): Int {
        val k = a.kind ?: return 50
        return if (k == IosIcons.Kind.SELF) 60 else k.ordinal
    }
}
