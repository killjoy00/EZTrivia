package com.rsm.eztrivia.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FriendChallengeLinkTest {
    @Test
    fun unsupportedVersionInsideWebLinkIsReportedAsOldNotMistyped() {
        val old = "EZ2-FXQ5-TK1V-58CG-G81A-6WA"
        val url = "https://killjoy00.github.io/EZTrivia/challenge.html?code=$old"
        assertNull(FriendChallengeLink.codeFrom(url))
        assertEquals(
            FriendChallengeCode.RejectionReason.UnsupportedVersion(2),
            FriendChallengeLink.rejectionReason(url),
        )
    }

    @Test
    fun unrelatedUrlsAreNotChallenges() {
        assertNull(FriendChallengeLink.codeFrom("https://example.com/challenge/EZ3-AAAA"))
        assertNull(FriendChallengeLink.codeFrom("eztrivia://settings"))
        assertNull(FriendChallengeLink.codeFrom("eztrivia://challenge/not-a-code"))
    }
}
