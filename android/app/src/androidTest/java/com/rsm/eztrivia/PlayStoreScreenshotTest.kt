package com.rsm.eztrivia

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rsm.eztrivia.data.PlayerStateStore
import com.rsm.eztrivia.model.TriviaCategory
import com.rsm.eztrivia.model.TriviaDifficulty
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SCREENSHOT_DIR = "/sdcard/Download/eztrivia-play-store-screenshots"

private fun runShellCommand(command: String): String {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
    return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
        .bufferedReader()
        .use { it.readText() }
}

private fun savePlayStoreScreenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    Thread.sleep(350)

    val output = "$SCREENSHOT_DIR/$name.png"
    runShellCommand("mkdir -p $SCREENSHOT_DIR")
    runShellCommand("screencap -p $output")
    val listing = runShellCommand("ls -l $output")
    check(listing.contains("$name.png")) {
        "Screenshot was not persisted at $output. Shell output: $listing"
    }
}

private fun waitForText(
    rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>,
    text: String,
    timeoutMillis: Long = 30_000,
) {
    rule.waitUntil(timeoutMillis) {
        rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
}

@RunWith(AndroidJUnit4::class)
class MainPlayStoreScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturePlayQuestionAndScores() {
        waitForText(composeRule, "Quick Play")
        savePlayStoreScreenshot("01-play")

        composeRule.onNodeWithText("Quick Play").performClick()
        waitForText(composeRule, "QUESTION 1 OF 10")
        composeRule.onNode(
            hasContentDescription("Answer A:", substring = true),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Next question", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        savePlayStoreScreenshot("02-question-explanation")

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        waitForText(composeRule, "Quick Play")

        // Give the Scores screenshot representative local history without
        // inventing catalog content or bypassing the real persistence layer.
        val store = PlayerStateStore(composeRule.activity.applicationContext)
        runBlocking {
            store.recordCategoryRound(
                category = TriviaCategory.HISTORY,
                difficulty = TriviaDifficulty.MEDIUM,
                score = 8,
                total = 10,
                points = 1_240,
            )
            store.recordCategoryRound(
                category = TriviaCategory.SCIENCE,
                difficulty = TriviaDifficulty.HARD,
                score = 7,
                total = 10,
                points = 1_560,
            )
            store.recordQuickPlay(
                score = 8,
                total = 10,
                points = 1_310,
                outcomes = listOf(true, true, false, true, true, true, false, true, true, true),
                categories = setOf(
                    TriviaCategory.HISTORY,
                    TriviaCategory.SCIENCE,
                    TriviaCategory.GEOGRAPHY,
                ),
            )
        }

        composeRule.onNodeWithText("Scores").performClick()
        waitForText(composeRule, "Your progress, recent rounds")
        savePlayStoreScreenshot("05-scores")
    }
}

@RunWith(AndroidJUnit4::class)
class DailyPlayStoreScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DailyChallengeActivity>()

    @Test
    fun captureDailyChallenge() {
        waitForText(composeRule, "Daily #")
        savePlayStoreScreenshot("03-daily")
    }
}

@RunWith(AndroidJUnit4::class)
class FriendPlayStoreScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<FriendChallengeActivity>()

    @Test
    fun captureFriendChallenge() {
        waitForText(composeRule, "Friend Challenge")
        savePlayStoreScreenshot("04-friend-challenge")
    }
}
