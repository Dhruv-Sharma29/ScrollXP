package com.scrollxp.app.data

import androidx.room.withTransaction
import com.scrollxp.app.domain.Treasures
import kotlin.random.Random
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

/** Award, reserve and reveal in transactions so retries and concurrent workers cannot duplicate rewards. */
class TreasureRepository(private val db: ScrollDatabase, private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis) {
    private val dao = db.dao()

    suspend fun settle() = db.withTransaction {
        val profile = dao.profile() ?: return@withTransaction
        if (!profile.onboarded) return@withTransaction
        val earnedAt = now()
        val today = Instant.ofEpochMilli(earnedAt).atZone(ZoneId.of(profile.zone.ifBlank { ZoneId.systemDefault().id })).toLocalDate()
        val completedDates = dao.goalDates().filter { runCatching { LocalDate.parse(it) < today }.getOrDefault(false) }
        val xp = dao.totalXp()
        val existing = dao.chests().associateBy { it.key }.toMutableMap()
        val reserved = existing.values.map { it.itemId }.toMutableSet()
        val awards = Treasures.chestMilestones.filter { xp >= it }.map {
            "milestone:$it" to if (it == 0) "Welcome gift" else "$it XP milestone"
        } + Treasures.completedWeeks(completedDates).map { "week:$it" to "Balance week of $it" }
        for ((key, source) in awards) {
            if (key in existing) continue
            val itemId = Treasures.draw(reserved, random)
            val chest = Chest(key, source, earnedAt, itemId, Treasures.find(itemId)?.rarity?.label ?: "Keepsake", economyVersion = Treasures.ECONOMY_VERSION)
            dao.insertChest(chest)
            reserved.add(itemId)
            existing[key] = chest
        }
    }

    suspend fun open(key: String): Chest? = db.withTransaction {
        val chest = dao.chest(key) ?: return@withTransaction null
        if (chest.openedAt != null) return@withTransaction chest
        val profile = dao.profile() ?: return@withTransaction null
        val opened = chest.copy(openedAt = now())
        dao.updateChest(opened)
        if (chest.itemId != Treasures.KEEPSAKE) {
            // Revealing grants ownership; placement is an explicit choice.
            dao.saveProfile(profile.copy(hiddenItems = (profile.hiddenItems.packages() + chest.itemId).encoded()))
        }
        opened
    }
}
