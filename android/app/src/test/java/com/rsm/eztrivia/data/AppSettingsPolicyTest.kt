package com.rsm.eztrivia.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsPolicyTest {
    @Test
    fun clampsPersistedRanges() {
        assertEquals(2, AppSettingsPolicy.clampAutoAdvanceSeconds(-10))
        assertEquals(15, AppSettingsPolicy.clampAutoAdvanceSeconds(99))
        assertEquals(12, AppSettingsPolicy.clampReminderHour(3))
        assertEquals(22, AppSettingsPolicy.clampReminderHour(23))
    }

    @Test
    fun feedbackDefaultsOnAndAutoAdvanceDefaultsOff() {
        val settings = AppSettings()
        assertFalse(settings.autoAdvanceEnabled)
        assertEquals(5, settings.autoAdvanceSeconds)
        assertTrue(settings.soundEnabled)
        assertTrue(settings.hapticsEnabled)
        assertFalse(settings.streakRemindersEnabled)
        assertEquals(20, settings.reminderHour)
    }

    @Test
    fun touchExplorationSuppressesAutoAdvance() {
        val settings = AppSettings(autoAdvanceEnabled = true)
        assertTrue(
            AppSettingsPolicy.autoAdvanceShouldRun(
                settings = settings,
                answered = true,
                touchExplorationEnabled = false,
            )
        )
        assertFalse(
            AppSettingsPolicy.autoAdvanceShouldRun(
                settings = settings,
                answered = true,
                touchExplorationEnabled = true,
            )
        )
        assertFalse(
            AppSettingsPolicy.autoAdvanceShouldRun(
                settings = settings,
                answered = false,
                touchExplorationEnabled = false,
            )
        )
    }
}
