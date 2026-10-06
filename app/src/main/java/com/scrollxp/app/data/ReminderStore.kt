package com.scrollxp.app.data

import android.content.Context
import com.scrollxp.app.domain.ReminderPolicy
import com.scrollxp.app.domain.ReminderSettings
import java.time.Instant
import java.time.ZoneId

/** Small independent preferences; existing Room game records need no migration. */
class ReminderStore(context: Context, name: String = "local_reminders") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    fun settings() = ReminderSettings(
        enabled = prefs.getBoolean("enabled", false),
        minute = prefs.getInt("minute", 1200).coerceIn(0, 1439),
        quietEnabled = prefs.getBoolean("quiet", true),
        quietStart = prefs.getInt("quiet_start", 1320).coerceIn(0, 1439),
        quietEnd = prefs.getInt("quiet_end", 480).coerceIn(0, 1439),
        revision = prefs.getLong("revision", 0),
    )
    fun next(now: Instant, zone: ZoneId) = ReminderPolicy.next(now, zone, settings(),
        prefs.getString("last_date", null), prefs.getLong("last_at", 0))
    fun save(value: ReminderSettings) {
        require(value.minute in 0..1439 && value.quietStart in 0..1439 && value.quietEnd in 0..1439)
        require(!value.quietEnabled || value.quietStart != value.quietEnd)
        check(prefs.edit().putBoolean("enabled", value.enabled).putInt("minute", value.minute)
            .putBoolean("quiet", value.quietEnabled).putInt("quiet_start", value.quietStart)
            .putInt("quiet_end", value.quietEnd).putLong("revision", settings().revision + 1).commit())
    }
    /** Persist before posting so a retried worker or process restart cannot post twice. */
    fun claim(now: Instant, zone: ZoneId): Boolean = synchronized(receiptLock) {
        if (!ReminderPolicy.canDeliver(now, zone, settings(), prefs.getString("last_date", null), prefs.getLong("last_at", 0))) return@synchronized false
        prefs.edit().putString("last_date", now.atZone(zone).toLocalDate().toString())
            .putLong("last_at", now.toEpochMilli()).commit()
    }
    fun clear() { check(prefs.edit().clear().putLong("revision", settings().revision + 1).commit()) }
    companion object { private val receiptLock = Any() }
}
