package com.rsm.eztrivia.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rsm.eztrivia.data.AppSettingsStore
import com.rsm.eztrivia.data.PlayerStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms the Daily streak reminder after the device restarts.
 *
 * AlarmManager drops every pending alarm on reboot, and the app only rescheduled
 * on launch. That is backwards for this feature: the reminder exists for players
 * who have *not* opened the app today, so a restart silently cancelled the one
 * notification that was meant to bring them back.
 *
 * `MY_PACKAGE_REPLACED` is handled too -- an app update clears alarms the same way.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val appContext = context.applicationContext
        // goAsync keeps the receiver alive across the DataStore reads; onReceive
        // itself must not block, and the process may otherwise be killed the
        // moment it returns.
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = AppSettingsStore(appContext).loaded()
                val playerState = PlayerStateStore(appContext).loaded()
                DailyReminderScheduler(appContext).refresh(
                    settings = settings,
                    playedDays = playerState.dailyResultsByDay.keys,
                )
            } catch (_: Exception) {
                // A reminder that cannot be rescheduled is not worth crashing
                // the boot broadcast over.
            } finally {
                result.finish()
            }
        }
    }
}
