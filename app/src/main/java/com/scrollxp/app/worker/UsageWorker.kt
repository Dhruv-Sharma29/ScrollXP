package com.scrollxp.app.worker

import kotlinx.coroutines.CancellationException
import android.content.Context
import androidx.work.*
import com.scrollxp.app.data.ScrollRepository
import java.util.concurrent.TimeUnit

class UsageWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        ScrollRepository(applicationContext).reconcile()
        com.scrollxp.app.widget.IslandWidget.refresh(applicationContext)
        Result.success()
    } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { Result.retry() }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("usage-reconcile", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
