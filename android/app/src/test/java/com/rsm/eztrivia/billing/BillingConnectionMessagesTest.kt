package com.rsm.eztrivia.billing

import org.junit.Assert.assertTrue
import org.junit.Test

class BillingConnectionMessagesTest {
    @Test
    fun connectionTimeoutGivesUserRetryRatherThanInfiniteSpinner() {
        val message = BillingConnectionMessages.timeout()
        assertTrue(message.contains("15 seconds"))
        assertTrue(message.contains("Play Store"))
        assertTrue(message.contains("Try again"))
    }

    @Test
    fun disconnectedServiceIsNotShownAsStillConnecting() {
        val message = BillingConnectionMessages.disconnected()
        assertTrue(message.contains("disconnected"))
        assertTrue(message.contains("Try again"))
    }

    @Test
    fun failedBillingSetupShowsDiagnosticResponseCode() {
        val message = BillingConnectionMessages.failed(3, "Billing unavailable")
        assertTrue(message.contains("code 3"))
        assertTrue(message.contains("Billing unavailable"))
        assertTrue(message.contains("Try again"))
    }

    @Test
    fun blankErrorStillExplainsWhatHappened() {
        val message = BillingConnectionMessages.failed(null, "")
        assertTrue(message.contains("connect"))
        assertTrue(message.contains("Try again"))
    }
}
