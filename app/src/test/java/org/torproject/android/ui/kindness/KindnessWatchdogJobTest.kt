package org.torproject.android.ui.kindness

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// see https://github.com/guardianproject/orbot-android/pull/1807/
class KindnessWatchdogJobTest {

    @Test
    fun restartsWhenWantedAndDead() {
        assertTrue(
            KindnessWatchdogJob.shouldRestartKindnessMode(
                wantsProxy = true,
                serviceRunning = false,
                regionBlocked = false
            )
        )
    }

    @Test
    fun leavesARunningServiceAlone() {
        assertFalse(
            KindnessWatchdogJob.shouldRestartKindnessMode(
                wantsProxy = true,
                serviceRunning = true,
                regionBlocked = false
            )
        )
    }

    @Test
    fun respectsTheUserSayingNo() {
        assertFalse(
            KindnessWatchdogJob.shouldRestartKindnessMode(
                wantsProxy = false,
                serviceRunning = false,
                regionBlocked = false
            )
        )
    }

    @Test
    fun respectsARegionBlock() {
        assertFalse(
            KindnessWatchdogJob.shouldRestartKindnessMode(
                wantsProxy = true,
                serviceRunning = false,
                regionBlocked = true
            )
        )
    }
}
