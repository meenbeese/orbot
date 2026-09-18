package org.torproject.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceProviderTest {
    @Test
    fun providerCallReturnsSuccessfulResult() {
        assertEquals("value", preferenceProviderCall("fallback") { "value" })
    }

    @Test
    fun providerCallFallsBackWhenProviderIsUnavailable() {
        assertEquals(
            "fallback",
            preferenceProviderCall("fallback") {
                throw IllegalArgumentException("Unknown URI")
            }
        )
    }

    @Test(expected = IllegalStateException::class)
    fun providerCallDoesNotHideUnrelatedRuntimeFailures() {
        preferenceProviderCall(Unit) {
            throw IllegalStateException("unrelated failure")
        }
    }
}
