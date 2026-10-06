package com.scrollxp.app

import com.scrollxp.app.domain.ReminderPolicy
import com.scrollxp.app.domain.ReminderSettings
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderPolicyTest {
    private val utc = ZoneId.of("UTC")
    private val enabled = ReminderSettings(enabled = true)
    private fun at(value: String) = Instant.parse(value)

    @Test fun optOutSchedulesNothing() {
        assertNull(ReminderPolicy.next(at("2026-10-05T10:00:00Z"), utc, ReminderSettings()))
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-05T20:00:00Z"), utc, ReminderSettings(), null, 0))
    }
    @Test fun timeIsFutureAndNoMissedDayCatchup() {
        assertEquals(at("2026-10-05T20:00:00Z"), ReminderPolicy.next(at("2026-10-05T19:00:00Z"), utc, enabled))
        assertEquals(at("2026-10-06T20:00:00Z"), ReminderPolicy.next(at("2026-10-05T20:00:00Z"), utc, enabled))
    }
    @Test fun overnightQuietHoursMoveLateReminderToMorning() {
        val settings = enabled.copy(minute = 23 * 60)
        assertEquals(at("2026-10-05T08:00:00Z"), ReminderPolicy.next(at("2026-10-05T07:00:00Z"), utc, settings))
        assertEquals(at("2026-10-06T08:00:00Z"), ReminderPolicy.next(at("2026-10-05T08:00:00Z"), utc, settings))
    }
    @Test fun daytimeQuietWindowAlsoMovesReminder() {
        val settings = enabled.copy(minute = 12 * 60, quietStart = 10 * 60, quietEnd = 17 * 60)
        assertEquals(at("2026-10-05T17:00:00Z"), ReminderPolicy.next(at("2026-10-05T09:00:00Z"), utc, settings))
    }
    @Test fun boundariesAreStartInclusiveAndEndExclusive() {
        assertTrue(ReminderPolicy.quiet(enabled, 1320))
        assertTrue(ReminderPolicy.quiet(enabled, 479))
        assertFalse(ReminderPolicy.quiet(enabled, 480))
        assertFalse(ReminderPolicy.quiet(enabled, 1319))
        assertFalse(ReminderPolicy.quiet(enabled.copy(quietEnabled = false), 1320))
    }
    @Test fun equalQuietTimesAreConservativelySilent() {
        val invalid = enabled.copy(quietEnd = enabled.quietStart)
        assertNull(ReminderPolicy.next(at("2026-10-05T20:00:00Z"), utc, invalid))
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-05T20:00:00Z"), utc, invalid, null, 0))
    }
    @Test fun daylightSavingGapMovesToExistingWallTime() {
        val settings = enabled.copy(minute = 150, quietEnabled = false)
        assertEquals(at("2026-03-08T07:30:00Z"), ReminderPolicy.next(at("2026-03-08T06:00:00Z"), ZoneId.of("America/New_York"), settings))
    }
    @Test fun repeatedDstHourDoesNotScheduleTwoReminders() {
        val settings = enabled.copy(minute = 90, quietEnabled = false)
        val zone = ZoneId.of("America/New_York")
        assertEquals(at("2026-11-01T05:30:00Z"), ReminderPolicy.next(at("2026-11-01T04:00:00Z"), zone, settings))
        assertEquals(at("2026-11-02T06:30:00Z"), ReminderPolicy.next(at("2026-11-01T05:31:00Z"), zone, settings))
    }
    @Test fun sameDayAndClockRollbackCannotRepeatDelivery() {
        val previous = at("2026-10-05T12:00:00Z").toEpochMilli()
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-05T20:00:00Z"), utc, enabled, "2026-10-05", previous))
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-04T20:00:00Z"), utc, enabled, "2026-10-05", previous))
        assertTrue(ReminderPolicy.canDeliver(at("2026-10-06T20:00:00Z"), utc, enabled, "2026-10-05", previous))
    }
    @Test fun timezoneTravelCannotCauseRapidDuplicateNotifications() {
        val previous = at("2026-10-05T20:00:00Z").toEpochMilli()
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-06T03:00:00Z"), ZoneId.of("Asia/Kolkata"), enabled, "2026-10-05", previous))
        assertFalse(ReminderPolicy.canDeliver(at("2026-10-06T01:00:00Z"), utc, enabled, "2026-10-05", previous))
    }
    @Test fun changingTimeAfterDeliverySchedulesTheNextEligibleDay() {
        val previous = at("2026-10-05T08:00:00Z").toEpochMilli()
        assertEquals(at("2026-10-06T20:00:00Z"), ReminderPolicy.next(at("2026-10-05T09:00:00Z"), utc, enabled, "2026-10-05", previous))
    }
}
