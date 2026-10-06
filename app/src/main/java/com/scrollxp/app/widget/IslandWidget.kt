package com.scrollxp.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.scrollxp.app.MainActivity
import com.scrollxp.app.R
import com.scrollxp.app.data.ScrollDatabase
import com.scrollxp.app.data.packages
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.domain.Worlds
import com.scrollxp.app.ui.drawIsland
import com.scrollxp.app.ui.theme.islandPalette
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.ZoneId

class IslandWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateLater(context)
    private fun updateLater(context: Context) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { refresh(context) } } catch (_: Exception) {
                // Keep the previous widget if Android cannot deliver this update.
            } finally { pending.finish() }
        }
    }
    companion object {
        suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, IslandWidget::class.java))
            if (ids.isEmpty()) return@withContext
            val dao = ScrollDatabase.get(context).dao()
            val p = dao.profile(); val xp = dao.totalXp()
            val treasures = dao.chests().filter { it.openedAt != null && Treasures.find(it.itemId) != null }.map { it.itemId }.toSet()
            val bitmap = Bitmap.createBitmap(480, 312, Bitmap.Config.ARGB_8888)
            try {
                val palette = islandPalette(true)
                CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(android.graphics.Canvas(bitmap)), Size(480f,312f)) {
                    drawIsland(xp,p?.roof ?: 0,p?.hiddenItems.orEmpty().packages(),treasures,true,palette.sky,Worlds.active(p?.region ?: Worlds.STARTER,xp).id)
                }
                val views = RemoteViews(context.packageName,R.layout.island_widget)
                views.setTextViewText(R.id.widget_title, if (p?.onboarded == true) p.islandName else "Your first island awaits")
                views.setImageViewBitmap(R.id.widget_art,bitmap)
                views.setTextViewText(R.id.widget_progress,if (p?.onboarded == true) "Level ${Progression.level(xp).level} · $xp XP" else "Tap to create your island")
                val detailed = context.getSharedPreferences("widget_preferences",Context.MODE_PRIVATE).getBoolean("detail",false)
                views.setViewVisibility(R.id.widget_detail,if (detailed) View.VISIBLE else View.GONE)
                if (detailed && p != null) {
                    val today = LocalDate.now(ZoneId.of(p.zone.ifBlank { ZoneId.systemDefault().id }))
                    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
                    val count = dao.goalDates().count { runCatching { LocalDate.parse(it) in monday..today.minusDays(1) }.getOrDefault(false) }
                    views.setTextViewText(R.id.widget_detail,"Balance week · $count / 5 days")
                }
                val intent = Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                manager.updateAppWidget(ids,views)
            } finally { bitmap.recycle() }
        }
    }
}
