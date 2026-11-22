package com.rsm.eztrivia.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rsm.eztrivia.MainActivity
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.AppSettingsPolicy
import com.rsm.eztrivia.data.AppSettingsStore
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.reminder.DailyReminderScheduler
import com.rsm.eztrivia.reminder.NotificationPermission
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: AppSettings,
    settingsStore: AppSettingsStore,
    playerState: PlayerState,
    onPlay: () -> Unit,
    onScores: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val scope = rememberCoroutineScope()
    val scheduler = remember(context.applicationContext) {
        DailyReminderScheduler(context.applicationContext)
    }
    val playGamesManager = activity?.playGamesManager
    val playGamesConnection = playGamesManager?.connection?.collectAsState()?.value
    val savedGamesManager = activity?.savedGamesManager
    val savedGamesState = savedGamesManager?.state?.collectAsState()?.value
    var permissionRevision by remember { mutableIntStateOf(0) }
    val playedDays = playerState.dailyResultsByDay.keys
    val notificationsAllowed = remember(permissionRevision, settings.streakRemindersEnabled) {
        NotificationPermission.isAllowed(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionRevision += 1
        scheduler.refresh(settings.copy(streakRemindersEnabled = true), playedDays)
    }

    Scaffold(
        bottomBar = {
            EZTriviaBottomBar(
                selected = AppSection.SETTINGS,
                onPlay = onPlay,
                onScores = onScores,
                onSettings = {},
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsSection("Gameplay") {
                    SettingToggle(
                        title = "Auto-advance",
                        checked = settings.autoAdvanceEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.setAutoAdvanceEnabled(enabled) }
                        },
                    )
                    if (settings.autoAdvanceEnabled) {
                        HorizontalDivider()
                        SliderSetting(
                            title = "Delay",
                            valueLabel = "${settings.autoAdvanceSeconds} seconds",
                            value = settings.autoAdvanceSeconds.toFloat(),
                            range = AppSettingsPolicy.AUTO_ADVANCE_RANGE.first.toFloat()..
                                AppSettingsPolicy.AUTO_ADVANCE_RANGE.last.toFloat(),
                            steps = AppSettingsPolicy.AUTO_ADVANCE_RANGE.count() - 2,
                            onValueChange = { raw ->
                                scope.launch { settingsStore.setAutoAdvanceSeconds(raw.toInt()) }
                            },
                        )
                    }
                }
            }

            item {
                SettingsSection("Daily Challenge") {
                    SettingToggle(
                        title = "Streak reminders",
                        checked = settings.streakRemindersEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.setStreakRemindersEnabled(enabled) }
                            if (!enabled) {
                                scheduler.cancel()
                            } else {
                                val needsPermission =
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                if (needsPermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scheduler.refresh(settings.copy(streakRemindersEnabled = true), playedDays)
                                }
                            }
                        },
                    )
                    if (settings.streakRemindersEnabled) {
                        HorizontalDivider()
                        SliderSetting(
                            title = "Remind me at",
                            valueLabel = hourLabel(settings.reminderHour),
                            value = settings.reminderHour.toFloat(),
                            range = AppSettingsPolicy.REMINDER_HOUR_RANGE.first.toFloat()..
                                AppSettingsPolicy.REMINDER_HOUR_RANGE.last.toFloat(),
                            steps = AppSettingsPolicy.REMINDER_HOUR_RANGE.count() - 2,
                            onValueChange = { raw ->
                                val hour = raw.toInt()
                                scope.launch { settingsStore.setReminderHour(hour) }
                                scheduler.refresh(settings.copy(reminderHour = hour), playedDays)
                            },
                        )
                        if (!notificationsAllowed) {
                            Text(
                                "Notifications are turned off for EZ Trivia.",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    )
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) { Text("Open notification settings") }
                        }
                    }
                }
            }

            item {
                SettingsSection("Feedback") {
                    SettingToggle(
                        title = "Sound effects",
                        checked = settings.soundEnabled,
                        onCheckedChange = { scope.launch { settingsStore.setSoundEnabled(it) } },
                    )
                    HorizontalDivider()
                    SettingToggle(
                        title = "Haptics",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { scope.launch { settingsStore.setHapticsEnabled(it) } },
                    )
                }
            }

            // Android-only preference. iOS follows the OS appearance.
            item {
                SettingsSection("Appearance") {
                    SettingToggle(
                        title = "Dark mode",
                        checked = settings.darkModeEnabled,
                        onCheckedChange = { scope.launch { settingsStore.setDarkModeEnabled(it) } },
                    )
                }
            }

            item {
                SettingsSection("Google Play Games") {
                    when {
                        playGamesConnection == null ->
                            Text("Unavailable", modifier = Modifier.padding(16.dp))
                        playGamesConnection.isChecking ->
                            Text("Connecting…", modifier = Modifier.padding(16.dp))
                        playGamesConnection.isAuthenticated -> {
                            val lastSync = savedGamesState?.lastSyncedAtMillis
                            Text(
                                when {
                                    savedGamesState?.isSyncing == true -> "Syncing progress…"
                                    lastSync != null -> "Progress synced ${dateTimeLabel(lastSync)}"
                                    else -> "Connected · Progress syncs automatically"
                                },
                                modifier = Modifier.padding(16.dp),
                            )
                            if (savedGamesState != null) {
                                savedGamesState.errorMessage?.let { error ->
                                    Text(
                                        error,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                TextButton(
                                    onClick = { savedGamesManager?.syncNow() },
                                    enabled = !savedGamesState.isSyncing,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                ) { Text("Sync now") }
                            }
                        }
                        else -> {
                            Text("Not connected", modifier = Modifier.padding(16.dp))
                            TextButton(
                                onClick = { playGamesManager?.signIn() },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            ) { Text("Connect Google Play Games") }
                        }
                    }
                    playGamesConnection?.errorMessage?.let { error ->
                        Text(
                            error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            item { MonetizationSettingsCard() }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
            )
        },
        modifier = Modifier.toggleable(
            value = checked,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
    )
}

@Composable
private fun SliderSetting(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ListItem(
            headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
            trailingContent = { Text(valueLabel, color = MaterialTheme.colorScheme.primary) },
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
    }
}

private fun hourLabel(hour: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(calendar.time)
}

private fun dateTimeLabel(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
