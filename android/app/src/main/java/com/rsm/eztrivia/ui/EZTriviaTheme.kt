package com.rsm.eztrivia.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Keep appearance consistent across the Play, Daily, and Friend Challenge entry activities.
 * The saved preference defaults to the existing light appearance.
 */
@Composable
fun EZTriviaTheme(darkModeEnabled: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkModeEnabled) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
