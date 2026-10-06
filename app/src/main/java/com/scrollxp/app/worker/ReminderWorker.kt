package com.scrollxp.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.scrollxp.app.MainActivity
import com.scrollxp.app.R
import com.scrollxp.app.data.ReminderStore
import com.scrollxp.app.data.ScrollDatabase
import com.scrollxp.app.domain.ReminderPolicy
import com.scrollxp.app.domain.ReminderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { withContext(Dispatchers.IO) {
        ReminderScheduler.gate.withLock {
            val store = ReminderStore(applicationContext)
            val settings = store.settings()
            if (!settings.enabled || inputData.getLong("revision", -1) != settings.revision) return@withLock Result.success()
            val now = Instant.now()
            val due = Instant.ofEpochMilli(inputData.getLong("due", 0))
            val profile = ScrollDatabase.get(applicationContext).dao().profile()
            // Skip very late deliveries rather than catching up a backlog of reminders.
            if (now >= due && Duration.between(due, now) <= Duration.ofHours(6) &&
                profile?.onboarded == true && !profile.paused && ReminderScheduler.allowed(applicationContext) &&
                store.claim(now, ZoneId.systemDefault())) {
                try { ReminderScheduler.post(applicationContext) } catch (_: SecurityException) { /* Permission may have changed since the check. */ }
            }
            ReminderScheduler.enqueue(applicationContext, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
            Result.success()
        }
    } } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Result.retry() }
}

object ReminderScheduler {
    internal val gate = Mutex()
    const val WORK = "daily-island-reminder"
    const val CHANNEL = "island-check-in"
    private const val NOTIFICATION = 104

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Island check-in", NotificationManager.IMPORTANCE_LOW).apply {
                description = "One optional, quiet reminder to visit your island"
                setShowBadge(false)
            })
    }
    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    suspend fun update(context: Context, value: ReminderSettings) = withContext(Dispatchers.IO) {
        gate.withLock {
            val store = ReminderStore(context)
            store.save(value)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
            enqueue(context, store.settings(), ExistingWorkPolicy.REPLACE)
        }
    }
    suspend fun reset(context: Context) = withContext(Dispatchers.IO) {
        gate.withLock {
            ReminderStore(context).clear()
            WorkManager.getInstance(context).cancelUniqueWork(WORK).result.get()
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
        }
    }
    suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
        gate.withLock { enqueue(context, ReminderStore(context).settings(), ExistingWorkPolicy.KEEP) }
    }
    suspend fun replan(context: Context) = withContext(Dispatchers.IO) {
        gate.withLock { enqueue(context, ReminderStore(context).settings(), ExistingWorkPolicy.REPLACE) }
    }
    internal fun enqueue(context: Context, settings: ReminderSettings, policy: ExistingWorkPolicy) {
        val now = Instant.now()
        val next = ReminderStore(context).next(now, ZoneId.systemDefault())
        val manager = WorkManager.getInstance(context)
        if (next == null) { manager.cancelUniqueWork(WORK).result.get(); return }
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis().coerceAtLeast(1000), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("revision" to settings.revision, "due" to next.toEpochMilli())).build()
        manager.enqueueUniqueWork(WORK, policy, request).result.get()
    }
    internal fun post(context: Context) {
        channel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_island)
            .setContentTitle("A little moment for your island")
            .setContentText("Visit your world, arrange a treasure, or check your balance goal.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification)
    }
}
