package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment

object OverdueRule {
    fun isOverdue(a: Assignment, nowMillis: Long): Boolean {
        val due = a.dueDateMillis ?: return false
        val status = a.status ?: return false
        if (status != AssignmentStatus.ASSIGNED && status != AssignmentStatus.REJECTED) return false
        return nowMillis > due
    }

    fun daysOverdue(a: Assignment, nowMillis: Long): Int {
        if (!isOverdue(a, nowMillis)) return 0
        val late = nowMillis - a.dueDateMillis!!
        return ((late + 86_400_000L - 1) / 86_400_000L).toInt()
    }
}
