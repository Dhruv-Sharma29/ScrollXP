package com.scrollxp.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 1,
    val islandName: String = "Little haven",
    val selectedApps: String = "",
    val budgetMinutes: Int = 45,
    val pendingApps: String? = null,
    val pendingBudget: Int? = null,
    val pendingAt: Long = 0,
    val roof: Int = 0,
    val personality: String = "Gentle",
    val hiddenItems: String = "",
    val onboarded: Boolean = false,
    val paused: Boolean = false,
    val trackingSince: Long = 0,
    val lastObserved: Long = 0,
    val zone: String = "",
    @ColumnInfo(defaultValue = "'Daylight'") val appearance: String = "Daylight",
    @ColumnInfo(defaultValue = "0") val reducedMotion: Boolean = false,
    @ColumnInfo(defaultValue = "1") val haptics: Boolean = true,
    @ColumnInfo(defaultValue = "'meadow'") val region: String = "meadow",
    @ColumnInfo(defaultValue = "0") val guideDismissed: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hasPlacedTreasure: Boolean = false,
)
@Entity(tableName = "days")
data class DayRecord(
    @PrimaryKey val date: String,
    val start: Long,
    val end: Long,
    val selectedApps: String,
    val budgetMinutes: Int,
    val eligibleSince: Long,
    val usageMillis: Long = 0,
    val perAppJson: String = "{}",
    val measuredAt: Long = 0,
    val partial: Boolean = true,
    val goalStatus: String = "PENDING",
)
@Entity(tableName = "tracking_breaks")
data class TrackingBreak(@PrimaryKey val start: Long, val end: Long = Long.MAX_VALUE)

@Entity(tableName = "rewards")
data class Reward(@PrimaryKey val key: String, val date: String, val kind: String, val xp: Int)

@Entity(tableName = "chests")
data class Chest(@PrimaryKey val key: String, val source: String, val earnedAt: Long,
    val itemId: String, val rarity: String, val openedAt: Long? = null, val economyVersion: Int = 1)

@Dao
interface ScrollDao {
    @Query("SELECT * FROM profile WHERE id = 1") fun observeProfile(): Flow<Profile?>
    @Query("SELECT * FROM profile WHERE id = 1") suspend fun profile(): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: Profile)
    @Query("SELECT * FROM days ORDER BY start DESC LIMIT 7") fun observeDays(): Flow<List<DayRecord>>
    @Query("SELECT * FROM days WHERE date = :date") suspend fun day(date: String): DayRecord?
    @Query("SELECT * FROM days WHERE goalStatus = 'PENDING'") suspend fun pendingDays(): List<DayRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDay(day: DayRecord)
    @Query("SELECT COALESCE(SUM(xp), 0) FROM rewards") fun observeXp(): Flow<Int>
    @Query("SELECT COALESCE(SUM(xp), 0) FROM rewards") suspend fun totalXp(): Int
    @Query("SELECT * FROM rewards ORDER BY `key`") suspend fun rewards(): List<Reward>
    @Query("SELECT * FROM days ORDER BY start DESC LIMIT 30") suspend fun recentHistory(): List<DayRecord>
    @Query("SELECT * FROM days ORDER BY start DESC LIMIT 90") suspend fun extendedHistory(): List<DayRecord>
    @Query("SELECT DISTINCT date FROM rewards WHERE kind = 'GOAL'") fun observeGoalDates(): Flow<List<String>>
    @Query("SELECT DISTINCT date FROM rewards WHERE kind = 'GOAL'") suspend fun goalDates(): List<String>
    @Query("SELECT * FROM chests ORDER BY earnedAt, `key`") fun observeChests(): Flow<List<Chest>>
    @Query("SELECT * FROM chests ORDER BY earnedAt, `key`") suspend fun chests(): List<Chest>
    @Query("SELECT * FROM chests WHERE `key` = :key") suspend fun chest(key: String): Chest?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertChest(chest: Chest): Long
    @Update suspend fun updateChest(chest: Chest)
    @Query("DELETE FROM chests") suspend fun clearChests()
    @Query("SELECT * FROM rewards WHERE `key` = :key") suspend fun reward(key: String): Reward?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveReward(reward: Reward)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveBreak(value: TrackingBreak)
    @Query("SELECT * FROM tracking_breaks WHERE end > :start") suspend fun breaks(start: Long): List<TrackingBreak>
    @Query("UPDATE tracking_breaks SET end = :now WHERE end = 9223372036854775807") suspend fun closeBreaks(now: Long)
    @Query("DELETE FROM tracking_breaks") suspend fun clearBreaks()
    @Query("DELETE FROM days") suspend fun clearDays()
    @Query("DELETE FROM rewards") suspend fun clearRewards()
    @Query("DELETE FROM profile") suspend fun clearProfile()
}
@Database(entities = [Profile::class, DayRecord::class, Reward::class, TrackingBreak::class, Chest::class], version = 4, exportSchema = true)
abstract class ScrollDatabase : RoomDatabase() {
    abstract fun dao(): ScrollDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN region TEXT NOT NULL DEFAULT 'meadow'")
                db.execSQL("ALTER TABLE profile ADD COLUMN guideDismissed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE profile ADD COLUMN hasPlacedTreasure INTEGER NOT NULL DEFAULT 0")
                // Preserve the first-placement step for existing, visibly placed chest cosmetics.
                db.execSQL("UPDATE profile SET hasPlacedTreasure = 1 WHERE EXISTS (SELECT 1 FROM chests WHERE openedAt IS NOT NULL AND itemId != 'keepsake' AND instr('|' || profile.hiddenItems || '|', '|' || itemId || '|') = 0)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN haptics INTEGER NOT NULL DEFAULT 1")
                db.execSQL("CREATE TABLE IF NOT EXISTS chests (`key` TEXT NOT NULL, source TEXT NOT NULL, earnedAt INTEGER NOT NULL, itemId TEXT NOT NULL, rarity TEXT NOT NULL, openedAt INTEGER, economyVersion INTEGER NOT NULL, PRIMARY KEY(`key`))")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN appearance TEXT NOT NULL DEFAULT 'Daylight'")
                db.execSQL("ALTER TABLE profile ADD COLUMN reducedMotion INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: ScrollDatabase? = null
        fun get(context: Context): ScrollDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, ScrollDatabase::class.java, "scrollxp.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
    }
}
fun String.packages(): Set<String> = split('|').filter { it.isNotBlank() }.toSet()
fun Set<String>.encoded(): String = sorted().joinToString("|")
