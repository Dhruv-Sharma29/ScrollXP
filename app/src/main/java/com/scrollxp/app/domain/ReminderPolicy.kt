package com.scrollxp.app.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

data class ReminderSettings(
    val enabled: Boolean = false,
    val minute: Int = 20 * 60,
    val quietEnabled: Boolean = true,
    val quietStart: Int = 22 * 60,
    val quietEnd: Int = 8 * 60,
    val revision: Long = 0,
)

/** Reminders follow the phone's local wall clock, separately from frozen goal windows. */
object ReminderPolicy {
    fun quiet(settings: ReminderSettings, minute: Int): Boolean = settings.quietEnabled &&
        if (settings.quietStart < settings.quietEnd) minute >= settings.quietStart && minute < settings.quietEnd
        else if (settings.quietStart > settings.quietEnd) minute >= settings.quietStart || minute < settings.quietEnd
        else true // Invalid equal boundaries conservatively mean silence all day.

    fun next(now: Instant, zone: ZoneId, settings: ReminderSettings, lastDate: String? = null, lastAt: Long = 0): Instant? {
        if (!settings.enabled || settings.quietEnabled && settings.quietStart == settings.quietEnd) return null
        val today = now.atZone(zone).toLocalDate()
        // Yesterday's late-evening reminder may have been moved into this morning.
        return (-1L..3L).map { offset ->
            var date = today.plusDays(offset)
            var minute = settings.minute
            if (quiet(settings, minute)) {
                if (settings.quietStart > settings.quietEnd && minute >= settings.quietStart) date = date.plusDays(1)
                minute = settings.quietEnd
            }
            date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(zone).toInstant()
        }.filter { it > now && canDeliver(it, zone, settings, lastDate, lastAt) }
            .minOrNull()
    }

    fun canDeliver(now: Instant, zone: ZoneId, settings: ReminderSettings, lastDate: String?, lastAt: Long): Boolean {
        val local = now.atZone(zone)
        return settings.enabled && !quiet(settings, local.hour * 60 + local.minute) &&
            local.toLocalDate().toString() != lastDate &&
            (lastAt == 0L || Duration.between(Instant.ofEpochMilli(lastAt), now) >= Duration.ofHours(20))
    }
}
