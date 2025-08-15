package org.torproject.android

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.torproject.android.ui.connect.ConnectFragment
import tools.fastlane.screengrab.Screengrab

class ScreenshotConnected : BaseScreenshotTest() {
    @get:Rule var mActivityScenarioRule = ActivityScenarioRule(OrbotActivity::class.java)

    @Before
    fun setup() {
        mActivityScenarioRule.scenario.onActivity { activity ->
            val navHost = activity.supportFragmentManager.primaryNavigationFragment
            val connectFrag = navHost!!.childFragmentManager.fragments[0] as ConnectFragment
            connectFrag.doLayoutOn(connectFrag.requireContext())
        }
    }

    @Test
    fun screenshotConnected() {
        mActivityScenarioRule.scenario.onActivity { activity ->
            onView(withId(R.id.lvConnected)).isVisible()
            Thread.sleep(300)
            Screengrab.screenshot("A-orbot_connected")
        }
    }
}
