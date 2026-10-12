package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment

/**
 * "Overdue" = the rep can still act on the task (per the status contract) and the due moment has passed.
 * Pure function of (assignment, now): the caller supplies the clock, so tests need no fake clock.
 */
object OverdueRule {
    private const val DAY_MS = 86_400_000L

    /** Milliseconds past the due moment, or null if the task is not overdue. */
    private fun lateByMillis(a: Assignment, nowMillis: Long): Long? {
        val due = a.dueDateMillis ?: return null
        val status = a.status ?: return null
        if (!StatusRules.salesCanEdit(status)) return null // submitted/approved/cancelled: never overdue
        return (nowMillis - due).takeIf { it > 0 }
    }

    fun isOverdue(a: Assignment, nowMillis: Long): Boolean = lateByMillis(a, nowMillis) != null

    /** Whole days late, rounded up (1 ms late = 1 day); 0 when not overdue. */
    fun daysOverdue(a: Assignment, nowMillis: Long): Int =
        lateByMillis(a, nowMillis)?.let { ((it + DAY_MS - 1) / DAY_MS).toInt() } ?: 0
}
