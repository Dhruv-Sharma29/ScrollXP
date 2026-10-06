package com.scrollxp.app.data

import android.content.Context
import androidx.room.withTransaction
import com.scrollxp.app.domain.BudgetPolicy
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.domain.UsageTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TrackingStatus { LOADING, ACCESS_REQUIRED, CHOOSE_APPS, READY, PARTIAL, PAUSED, EMPTY_HISTORY, ERROR }

class ScrollRepository(context: Context, private val database: ScrollDatabase = ScrollDatabase.get(context)) {
    val dao = database.dao()
    private val treasures = TreasureRepository(database)
    private val worlds = WorldRepository(database)
    private val source = AndroidUsageSource(context)
    fun hasAccess() = source.hasAccess()
    suspend fun installedApps() = withContext(Dispatchers.IO) { source.installedApps() }

    suspend fun initialize() = gate.withLock {
        if (dao.profile() == null) dao.saveProfile(Profile(zone = ZoneId.systemDefault().id))
    }

    suspend fun configure(name: String, apps: Set<String>, budget: Int) = gate.withLock {
        val profile = dao.profile() ?: Profile(zone = ZoneId.systemDefault().id)
        val now = System.currentTimeMillis()
        val zone = ZoneId.of(profile.zone.ifBlank { ZoneId.systemDefault().id })
        if (!profile.onboarded) {
            dao.saveProfile(profile.copy(islandName = name.trim().take(28).ifBlank { "Little haven" },
                selectedApps = apps.take(3).toSet().encoded(), budgetMinutes = budget.coerceIn(15, 180),
                onboarded = true, trackingSince = if (source.hasAccess()) now else 0, zone = zone.id))
        } else {
            val changes = apps.encoded() != profile.selectedApps || budget != profile.budgetMinutes
            dao.saveProfile(profile.copy(islandName = name.trim().take(28).ifBlank { "Little haven" },
                pendingApps = if (changes) apps.take(3).toSet().encoded() else null,
                pendingBudget = if (changes) budget.coerceIn(15, 180) else null,
                pendingAt = if (changes) Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() else 0))
        }
    }

    suspend fun decorate(roof: Int? = null, item: String? = null, visible: Boolean? = null) = gate.withLock {
        database.withTransaction {
            val profile = dao.profile() ?: return@withTransaction
            var hidden = profile.hiddenItems.packages()
            if (item != null) {
                val owned = Progression.milestones.any { it.id == item && dao.totalXp() >= it.xp } ||
                    dao.chests().any { it.itemId == item && it.openedAt != null && Treasures.find(item) != null }
                if (!owned) return@withTransaction
                hidden = if (visible ?: (item in hidden)) hidden - item else hidden + item
            }
            dao.saveProfile(profile.copy(roof = roof?.coerceIn(0, 2) ?: profile.roof, hiddenItems = hidden.encoded(),
                hasPlacedTreasure = profile.hasPlacedTreasure || (item != null && item !in hidden && Treasures.find(item) != null)))
        }
    }
    suspend fun visitRegion(id: String) = gate.withLock { worlds.visit(id) }
    suspend fun guideDismissed(value: Boolean) = gate.withLock { worlds.guideDismissed(value) }
    suspend fun openChest(key: String) = gate.withLock { treasures.open(key) }
    suspend fun personality(value: String) = gate.withLock {
        val profile = dao.profile() ?: return@withLock
        dao.saveProfile(profile.copy(personality = value.takeIf { it in listOf("Gentle", "Sarcastic", "Silent") } ?: "Gentle"))
    }
    suspend fun comfort(appearance: String? = null, reducedMotion: Boolean? = null, haptics: Boolean? = null) = gate.withLock {
        val profile = dao.profile() ?: return@withLock
        dao.saveProfile(profile.copy(
            appearance = appearance?.takeIf { it in listOf("Daylight", "Dusk", "System") } ?: profile.appearance,
            reducedMotion = reducedMotion ?: profile.reducedMotion, haptics = haptics ?: profile.haptics))
    }
    suspend fun pause(paused: Boolean) = gate.withLock {
        database.withTransaction {
            val profile = dao.profile() ?: return@withTransaction
            if (paused) dao.saveBreak(TrackingBreak(System.currentTimeMillis())) else dao.closeBreaks(System.currentTimeMillis())
            dao.pendingDays().forEach { dao.saveDay(it.copy(partial = true)) }
            dao.saveProfile(profile.copy(paused = paused, trackingSince = if (paused) 0 else System.currentTimeMillis()))
        }
    }
    suspend fun reset() = gate.withLock {
        database.withTransaction {
            dao.clearChests(); dao.clearDays(); dao.clearRewards(); dao.clearBreaks(); dao.clearProfile()
            dao.saveProfile(Profile(zone = ZoneId.systemDefault().id))
        }
    }
    suspend fun backup() = gate.withLock { IslandBackupStore(database).snapshot() }
    suspend fun restore(backup: IslandBackup, stillSameAccount: () -> Boolean) = gate.withLock {
        IslandBackupStore(database).restore(backup, stillSameAccount)
    }

