package com.rsm.eztrivia

import android.app.Activity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun assertClickTargetsStayAboveNavigationBar(
    rule: AndroidComposeTestRule<*, *>,
    activity: Activity,
) {
    rule.waitForIdle()

    val decor = activity.window.decorView
    val rootHeight = decor.height
    val navigationBarBottom = ViewCompat.getRootWindowInsets(decor)
        ?.getInsets(WindowInsetsCompat.Type.navigationBars())
        ?.bottom
        ?: 0

    assertTrue("Expected a non-zero Android navigation-bar inset", navigationBarBottom > 0)

    val clickTargets = rule.onAllNodes(hasClickAction(), useUnmergedTree = true)
        .fetchSemanticsNodes()
    assertTrue("Expected at least one clickable control", clickTargets.isNotEmpty())

    val lowestClickableBottom = clickTargets.maxOf { it.boundsInRoot.bottom }
    val safeBottom = rootHeight - navigationBarBottom

    assertTrue(
        "Clickable control overlaps Android navigation UI: " +
            "lowestBottom=$lowestClickableBottom safeBottom=$safeBottom " +
            "rootHeight=$rootHeight navInset=$navigationBarBottom",
        lowestClickableBottom <= safeBottom + 1f,
    )
}

@RunWith(AndroidJUnit4::class)
class MainSystemBarInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun clickTargetsStayAboveNavigationBar() {
        assertClickTargetsStayAboveNavigationBar(composeRule, composeRule.activity)
    }
}

@RunWith(AndroidJUnit4::class)
class DailySystemBarInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DailyChallengeActivity>()

    @Test
    fun clickTargetsStayAboveNavigationBar() {
        assertClickTargetsStayAboveNavigationBar(composeRule, composeRule.activity)
    }
}

@RunWith(AndroidJUnit4::class)
class FriendSystemBarInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<FriendChallengeActivity>()

    @Test
    fun clickTargetsStayAboveNavigationBar() {
        assertClickTargetsStayAboveNavigationBar(composeRule, composeRule.activity)
    }
}
