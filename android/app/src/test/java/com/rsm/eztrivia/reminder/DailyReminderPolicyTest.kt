package com.rsm.eztrivia.reminder

import com.rsm.eztrivia.data.AppSettings
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReminderPolicyTest {
    private val zone = ZoneId.of("America/Chicago")

    @Test
    fun schedulesTomorrowAfterCompletingATwoDayStreak() {
        val settings = AppSettings(streakRemindersEnabled = true, reminderHour = 20)
        val now = ZonedDateTime.of(2026, 9, 8, 18, 0, 0, 0, zone)

        val plan = DailyReminderPolicy.plan(
            settings = settings,
            playedDays = setOf(249, 250),
            now = now,
        )

        assertEquals(ZonedDateTime.of(2026, 9, 9, 20, 0, 0, 0, zone), plan?.fireAt)
        assertEquals(2, plan?.streak)
    }

    @Test
    fun schedulesOneMinuteOutWhenChosenHourAlreadyPassed() {
        val settings = AppSettings(streakRemindersEnabled = true, reminderHour = 20)
        val now = ZonedDateTime.of(2026, 9, 9, 21, 15, 0, 0, zone)

        val plan = DailyReminderPolicy.plan(
            settings = settings,
            playedDays = setOf(249, 250),
            now = now,
        )

        assertEquals(now.plusMinutes(1), plan?.fireAt)
        assertEquals(2, plan?.streak)
    }

    @Test
    fun doesNotInterruptForOneDayOrDisabledReminders() {
        val now = ZonedDateTime.of(2026, 9, 9, 18, 0, 0, 0, zone)
        assertNull(
            DailyReminderPolicy.plan(
                settings = AppSettings(streakRemindersEnabled = true),
                playedDays = setOf(250),
                now = now,
            )
        )
        assertNull(
            DailyReminderPolicy.plan(
                settings = AppSettings(streakRemindersEnabled = false),
                playedDays = setOf(249, 250),
                now = now,
            )
        )
    }
}
