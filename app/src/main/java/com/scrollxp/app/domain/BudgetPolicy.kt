package com.scrollxp.app.domain

object BudgetPolicy {
    fun result(now: Long, end: Long, usageMillis: Long, budgetMinutes: Int,
               fullDay: Boolean, endEvidence: Boolean): String = when {
        now < end -> "PENDING"
        !fullDay || !endEvidence -> "UNKNOWN"
        usageMillis <= budgetMinutes * 60_000L -> "SUCCESS"
        else -> "OVER_BUDGET"
    }
}
