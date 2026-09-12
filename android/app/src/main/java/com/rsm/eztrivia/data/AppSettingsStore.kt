package com.rsm.eztrivia.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.appSettingsDataStore by preferencesDataStore(name = "eztrivia_app_settings")

data class AppSettings(
    val autoAdvanceEnabled: Boolean = false,
    val autoAdvanceSeconds: Int = AppSettingsPolicy.DEFAULT_AUTO_ADVANCE_SECONDS,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val streakRemindersEnabled: Boolean = false,
    val reminderHour: Int = AppSettingsPolicy.DEFAULT_REMINDER_HOUR,
)

object AppSettingsPolicy {
    val AUTO_ADVANCE_RANGE: IntRange = 2..15
    val REMINDER_HOUR_RANGE: IntRange = 12..22
    const val DEFAULT_AUTO_ADVANCE_SECONDS = 5
    const val DEFAULT_REMINDER_HOUR = 20
    const val MINIMUM_STREAK = 2

    fun clampAutoAdvanceSeconds(value: Int): Int = value.coerceIn(AUTO_ADVANCE_RANGE)
    fun clampReminderHour(value: Int): Int = value.coerceIn(REMINDER_HOUR_RANGE)

    fun autoAdvanceShouldRun(
        settings: AppSettings,
        answered: Boolean,
        touchExplorationEnabled: Boolean,
    ): Boolean =
        settings.autoAdvanceEnabled && answered && !touchExplorationEnabled
}

class AppSettingsStore(context: Context) {
    private object Keys {
        val autoAdvanceEnabled = booleanPreferencesKey("gameplay.autoAdvance.enabled")
        val autoAdvanceSeconds = intPreferencesKey("gameplay.autoAdvance.seconds")
        val soundEnabled = booleanPreferencesKey("feedback.sound.enabled")
        val hapticsEnabled = booleanPreferencesKey("feedback.haptics.enabled")
        val streakRemindersEnabled = booleanPreferencesKey("daily.streakReminder.enabled")
        val reminderHour = intPreferencesKey("daily.streakReminder.hour")
    }

    private val dataStore = context.applicationContext.appSettingsDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val state: StateFlow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decode)
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    /**
     * The persisted settings, awaited rather than sampled.
     *
     * `state` is seeded eagerly with defaults, so a caller outside a
     * composition -- a broadcast receiver, say -- that read `state.value`
     * could act on defaults that were never on disk.
     */
    suspend fun loaded(): AppSettings = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decode)
        .first()

    suspend fun setAutoAdvanceEnabled(enabled: Boolean) = set(Keys.autoAdvanceEnabled, enabled)

    suspend fun setAutoAdvanceSeconds(seconds: Int) =
        set(Keys.autoAdvanceSeconds, AppSettingsPolicy.clampAutoAdvanceSeconds(seconds))

    suspend fun setSoundEnabled(enabled: Boolean) = set(Keys.soundEnabled, enabled)

    suspend fun setHapticsEnabled(enabled: Boolean) = set(Keys.hapticsEnabled, enabled)

    suspend fun setStreakRemindersEnabled(enabled: Boolean) = set(Keys.streakRemindersEnabled, enabled)

    suspend fun setReminderHour(hour: Int) =
        set(Keys.reminderHour, AppSettingsPolicy.clampReminderHour(hour))

    private suspend fun <T> set(key: Preferences.Key<T>, value: T) {
        dataStore.edit { preferences -> preferences[key] = value }
    }

    private fun decode(preferences: Preferences): AppSettings = AppSettings(
        autoAdvanceEnabled = preferences[Keys.autoAdvanceEnabled] ?: false,
        autoAdvanceSeconds = AppSettingsPolicy.clampAutoAdvanceSeconds(
            preferences[Keys.autoAdvanceSeconds] ?: AppSettingsPolicy.DEFAULT_AUTO_ADVANCE_SECONDS
        ),
        soundEnabled = preferences[Keys.soundEnabled] ?: true,
        hapticsEnabled = preferences[Keys.hapticsEnabled] ?: true,
        streakRemindersEnabled = preferences[Keys.streakRemindersEnabled] ?: false,
        reminderHour = AppSettingsPolicy.clampReminderHour(
            preferences[Keys.reminderHour] ?: AppSettingsPolicy.DEFAULT_REMINDER_HOUR
        ),
    )
}
