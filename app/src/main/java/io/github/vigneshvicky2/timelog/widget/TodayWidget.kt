package io.github.vigneshvicky2.timelog.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.vigneshvicky2.timelog.MainActivity
import io.github.vigneshvicky2.timelog.R
import io.github.vigneshvicky2.timelog.formatDuration
import io.github.vigneshvicky2.timelog.reminder.Reminders

/**
 * Home-screen widget: today's logged time against 8h, with a "Log" button that jumps straight
 * to Add time. The app pushes fresh numbers whenever entries or Jira data change.
 */
class TodayWidget : AppWidgetProvider() {
    data class Summary(val logged: Long, val pending: Long, val target: Long)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val s = if (prefs.getString("day", null) == today)
            Summary(prefs.getLong("logged", 0), prefs.getLong("pending", 0), prefs.getLong("target", 8 * 3600L))
        else Summary(0, 0, 8 * 3600L)
        ids.forEach { manager.updateAppWidget(it, views(context, s)) }
    }

    companion object {
        private const val PREFS = "widget"

        /** Saves today's numbers and redraws every placed widget. Cheap; safe to call often. */
        fun update(context: Context, s: Summary) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("day", java.time.LocalDate.now().toString())
                .putLong("logged", s.logged).putLong("pending", s.pending).putLong("target", s.target)
                .apply()
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { manager.updateAppWidget(it, views(context, s)) }
        }

        private fun views(context: Context, s: Summary): RemoteViews {
            val done = s.logged + s.pending
            val left = s.target - done
            val open = PendingIntent.getActivity(
                context, 10, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val log = PendingIntent.getActivity(
                context, 11,
                Intent(context, MainActivity::class.java).setAction(Reminders.ACTION_LOG)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.widget_total, formatDuration(done))
                setTextViewText(
                    R.id.widget_sub,
                    when {
                        left > 0 -> "${formatDuration(left)} to go today"
                        left == 0L -> "Day complete"
                        else -> "${formatDuration(-left)} over 8h"
                    } + if (s.pending > 0) " · ${formatDuration(s.pending)} pending" else "",
                )
                setProgressBar(R.id.widget_bar, 100, ((done * 100) / s.target).toInt().coerceIn(0, 100), false)
                setOnClickPendingIntent(R.id.widget_root, open)
                setOnClickPendingIntent(R.id.widget_log, log)
            }
        }
    }
}
