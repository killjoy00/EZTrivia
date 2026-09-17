package com.rsm.eztrivia

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CrashlyticsConfigurationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun configuredBuildInitializesFirebaseAndCrashlytics() {
        composeRule.waitForIdle()

        assertTrue(
            "This regression test must run with a real Firebase Android config",
            BuildConfig.CRASHLYTICS_CONFIGURED,
        )

        val context = composeRule.activity.applicationContext
        assertTrue(
            "Default Firebase app was not initialized",
            FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME },
        )

        // Resolving the singleton proves the Crashlytics component is registered
        // with the configured Firebase app. Debug collection remains disabled by
        // EZTriviaApplication so this test does not create production crash noise.
        FirebaseCrashlytics.getInstance()
    }
}
