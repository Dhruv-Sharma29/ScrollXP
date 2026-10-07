package com.scrollxp.app.worker

import android.content.Context
import androidx.work.*
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.scrollxp.app.BuildConfig
import com.scrollxp.app.data.ScrollRepository
import com.scrollxp.app.domain.NightlyPolicy
import com.scrollxp.app.online.CloudAccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.time.*
import java.util.concurrent.TimeUnit

data class NightlySettings(val uid: String? = null, val expected: String = "", val revision: Long = 0,
    val status: String = "Nightly cloud backup is off.")
class NightlyStore(context: Context) {
    private val prefs = context.getSharedPreferences("nightly_backup", Context.MODE_PRIVATE)
    fun settings() = NightlySettings(prefs.getString("uid", null), prefs.getString("expected", "").orEmpty(), prefs.getLong("revision", 0),
        prefs.getString("status", "Nightly cloud backup is off.").orEmpty())
    fun update(uid: String?, expected: String, status: String) {
        prefs.edit().putString("uid", uid).putString("expected", expected).putString("status", status)
            .putLong("revision", settings().revision + 1).commit()
    }
    fun receipt(expected: String, date: String, status: String) {
        prefs.edit().putString("expected", expected).putString("date", date).putString("status", status).commit()
    }
    fun date(): String? = prefs.getString("date", null)
    companion object {
        fun stamp(time: Timestamp?) = time?.let { "${it.seconds}:${it.nanoseconds}" }.orEmpty()
    }
}

object NightlyScheduler {
    val gate = Mutex()
    const val WORK = "nightly-island-backup"
    fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val config = NightlyStore(context).settings(); if (config.uid == null) return
        val now = Instant.now(); val due = NightlyPolicy.next(now, ZoneId.systemDefault())
        val request = OneTimeWorkRequestBuilder<NightlyBackupWorker>()
            .setInitialDelay(Duration.between(now, due).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .setInputData(workDataOf("uid" to config.uid, "revision" to config.revision, "due" to due.toEpochMilli())).build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK, policy, request)
    }
    suspend fun disable(context: Context, status: String = "Nightly cloud backup is off.") = gate.withLock {
        NightlyStore(context).update(null, "", status)
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
    }
    suspend fun enable(context: Context, uid: String, time: Timestamp?) = gate.withLock {
        NightlyStore(context).update(uid, NightlyStore.stamp(time), "Scheduled around 1 a.m. while online; Android may delay it.")
        enqueue(context, ExistingWorkPolicy.REPLACE)
    }
}

/** A compare-and-set save never silently replaces a cloud save made by another device. */
class NightlyBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = NightlyScheduler.gate.withLock {
        val store = NightlyStore(applicationContext); val config = store.settings()
        if (config.uid == null || config.uid != inputData.getString("uid") || config.revision != inputData.getLong("revision", -1)) return@withLock Result.success()
        fun next(): Result { NightlyScheduler.enqueue(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE); return Result.success() }
        val now = Instant.now(); val date = now.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        if (!NightlyPolicy.timely(now, Instant.ofEpochMilli(inputData.getLong("due", 0))) || store.date() == date) return@withLock next()
        try {
            if (!BuildConfig.ONLINE_ENABLED || !BuildConfig.FIREBASE_CONFIGURED || FirebaseApp.getApps(applicationContext).isEmpty()) return@withLock next()
            val auth = FirebaseAuth.getInstance(); val user = auth.currentUser
            if (user?.uid != config.uid || !user.isEmailVerified) {
                store.update(null, "", "Paused: sign in and verify the account, then enable nightly backup again."); return@withLock Result.success()
            }
            val db = com.scrollxp.app.online.FirebaseStores.database(); val cloud = CloudAccountStore(db)
            if (cloud.deletionStarted(user.uid)) { store.update(null, "", "Paused for account deletion."); return@withLock Result.success() }
            val repo = ScrollRepository(applicationContext)
            if (repo.dao.profile()?.onboarded != true) return@withLock next()
            repo.reconcile()
            val snapshot = repo.backup()
            check(auth.currentUser?.uid == config.uid) { "Account changed." }
            val saved = cloud.saveIfUnchanged(user.uid, snapshot, config.expected)
            store.receipt(NightlyStore.stamp(saved.getTimestamp("updatedAt")), date, "Last nightly save: $date · ${snapshot.xp} XP")
            next()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: IllegalStateException) {
            store.update(null, "", "Paused: cloud backup or account changed. Review the latest backup before enabling again."); Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else {
                store.receipt(config.expected, date, "Nightly save could not connect. Your island is saved locally; retry tomorrow."); next()
            }
        }
    }
}
