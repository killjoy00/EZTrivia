package com.rsm.eztrivia

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator regression: Android Settings should not reintroduce verbose essays. */
@RunWith(AndroidJUnit4::class)
class SettingsParityNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun settingsAreCompactAndStillExposePurchaseAndSupport() {
        rule.waitUntil(30_000) {
            runCatching { rule.onNodeWithText("Settings").fetchSemanticsNode() }.isSuccess
        }
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Gameplay").assertExists()
        rule.onNodeWithText("Auto-advance").assertExists()
        rule.onNodeWithText("Tune the pace and feedback without changing the game.")
            .assertDoesNotExist()
        rule.onNodeWithText(
            "The Next button always remains available. Auto-advance pauses automatically while Android touch exploration is active."
        ).assertDoesNotExist()

        // These actions must remain accessible even when Play Billing cannot
        // connect in an emulator (a real licensed Play install is tested separately).
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Remove Ads"))
        rule.onNodeWithText("Remove Ads").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Report a problem"))
        rule.onNodeWithText("Report a problem").assertExists()
    }
}
