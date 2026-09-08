package com.rsm.eztrivia.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.Composable
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
import com.rsm.eztrivia.data.AppSettings
import com.rsm.eztrivia.data.AppSettingsPolicy
import com.rsm.eztrivia.data.AppSettingsStore
import com.rsm.eztrivia.data.PlayerState
import com.rsm.eztrivia.reminder.DailyReminderScheduler
import com.rsm.eztrivia.reminder.NotificationPermission
import java.text.DateFormat
import java.util.Calendar
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
    val scope = rememberCoroutineScope()
    val scheduler = remember(context.applicationContext) {
        DailyReminderScheduler(context.applicationContext)
    }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Tune the pace and feedback without changing the game.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = "Gameplay") {
                    SettingToggle(
                        title = "Auto-advance",
                        subtitle = "Move on automatically after the explanation.",
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
                    Text(
                        "The Next button always remains available. Auto-advance pauses automatically while Android touch exploration is active.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = "Daily Challenge") {
                    SettingToggle(
                        title = "Streak reminders",
                        subtitle = "Only remind me when a streak of ${AppSettingsPolicy.MINIMUM_STREAK}+ days is at risk.",
                        checked = settings.streakRemindersEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.setStreakRemindersEnabled(enabled) }
                            if (!enabled) {
                                scheduler.cancel()
                            } else {
                                val needsRuntimePermission =
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS,
                                        ) != PackageManager.PERMISSION_GRANTED
                                if (needsRuntimePermission) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scheduler.refresh(
                                        settings.copy(streakRemindersEnabled = true),
                                        playedDays,
                                    )
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
                        Text(
                            if (notificationsAllowed) {
                                "If today's Daily is still unplayed and your streak is on the line, Android will send one local reminder around this time."
                            } else {
                                "Notifications are currently blocked for EZ Trivia, so no streak reminder can appear."
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!notificationsAllowed) {
                            OutlinedButton(
                                onClick = {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text("Open notification settings")
                            }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "Feedback") {
                    SettingToggle(
                        title = "Sound effects",
                        subtitle = "Short correct, wrong, and round-complete cues.",
                        checked = settings.soundEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.setSoundEnabled(enabled) }
                        },
                    )
                    HorizontalDivider()
                    SettingToggle(
                        title = "Haptics",
                        subtitle = "Use Android's system-respecting touch feedback.",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsStore.setHapticsEnabled(enabled) }
                        },
                    )
                }
            }

            item {
                SettingsSection(title = "Accessibility") {
                    Text(
                        "Answer choices expose their letter, text, and result state to screen readers. Question prompts are headings, touch targets use Material sizing, and auto-advance stops while touch exploration is active.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsSection(title = "Progress") {
                    Text(
                        "Scores, Daily and Friend history, lifetime points, seen questions, and achievement progress are stored locally on this device. Google Play Games sync is not enabled yet.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
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
            title.uppercase(),
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
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
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
