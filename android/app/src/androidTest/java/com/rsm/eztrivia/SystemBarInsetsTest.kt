package com.rsm.eztrivia

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

private fun assertClickTargetsStayAboveNavigationBar(
    rule: AndroidComposeTestRule<*, *>,
) {
    rule.waitForIdle()

    var rootHeight = 0
    var navigationBarBottom = 0
    rule.activityRule.scenario.onActivity { activity ->
        val decor = activity.window.decorView
        rootHeight = decor.height
        navigationBarBottom = ViewCompat.getRootWindowInsets(decor)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?: 0
    }

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
        assertClickTargetsStayAboveNavigationBar(composeRule)
    }
}

@RunWith(AndroidJUnit4::class)
class DailySystemBarInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<DailyChallengeActivity>()

    @Test
    fun clickTargetsStayAboveNavigationBar() {
        assertClickTargetsStayAboveNavigationBar(composeRule)
    }
}

@RunWith(AndroidJUnit4::class)
class FriendSystemBarInsetsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<FriendChallengeActivity>()

    @Test
    fun clickTargetsStayAboveNavigationBar() {
        assertClickTargetsStayAboveNavigationBar(composeRule)
    }
}
