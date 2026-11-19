package com.rimon.smartfamily.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.rimon.smartfamily.MainActivity
import com.rimon.smartfamily.R
import com.rimon.smartfamily.SmartFamilyApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Opens the app on a route (shortcuts, widgets, the quick-settings tile). */
fun routeIntent(context: Context, route: String, code: Int): PendingIntent =
    PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Home-screen widget: what is still open on the shopping lists. */
class ShoppingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            runCatching {
                val c = (context.applicationContext as SmartFamilyApp).container
                val fam = c.settings.current().activeFamilyId
                val items = if (fam.isEmpty()) emptyList() else c.db.listItems().observeOpenInFamily(fam).first()
                val body = if (items.isEmpty()) context.getString(R.string.widget_shop_empty)
                else items.take(12).joinToString("\n") { "• " + it.name + if (it.quantity != 1.0) " ×" + it.quantity.toString().removeSuffix(".0") else "" } +
                    if (items.size > 12) "\n…+" + (items.size - 12) else ""
                ids.forEach { id -> manager.updateAppWidget(id, views(context, context.getString(R.string.widget_shop_title, items.size), body, "shopping", "quickadd")) }
            }
            pending.finish()
        }
    }
}

/** Home-screen widget: today — events, chores due and the Hebrew date. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        widgetScope.launch {
            runCatching {
                val c = (context.applicationContext as SmartFamilyApp).container
                val s = c.settings.current()
                val now = System.currentTimeMillis()
                val zone = java.time.ZoneId.of("Asia/Jerusalem")
                val today = java.time.LocalDate.now(zone)
                val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val info = runCatching { com.rimon.smartfamily.smart.JewishDays.info(today, com.rimon.smartfamily.smart.Cities.byKey(s.zmanimCity)) }.getOrNull()
                val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                val events = c.homeLife.expand(c.homeLife.allEventsForReminders().filter { s.activeFamilyId.isEmpty() || it.familyId == s.activeFamilyId }, now, end)
                    .sortedBy { it.start }.map { "🗓 " + fmt.format(it.start) + " " + it.event.title }
                val chores = c.homeLife.choresDueBefore(end).filter { s.activeFamilyId.isEmpty() || it.familyId == s.activeFamilyId }.map { "✔ " + it.title }
                val lines = (events + chores).take(10)
                val title = listOfNotNull(info?.hFull, info?.holidays?.firstOrNull()).joinToString(" · ").ifEmpty { context.getString(R.string.widget_today) }
                val body = lines.joinToString("\n").ifEmpty { context.getString(R.string.widget_today_empty) }
                ids.forEach { id -> manager.updateAppWidget(id, views(context, title, body, "calendar", "quickadd")) }
            }
            pending.finish()
        }
    }
}

private fun views(context: Context, title: String, body: String, open: String, plus: String) =
    RemoteViews(context.packageName, R.layout.widget_list).apply {
        setTextViewText(R.id.w_title, title)
        setTextViewText(R.id.w_body, body)
        setOnClickPendingIntent(R.id.w_root, routeIntent(context, open, open.hashCode()))
        setOnClickPendingIntent(R.id.w_add, routeIntent(context, plus, (open + plus).hashCode()))
    }

object Widgets {
    /** Refresh both widgets (after sync or a local change). Cheap when no widget is placed. */
    fun refresh(context: Context) {
        val m = AppWidgetManager.getInstance(context)
        listOf(ShoppingWidget::class.java, TodayWidget::class.java).forEach { cls ->
            val ids = m.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isNotEmpty()) context.sendBroadcast(Intent(context, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
        }
    }
}

/** Quick-settings tile: add something to the family in one tap. */
class QuickAddTile : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "quickadd").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 77, intent, PendingIntent.FLAG_IMMUTABLE))
        else @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
}
