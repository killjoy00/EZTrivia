package com.rsm.eztrivia.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.rsm.eztrivia.R
import com.rsm.eztrivia.data.AppSettings
import java.io.Closeable

/**
 * Short gameplay feedback shared by category, Quick Play, Daily, and Friend rounds.
 *
 * The WAV files are generated from the same repository-owned assets as iOS. Audio
 * uses sonification attributes so a trivia cue behaves like a UI sound rather than
 * taking over media playback. Haptics go through View.performHapticFeedback so
 * Android's own haptic accessibility/system settings remain authoritative.
 */
class AppFeedback(context: Context) : Closeable {
    private enum class Cue { CORRECT, WRONG, COMPLETE }

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val samples = mapOf(
        Cue.CORRECT to soundPool.load(context, R.raw.correct, 1),
        Cue.WRONG to soundPool.load(context, R.raw.wrong, 1),
        Cue.COMPLETE to soundPool.load(context, R.raw.complete, 1),
    )

    fun answer(correct: Boolean, settings: AppSettings, view: View) {
        play(if (correct) Cue.CORRECT else Cue.WRONG, settings)
        if (settings.hapticsEnabled) {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
    }

    fun roundComplete(settings: AppSettings, view: View) {
        play(Cue.COMPLETE, settings)
        if (settings.hapticsEnabled) {
            val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
            view.performHapticFeedback(constant)
        }
    }

    private fun play(cue: Cue, settings: AppSettings) {
        if (!settings.soundEnabled) return
        val sample = samples[cue] ?: return
        soundPool.play(sample, 0.72f, 0.72f, 1, 0, 1f)
    }

    override fun close() {
        soundPool.release()
    }
}

@Composable
fun rememberAppFeedback(): AppFeedback {
    val context = LocalContext.current.applicationContext
    val feedback = remember(context) { AppFeedback(context) }
    DisposableEffect(feedback) {
        onDispose { feedback.close() }
    }
    return feedback
}
