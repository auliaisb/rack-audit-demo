package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment

/** STUB — compiles, does nothing useful. Exists only so the tests can fail for the RIGHT reason. */
object OverdueRule {
    fun isOverdue(a: Assignment, nowMillis: Long): Boolean = false
    fun daysOverdue(a: Assignment, nowMillis: Long): Int = 0
}
