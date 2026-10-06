package com.scrollxp.app.data

import androidx.room.withTransaction
import com.scrollxp.app.domain.Progression
import com.scrollxp.app.domain.Treasures
import com.scrollxp.app.domain.Worlds
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

/** Game records only: never selected apps, raw events, daily minutes, or reminder settings. */
data class IslandBackup(val profile: Profile, val rewards: List<Reward>, val chests: List<Chest>) {
    val xp: Int get() = rewards.sumOf { it.xp }
    fun validate() {
        require(profile.islandName.isNotBlank() && profile.islandName.length <= 28) { "Invalid island name" }
        require(profile.roof in 0..2 && profile.budgetMinutes in 15..180)
        require(profile.appearance in listOf("Daylight", "Dusk", "System"))
        require(profile.personality in listOf("Gentle", "Sarcastic", "Silent"))
        ZoneId.of(profile.zone)
        require(Worlds.find(profile.region) != null)
        val items = Progression.milestones.map { it.id }.toSet() + Treasures.all.map { it.id }
        require(profile.hiddenItems.packages().all { it in items })
        require(rewards.size <= 18_000 && rewards.map { it.key }.distinct().size == rewards.size)
        rewards.forEach {
            LocalDate.parse(it.date)
            require((it.kind == "USAGE" && it.key == "usage:${it.date}" && it.xp in 0..Progression.DAILY_CAP) ||
                (it.kind == "GOAL" && it.key == "goal:${it.date}" && it.xp == Progression.GOAL_BONUS))
        }
        require(rewards.sumOf { it.xp.toLong() } <= 100_000_000)
        require(chests.size <= 2_000 && chests.map { it.key }.distinct().size == chests.size)
        require(chests.filter { it.itemId != Treasures.KEEPSAKE }.map { it.itemId }.distinct().size == chests.count { it.itemId != Treasures.KEEPSAKE })
        chests.forEach {
            require(it.key.length <= 80 && it.source.length <= 80 && it.earnedAt >= 0 && (it.openedAt == null || it.openedAt >= 0))
            require(it.economyVersion == Treasures.ECONOMY_VERSION)
            require(it.rarity == (Treasures.find(it.itemId)?.rarity?.label ?: "Keepsake"))
            require(it.itemId == Treasures.KEEPSAKE || Treasures.find(it.itemId) != null)
            if (it.key.startsWith("milestone:")) require(it.key.removePrefix("milestone:").toInt() in Treasures.chestMilestones.filter { threshold -> threshold <= xp })
            else { require(it.key.startsWith("week:")); LocalDate.parse(it.key.removePrefix("week:")) }
        }
    }
    fun encode(): String {
        validate()
        val p = profile
        val value = JSONObject().put("version", 1).put("profile", JSONObject()
            .put("name", p.islandName).put("budget", p.budgetMinutes).put("roof", p.roof)
            .put("personality", p.personality).put("hidden", p.hiddenItems).put("zone", p.zone)
            .put("appearance", p.appearance).put("reducedMotion", p.reducedMotion).put("haptics", p.haptics)
            .put("region", p.region).put("guideDismissed", p.guideDismissed).put("hasPlacedTreasure", p.hasPlacedTreasure))
            .put("rewards", JSONArray().apply { rewards.forEach { put(JSONObject().put("key",it.key).put("date",it.date).put("kind",it.kind).put("xp",it.xp)) } })
            .put("chests", JSONArray().apply { chests.forEach { put(JSONObject().put("key",it.key).put("source",it.source)
                .put("earnedAt",it.earnedAt).put("itemId",it.itemId).put("rarity",it.rarity)
                .put("openedAt",it.openedAt ?: JSONObject.NULL).put("economyVersion",it.economyVersion)) } }).toString()
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "This backup is too large for the current cloud format." }
        return value
    }
    companion object {
        const val MAX_BYTES = 900_000
        fun decode(value: String): IslandBackup {
            require(value.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            val json = JSONObject(value); require(json.getInt("version") == 1)
            val p = json.getJSONObject("profile")
            val rewards = json.getJSONArray("rewards")
            val chests = json.getJSONArray("chests")
            require(rewards.length() <= 18_000 && chests.length() <= 2_000)
            return IslandBackup(Profile(islandName = p.getString("name"), budgetMinutes = p.getInt("budget"), roof = p.getInt("roof"),
                personality = p.getString("personality"), hiddenItems = p.getString("hidden"), zone = p.getString("zone"),
                appearance = p.getString("appearance"), reducedMotion = p.getBoolean("reducedMotion"), haptics = p.getBoolean("haptics"),
                region = p.getString("region"), guideDismissed = p.getBoolean("guideDismissed"), hasPlacedTreasure = p.getBoolean("hasPlacedTreasure")),
                List(rewards.length()) { rewards.getJSONObject(it).let { r -> Reward(r.getString("key"), r.getString("date"), r.getString("kind"), r.getInt("xp")) } },
                List(chests.length()) { chests.getJSONObject(it).let { c -> Chest(c.getString("key"), c.getString("source"), c.getLong("earnedAt"),
                    c.getString("itemId"), c.getString("rarity"), if (c.isNull("openedAt")) null else c.getLong("openedAt"), c.getInt("economyVersion")) } }).also { it.validate() }
        }
    }
}

class IslandBackupStore(private val database: ScrollDatabase) {
    suspend fun snapshot(): IslandBackup = database.withTransaction {
        IslandBackup(requireNotNull(database.dao().profile()), database.dao().rewards(), database.dao().chests())
    }
    suspend fun restore(backup: IslandBackup, stillSameAccount: () -> Boolean) = database.withTransaction {
        backup.validate(); check(stillSameAccount()) { "Account changed. Please try again." }
        val dao = database.dao(); val local = dao.profile() ?: Profile()
        dao.clearDays(); dao.clearRewards(); dao.clearChests(); dao.clearBreaks()
        backup.rewards.forEach { dao.saveReward(it) }; backup.chests.forEach { dao.insertChest(it) }
        // Device-specific selection stays local; history starts again and must earn full-day coverage.
        dao.saveProfile(backup.profile.copy(selectedApps = local.selectedApps, onboarded = local.onboarded,
            paused = local.paused, trackingSince = 0, lastObserved = 0))
    }
}
