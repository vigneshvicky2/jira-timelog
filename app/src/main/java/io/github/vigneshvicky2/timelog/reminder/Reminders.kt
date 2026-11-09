package io.github.vigneshvicky2.timelog.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.vigneshvicky2.timelog.MainActivity
import io.github.vigneshvicky2.timelog.R
import io.github.vigneshvicky2.timelog.data.Store
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminders {
    private const val CHANNEL = "timelog_reminders"
    private const val REQUEST = 4201
    private const val NOTIFICATION_ID = 7001

    private fun LocalDate.onWeekend() = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY

    /** Next moment the reminder should fire, or null when it's turned off. */
    fun nextTrigger(store: Store, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        if (!store.reminderEnabled) return null
        var t = now.toLocalDate().atTime(store.reminderHour, store.reminderMinute)
        if (!t.isAfter(now)) t = t.plusDays(1)
        while (store.reminderWeekdaysOnly && t.toLocalDate().onWeekend()) t = t.plusDays(1)
        return t
    }

    /** (Re)schedules the single upcoming alarm; cancels it when reminders are off. */
    fun schedule(context: Context) {
        val store = Store(context)
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(context)
        am.cancel(pi)
        val at = nextTrigger(store) ?: return
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, REQUEST, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Posts a reminder. [index] picks the message; by default it rotates by date. */
    fun show(context: Context, index: Int = ReminderMessages.indexFor(LocalDate.now())) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily time log reminder", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A daily nudge to log your Jira time"
            }
        )
        val (title, body) = ReminderMessages.all[index.mod(ReminderMessages.all.size)]
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setColor(0xFF6C5CE7.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    fun isWeekend(d: LocalDate) = d.onWeekend()
}

/** Fires at the chosen time, shows today's message, then schedules tomorrow's. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val today = LocalDate.now()
        if (store.reminderEnabled && !(store.reminderWeekdaysOnly && Reminders.isWeekend(today))) {
            Reminders.show(context)
        }
        Reminders.schedule(context)
    }
}

/** Alarms are cleared on reboot / app update / clock change, so put the next one back. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Reminders.schedule(context)
}

object ReminderMessages {
    fun indexFor(d: LocalDate) = (d.toEpochDay() % all.size).toInt()

    val all: List<Pair<String, String>> = listOf(
        "⏰ Timesheet o'clock" to "Your hours won't log themselves. Two minutes now saves a Friday-evening panic.",
        "🧾 Jira is waiting" to "Somewhere a Jira ticket is wondering what you did today. Go tell it.",
        "🦸 Hero move" to "Log today's time while it's still fresh in your head. Future you says thanks.",
        "🍵 Chai + timelog" to "Grab a chai, open Timelog, tap a few chips. Done before it cools down.",
        "📅 Your meetings are ready" to "Today's meetings are already lined up as suggestions. Just tap and log.",
        "🎯 8 hours, accounted for" to "Fill today's ring before you sign off. It's oddly satisfying.",
        "🧠 Memory check" to "What did you work on today? Quick, log it before it blurs into tomorrow.",
        "🚀 Speedrun time" to "Can you log today in under 30 seconds? Timer starts… now.",
        "🐢 Don't let it pile up" to "One day is a minute of logging. Three days is an archaeology project.",
        "🎧 Before you log off…" to "…log your hours. Then log off with a clear conscience.",
        "🧩 Missing pieces" to "Today's timesheet has a few empty slots. Let's fill them in.",
        "🌇 Day's almost done" to "Wrap it up properly: log your time and call it a day.",
        "📈 The sprint needs you" to "Your sprint burndown runs on honest hours. Feed it.",
        "🔔 Friendly nudge" to "Your daily, totally non-judgmental reminder to log your time.",
        "🏁 Last lap" to "You've done the work. Logging it is the victory lap.",
        "🍕 Quicker than ordering food" to "Open Timelog, tap your tickets, hit Log all. Done.",
        "🤖 Beep boop" to "Automated scan detected unlogged hours in human. Please resolve.",
        "🌱 Small habit, big win" to "Logging daily keeps the Friday timesheet monster away.",
        "🕵️ Where did the day go?" to "Let's find out, and put it in Jira while we're at it.",
        "🎉 Confetti is waiting" to "Log today's time and claim your well-earned confetti burst.",
        "📝 Note to self" to "Log the hours. Then relax. In that order.",
        "⚡ The easiest task on your board" to "Today's timelog takes about a minute. Knock it out.",
        "🧘 Peace of mind" to "No pending hours, no last-minute stress. Log today now.",
        "🛎️ Ding ding" to "Time to turn today's work into today's worklog.",
        "🌙 Before tomorrow starts" to "Tomorrow-you won't remember today's details. Today-you still does.",
        "🧮 Hours check" to "Is today at 8h yet? Open Timelog and see what's left.",
        "🏖️ Took some time off?" to "Leave counts too. Mark it in one tap so your week adds up.",
        "🎮 Daily quest available" to "Log today's time to complete the quest and keep your streak alive.",
        "📬 You've got (time) mail" to "Your meetings and tickets are ready to be logged. Takes a minute.",
        "✅ Finish strong" to "Close today's loop: log your time, hit Log all, done.",
    )
}
