package com.rackaudit.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusRulesTest {
    @Test fun happyPathIsAllowed() {
        assertTrue(StatusRules.canTransition(AssignmentStatus.ASSIGNED, AssignmentStatus.SUBMITTED, Actor.SALES))
        assertTrue(StatusRules.canTransition(AssignmentStatus.REJECTED, AssignmentStatus.SUBMITTED, Actor.SALES))
    }

    @Test fun salesCannotReviewOrCancel() {
        assertFalse(StatusRules.canTransition(AssignmentStatus.SUBMITTED, AssignmentStatus.APPROVED, Actor.SALES))
        assertFalse(StatusRules.canTransition(AssignmentStatus.SUBMITTED, AssignmentStatus.REJECTED, Actor.SALES))
        assertFalse(StatusRules.canTransition(AssignmentStatus.ASSIGNED, AssignmentStatus.CANCELLED, Actor.SALES))
    }

    @Test fun noSkippingToApproved() {
        for (actor in Actor.entries) {
            assertFalse(StatusRules.canTransition(AssignmentStatus.ASSIGNED, AssignmentStatus.APPROVED, actor))
        }
    }

    @Test fun terminalStatusesHaveNoWayOut() {
        for (terminal in listOf(AssignmentStatus.APPROVED, AssignmentStatus.CANCELLED)) {
            assertTrue(StatusRules.transitions.none { it.from == terminal })
        }
    }

    @Test fun salesCanEditOnlyWhenAssignedOrRejected() {
        assertTrue(StatusRules.salesCanEdit(AssignmentStatus.ASSIGNED))
        assertTrue(StatusRules.salesCanEdit(AssignmentStatus.REJECTED))
        assertFalse(StatusRules.salesCanEdit(AssignmentStatus.SUBMITTED))
        assertFalse(StatusRules.salesCanEdit(AssignmentStatus.APPROVED))
        assertFalse(StatusRules.salesCanEdit(AssignmentStatus.CANCELLED))
    }

    @Test fun unknownWireValueIsNullNotACrash() {
        assertNull(AssignmentStatus.fromWire("archived"))
        assertNull(AssignmentStatus.fromWire(null))
        assertEquals(AssignmentStatus.REJECTED, AssignmentStatus.fromWire("rejected"))
    }
}
