package com.scrollxp.app.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Recompute wall-clock times when Android changes its clock or timezone. */
class ReminderClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { ReminderScheduler.replan(context.applicationContext); NightlyScheduler.enqueue(context.applicationContext, androidx.work.ExistingWorkPolicy.REPLACE) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* A future app refresh retries scheduling if Android is temporarily unavailable. */ }
            finally { pending.finish() }
        }
    }
}
