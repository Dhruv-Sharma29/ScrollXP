package com.scrollxp.app.domain

enum class EventKind { RESUME, PAUSE, SCREEN_OFF, SCREEN_ON, LOCK, UNLOCK, SHUTDOWN, STARTUP }
data class TimelineEvent(val time: Long, val kind: EventKind, val app: String = "", val activity: String = "")
data class UsageInterval(val app: String, val start: Long, val end: Long)

/** Last-resumed-activity estimate. No content or gesture inference. */
object UsageTimeline {
    fun reconstruct(events: List<TimelineEvent>, start: Long, end: Long): List<UsageInterval> {
        require(end >= start)
        var active: TimelineEvent? = null
        var interactive: Boolean? = null
        var unlocked: Boolean? = null
        val result = mutableListOf<UsageInterval>()
        fun close(at: Long) {
            active?.let {
                val left = maxOf(it.time, start)
                val right = minOf(at, end)
                if (right > left) result += UsageInterval(it.app, left, right)
            }
            active = null
        }
        events.distinct().sortedBy { it.time }.filter { it.time <= end }.forEach { event ->
            when (event.kind) {
                EventKind.RESUME -> { close(event.time); if (interactive != false && unlocked != false) active = event }
                EventKind.PAUSE -> if (active?.app == event.app &&
                    (event.activity.isEmpty() || active?.activity == event.activity)) close(event.time)
                EventKind.SCREEN_OFF -> { close(event.time); interactive = false }
                EventKind.LOCK -> { close(event.time); unlocked = false }
                EventKind.SCREEN_ON -> interactive = true
                EventKind.UNLOCK -> unlocked = true
                // Shutdown ends the old session. New resume events establish activity when
                // screen/keyguard state is unknown; explicit off/locked states still block it.
                EventKind.SHUTDOWN -> { close(event.time); interactive = null; unlocked = null }
                // Android can emit SCREEN_ON before STARTUP. Preserve that newer evidence.
                EventKind.STARTUP -> close(event.time)
            }
        }
        close(end)
        return result
    }
    fun excluding(intervals: List<UsageInterval>, breaks: List<Pair<Long, Long>>): List<UsageInterval> {
        return breaks.fold(intervals) { current, (left, right) ->
            current.flatMap { interval ->
                if (right <= interval.start || left >= interval.end) listOf(interval)
                else buildList {
                    if (left > interval.start) add(interval.copy(end = left))
                    if (right < interval.end) add(interval.copy(start = right))
                }
            }
        }
    }
    fun totals(intervals: List<UsageInterval>, selected: Set<String>, start: Long, end: Long): Map<String, Long> {
        val sums = selected.associateWith { 0L }.toMutableMap()
        intervals.filter { it.app in selected }.forEach {
            val duration = (minOf(it.end, end) - maxOf(it.start, start)).coerceAtLeast(0)
            sums[it.app] = sums.getValue(it.app) + duration
        }
        return sums
    }
}
