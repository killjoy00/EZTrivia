package com.rsm.eztrivia.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rsm.eztrivia.DailyChallengeActivity
import com.rsm.eztrivia.R
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.AppSettingsPolicy
import com.rsm.eztrivia.model.DailyChallenge
import com.rsm.eztrivia.model.DailyStreak
import java.time.LocalDate
import java.time.ZonedDateTime

private const val REMINDER_REQUEST_CODE = 252
private const val REMINDER_ACTION = "com.rsm.eztrivia.DAILY_STREAK_REMINDER"
private const val EXTRA_STREAK = "streak"
private const val CHANNEL_ID = "daily_streak_reminders"

data class DailyReminderPlan(
    val fireAt: ZonedDateTime,
    val streak: Int,
)

object DailyReminderPolicy {
    private val epoch = LocalDate.of(2026, 1, 1)

    fun plan(
        settings: AppSettings,
        playedDays: Set<Int>,
        now: ZonedDateTime,
    ): DailyReminderPlan? {
        if (!settings.streakRemindersEnabled) return null

        val today = DailyChallenge.day(now.toLocalDate())
        val risk = DailyStreak.dayAtRisk(
            playedDays = playedDays,
            today = today,
            minimumStreak = AppSettingsPolicy.MINIMUM_STREAK,
        ) ?: return null

        val riskDate = epoch.plusDays(risk.first.toLong())
        val scheduled = riskDate
            .atTime(AppSettingsPolicy.clampReminderHour(settings.reminderHour), 0)
            .atZone(now.zone)

        if (scheduled.isAfter(now)) {
            return DailyReminderPlan(scheduled, risk.second)
        }

        // Match iOS: if the chosen hour already passed but midnight has not,
        // a one-minute nudge is still useful instead of silently dropping the
        // streak reminder for the rest of the evening.
        val soon = now.plusMinutes(1)
        val endOfRiskDay = riskDate.plusDays(1).atStartOfDay(now.zone)
        return if (soon.isBefore(endOfRiskDay)) {
            DailyReminderPlan(soon, risk.second)
        } else {
            null
        }
    }
}

object NotificationPermission {
    fun isAllowed(context: Context): Boolean {
        val runtimeAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeAllowed && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

class DailyReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    fun refresh(
        settings: AppSettings,
        playedDays: Set<Int>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ) {
        cancel()
        DailyReminderNotifier.ensureChannel(appContext)
        if (!NotificationPermission.isAllowed(appContext)) return

        val plan = DailyReminderPolicy.plan(settings, playedDays, now) ?: return
        val intent = Intent(appContext, DailyReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
            putExtra(EXTRA_STREAK, plan.streak)
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // This reminder is useful around a chosen hour, not at an exact second.
        // setAndAllowWhileIdle keeps it available in Doze without requesting the
        // special exact-alarm permission from the player.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            plan.fireAt.toInstant().toEpochMilli(),
            pending,
        )
    }

    fun cancel() {
        val intent = Intent(appContext, DailyReminderReceiver::class.java).apply {
            action = REMINDER_ACTION
        }
        val pending = PendingIntent.getBroadcast(
            appContext,
            REMINDER_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != REMINDER_ACTION) return
        if (!NotificationPermission.isAllowed(context)) return

        val streak = intent.getIntExtra(EXTRA_STREAK, AppSettingsPolicy.MINIMUM_STREAK)
        DailyReminderNotifier.post(context, streak)
    }
}

private object DailyReminderNotifier {
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily Challenge reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A reminder only when an active Daily Challenge streak is at risk."
        }
        manager.createNotificationChannel(channel)
    }

    fun post(context: Context, streak: Int) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        ensureChannel(context)
        val contentIntent = PendingIntent.getActivity(
            context,
            REMINDER_REQUEST_CODE,
            Intent(context, DailyChallengeActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = if (streak >= 7) {
            "Today's Daily Challenge is still open. $streak days on the line."
        } else {
            "Play today's Daily Challenge before midnight to keep it going."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_daily_notification)
            .setContentTitle("Keep your $streak-day streak")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(REMINDER_REQUEST_CODE, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify(). A local
            // streak reminder should fail silently rather than crash the app.
        }
    }
}
