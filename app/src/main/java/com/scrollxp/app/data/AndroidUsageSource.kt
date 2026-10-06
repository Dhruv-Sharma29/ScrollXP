package com.scrollxp.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.os.UserManager
import com.scrollxp.app.domain.EventKind
import com.scrollxp.app.domain.TimelineEvent

data class InstalledApp(val packageName: String, val name: String)
class AndroidUsageSource(private val context: Context) {
    @Suppress("DEPRECATION")
    fun hasAccess(): Boolean {
        val manager = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            manager.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else manager.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }
    @Suppress("DEPRECATION")
    fun installedApps(): List<InstalledApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).map { InstalledApp(it.activityInfo.packageName, it.loadLabel(context.packageManager).toString()) }
        .filter { it.packageName != context.packageName }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }
    fun events(start: Long, end: Long): List<TimelineEvent>? {
        if (!hasAccess() || !context.getSystemService(UserManager::class.java).isUserUnlocked) return null
        return try {
            val stream = context.getSystemService(UsageStatsManager::class.java).queryEvents(start, end) ?: return null
            val output = mutableListOf<TimelineEvent>()
            val event = UsageEvents.Event()
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                // 1/2 are also foreground/background events on pre-29 Android.
                val kind = when (event.eventType) {
                    1 -> EventKind.RESUME
                    2 -> EventKind.PAUSE
                    15 -> EventKind.SCREEN_ON
                    16 -> EventKind.SCREEN_OFF
                    17 -> EventKind.LOCK
                    18 -> EventKind.UNLOCK
                    26 -> EventKind.SHUTDOWN
                    27 -> EventKind.STARTUP
                    else -> null
                }
                if (kind != null) output += TimelineEvent(event.timeStamp, kind, event.packageName.orEmpty(), event.className.orEmpty())
            }
            output
        } catch (_: SecurityException) { null }
    }
}
