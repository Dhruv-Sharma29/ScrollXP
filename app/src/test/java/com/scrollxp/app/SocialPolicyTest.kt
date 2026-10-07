package com.scrollxp.app

import com.scrollxp.app.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SocialPolicyTest {
    @Test fun handlesNormalizeAndRejectImpersonationOrUnsupportedCharacters() {
        assertEquals("dhruv29", SocialPolicy.handle(" @Dhruv29 "))
        assertTrue(SocialPolicy.valid("dhruv_29"))
        listOf("ab", "1abc", "support", "scrollxp", "hello-world", "héllo", "x".repeat(21)).forEach { assertFalse(SocialPolicy.valid(it)) }
        assertEquals(SocialPolicy.pair("alice", "bob"), SocialPolicy.pair("bob", "alice"))
    }
    @Test fun weekIsSharedUtcAndScoresNeedCompletePostConsentNonOverlappingDays() {
        val start = Instant.parse("2026-10-05T00:00:00Z").toEpochMilli()
        assertEquals(start, SocialPolicy.start(SocialPolicy.week(start)))
        assertEquals(SocialPolicy.week(start) - 1, SocialPolicy.week(start - 1))
        val day = 86_400_000L
        val history = listOf(BalanceDay(start, start + day, true), BalanceDay(start + day, start + 2 * day, true),
            BalanceDay(start + day, start + 2 * day, true), BalanceDay(start + 2 * day, start + 3 * day, false))
        assertEquals(1, SocialPolicy.score(history, start + 1, start + 3 * day))
        assertEquals(0, SocialPolicy.score(history, start, start + day - 1))
        assertEquals(0, SocialPolicy.score(history, start, start + FriendScorePolicy.WEEK))
    }
    @Test fun tiesSharePositionRatherThanAlphabeticalVictory() {
        assertEquals(listOf(1, 1, 3, 4, 4), SocialPolicy.ranks(listOf(4, 4, 3, 0, 0)))
    }
    @Test fun nightlySchedulingUsesLocalOneAmAndSkipsLateCatchup() {
        val zone = ZoneId.of("Asia/Kolkata")
        val now = Instant.parse("2026-10-05T16:00:00Z")
        val due = NightlyPolicy.next(now, zone)
        assertEquals(Instant.parse("2026-10-05T19:30:00Z"), due)
        assertEquals(due.plusSeconds(86400), NightlyPolicy.next(due, zone))
        assertFalse(NightlyPolicy.timely(due.minusSeconds(1), due))
        assertTrue(NightlyPolicy.timely(due.plusSeconds(3600), due))
        assertFalse(NightlyPolicy.timely(due.plusSeconds(21601), due))
    }
    @Test fun nightlySchedulingRemainsInFutureAcrossTimezoneAndDstBoundaries() {
        val now = Instant.parse("2026-11-01T06:30:00Z")
        for (zone in listOf("America/New_York", "Asia/Kolkata", "Pacific/Auckland")) {
            val next = NightlyPolicy.next(now, ZoneId.of(zone)); assertTrue(next > now)
            assertEquals(1, next.atZone(ZoneId.of(zone)).hour)
        }
    }
}
