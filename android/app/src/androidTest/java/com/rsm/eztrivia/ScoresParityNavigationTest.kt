package com.rsm.eztrivia

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScoresParityNavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scoresProvidesInAppLeaderboardAndAchievementNavigationWithoutSignIn() {
        rule.waitUntil(30_000) {
            runCatching { rule.onNodeWithText("Scores").fetchSemanticsNode() }.isSuccess
        }
        rule.onNodeWithText("Scores").performClick()
        rule.onNodeWithText("Leaderboards").performClick()
        rule.onNodeWithText("Daily Challenge").assertExists()
        rule.onNodeWithText("Global").assertExists()
        rule.onNodeWithText("Friends").assertExists()
        rule.onNodeWithText("‹ Scores").performClick()
        rule.onNodeWithText("Question coverage").assertExists()
        rule.onNodeWithText("Achievements").performClick()
        rule.onNodeWithText("Achievements").assertExists()
    }
}
