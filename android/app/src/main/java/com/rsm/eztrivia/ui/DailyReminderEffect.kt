package com.rsm.eztrivia.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.reminder.DailyReminderScheduler

/** Keeps the one pending streak reminder synchronized with current local state. */
@Composable
fun DailyReminderEffect(
    settings: AppSettings,
    playedDays: Set<Int>,
) {
    val context = LocalContext.current.applicationContext
    val scheduler = remember(context) { DailyReminderScheduler(context) }

    LaunchedEffect(
        settings.streakRemindersEnabled,
        settings.reminderHour,
        playedDays,
    ) {
        scheduler.refresh(settings, playedDays)
    }
}
