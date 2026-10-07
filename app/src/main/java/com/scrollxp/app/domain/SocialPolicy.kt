package com.scrollxp.app.domain

import java.time.*
import java.util.Locale

object SocialPolicy {
    const val ANCHOR = 345_600_000L // Monday 5 January 1970, UTC
    const val LIMIT = 20 // Includes pending requests and blocks, keeping cleanup bounded.
    val areas = listOf("Dehradun", "Delhi", "Mumbai", "Bengaluru", "Hyderabad", "Chennai", "Kolkata", "Pune")
    fun handle(input: String) = input.trim().removePrefix("@").lowercase(Locale.ROOT)
    fun valid(input: String): Boolean = input.matches(Regex("[a-z][a-z0-9_]{2,19}")) &&
        input !in setOf("admin", "support", "scrollxp", "official", "moderator")
    fun pair(a: String, b: String) = listOf(a, b).sorted().joinToString("~")
    fun week(now: Long) = Math.floorDiv(now - ANCHOR, FriendScorePolicy.WEEK)
    fun start(week: Long) = ANCHOR + week * FriendScorePolicy.WEEK
    fun score(days: List<BalanceDay>, since: Long, now: Long) = FriendScorePolicy.score(
        days, start(week(now)), since, start(week(now)) + FriendScorePolicy.WEEK, now)
    fun ranks(scores: List<Int>) = scores.map { value -> 1 + scores.count { it > value } }
}

object NightlyPolicy {
    fun next(now: Instant, zone: ZoneId): Instant {
        val local = now.atZone(zone)
        val today = local.toLocalDate().atTime(1, 0).atZone(zone).toInstant()
        return if (today > now) today else local.toLocalDate().plusDays(1).atTime(1, 0).atZone(zone).toInstant()
    }
    fun timely(now: Instant, due: Instant) = now >= due && Duration.between(due, now) <= Duration.ofHours(6)
}