    suspend fun reconcile(): TrackingStatus = withContext(Dispatchers.IO) {
        gate.withLock {
            var profile = dao.profile() ?: Profile(zone = ZoneId.systemDefault().id).also { dao.saveProfile(it) }
            treasures.settle()
            if (!profile.onboarded || profile.selectedApps.isBlank()) return@withLock TrackingStatus.CHOOSE_APPS
            if (profile.paused) return@withLock TrackingStatus.PAUSED
            val now = System.currentTimeMillis()
            if (!source.hasAccess()) {
                database.withTransaction {
                    if (profile.trackingSince > 0) dao.saveBreak(TrackingBreak(profile.lastObserved.takeIf { it > 0 } ?: now))
                    dao.pendingDays().forEach { dao.saveDay(it.copy(partial = true)) }
                    dao.saveProfile(profile.copy(trackingSince = 0, lastObserved = now))
                }
                return@withLock TrackingStatus.ACCESS_REQUIRED
            }
            if (profile.trackingSince == 0L) { dao.closeBreaks(now); profile = profile.copy(trackingSince = now) }
            val zone = ZoneId.of(profile.zone.ifBlank { ZoneId.systemDefault().id })
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (profile.pendingAt in 1..now) {
                profile = profile.copy(selectedApps = profile.pendingApps ?: profile.selectedApps,
                    budgetMinutes = profile.pendingBudget ?: profile.budgetMinutes,
                    trackingSince = if (profile.pendingApps != null && profile.pendingApps != profile.selectedApps)
                        maxOf(profile.trackingSince, profile.pendingAt) else profile.trackingSince,
                    pendingApps = null, pendingBudget = null, pendingAt = 0)
            }
            val windowStart = today.minusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val events = source.events(windowStart - SIX_HOURS, now)
            if (events.isNullOrEmpty()) {
                database.withTransaction {
                    dao.pendingDays().forEach { dao.saveDay(it.copy(partial = true)) }
                    dao.saveProfile(profile.copy(lastObserved = now))
                }
                return@withLock TrackingStatus.EMPTY_HISTORY
            }
            val breaks = dao.breaks(windowStart).map { it.start to it.end }
            val intervals = UsageTimeline.excluding(UsageTimeline.reconstruct(events, windowStart, now), breaks)
            val staleGap = profile.lastObserved > now ||
                (profile.lastObserved > 0 && now - profile.lastObserved > MAX_OBSERVATION_GAP)
            var todayPartial = true
            database.withTransaction {
                for (offset in 2 downTo 0) {
                    val date = today.minusDays(offset.toLong())
                    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
                    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    if (end <= profile.trackingSince) continue
                    val existing = dao.day(date.toString())
                    // Do not invent historical app selections when settings changed during a gap.
                    if (existing == null && start < profile.trackingSince && offset != 0) continue
                    var day = existing ?: DayRecord(date.toString(), start, end, profile.selectedApps,
                        profile.budgetMinutes, maxOf(start, profile.trackingSince),
                        partial = profile.trackingSince > start || profile.lastObserved == 0L)
                    val gapAffectsDay = staleGap && day.end > profile.lastObserved
                    val totals = UsageTimeline.totals(intervals, day.selectedApps.packages(), day.eligibleSince, minOf(end, now))
                    val total = totals.values.sum()
                    val historyCoversStart = events.first().time <= day.eligibleSince || day.measuredAt > 0
                    day = day.copy(usageMillis = total, perAppJson = JSONObject(totals).toString(), measuredAt = now,
                        partial = day.partial || gapAffectsDay || !historyCoversStart)
                    val key = "usage:${day.date}"
                    val previous = dao.reward(key)?.xp ?: 0
                    val entitlement = maxOf(previous, Progression.usageXp(total))
                    dao.saveReward(Reward(key, day.date, "USAGE", entitlement))
                    if (now >= day.end && day.goalStatus == "PENDING") {
                        val hasEndEvidence = events.last().time >= day.end
                        val status = BudgetPolicy.result(now, day.end, total, day.budgetMinutes,
                            !day.partial && day.eligibleSince == day.start, hasEndEvidence)
                        day = day.copy(goalStatus = status)
                        if (status == "SUCCESS") dao.saveReward(Reward("goal:${day.date}", day.date, "GOAL", Progression.GOAL_BONUS))
                    }
                    dao.saveDay(day)
                    if (offset == 0) todayPartial = day.partial
                }
                dao.pendingDays().filter { it.end < windowStart }.forEach { dao.saveDay(it.copy(partial = true, goalStatus = "UNKNOWN")) }
                dao.saveProfile(profile.copy(lastObserved = now))
            }
            treasures.settle()
            if (todayPartial) TrackingStatus.PARTIAL else TrackingStatus.READY
        }
    }
    companion object {
        private val gate = Mutex()
        private const val SIX_HOURS = 6 * 60 * 60_000L
        private const val MAX_OBSERVATION_GAP = 36 * 60 * 60_000L
    }
}
