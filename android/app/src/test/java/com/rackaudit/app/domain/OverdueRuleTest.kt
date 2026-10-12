package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Written BEFORE the implementation (TDD). Rule, from the product owner's mouth:
 *  - A task is overdue when the rep can still act on it (assigned / rejected) and the due moment has passed.
 *  - Once submitted, it is never "overdue" any more, even if it was late.
 *  - Exactly at the due moment is NOT overdue yet.
 *  - No due date, or an unknown status -> not overdue.
 *  - "Days overdue" rounds UP: 1 ms late is 1 day late; exactly 24 h late is still 1 day; 24 h + 1 ms is 2.
 */
class OverdueRuleTest {
    private val day = 86_400_000L
    private val due = 1_000_000_000_000L

    private fun a(status: AssignmentStatus?, dueMillis: Long? = due) =
        Assignment("a", "t", "", "s", "", dueMillis, status, 0, "", "")

    @Test fun assignedAndPastDue_isOverdue() {
        assertTrue(OverdueRule.isOverdue(a(AssignmentStatus.ASSIGNED), due + 1))
    }

    @Test fun rejectedAndPastDue_isOverdue() {
        assertTrue(OverdueRule.isOverdue(a(AssignmentStatus.REJECTED), due + 1))
    }

    @Test fun exactlyAtDueMoment_isNotOverdue() {
        assertFalse(OverdueRule.isOverdue(a(AssignmentStatus.ASSIGNED), due))
    }

    @Test fun beforeDue_isNotOverdue() {
        assertFalse(OverdueRule.isOverdue(a(AssignmentStatus.ASSIGNED), due - 1))
    }

    @Test fun submittedApprovedCancelled_areNeverOverdue() {
        for (s in listOf(AssignmentStatus.SUBMITTED, AssignmentStatus.APPROVED, AssignmentStatus.CANCELLED)) {
            assertFalse("$s", OverdueRule.isOverdue(a(s), due + 10 * day))
        }
    }

    @Test fun missingDueDateOrUnknownStatus_isNotOverdue() {
        assertFalse(OverdueRule.isOverdue(a(AssignmentStatus.ASSIGNED, dueMillis = null), due + day))
        assertFalse(OverdueRule.isOverdue(a(null), due + day))
    }

    @Test fun daysOverdue_roundsUp() {
        val x = a(AssignmentStatus.ASSIGNED)
        assertEquals(1, OverdueRule.daysOverdue(x, due + 1))
        assertEquals(1, OverdueRule.daysOverdue(x, due + day))
        assertEquals(2, OverdueRule.daysOverdue(x, due + day + 1))
        assertEquals(10, OverdueRule.daysOverdue(x, due + 10 * day))
    }

    @Test fun daysOverdue_isZeroWhenNotOverdue() {
        assertEquals(0, OverdueRule.daysOverdue(a(AssignmentStatus.ASSIGNED), due))
        assertEquals(0, OverdueRule.daysOverdue(a(AssignmentStatus.SUBMITTED), due + 5 * day))
    }
}
