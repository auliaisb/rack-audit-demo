package com.rackaudit.app.domain

import com.rackaudit.app.data.Assignment

/**
 * What a rep should see first: work they can act on (assigned/rejected), soonest due first;
 * then waiting-for-review; then finished ones, most recently due first.
 */
object AssignmentOrdering {
    private fun group(a: Assignment): Int = when (a.status) {
        AssignmentStatus.ASSIGNED, AssignmentStatus.REJECTED -> 0
        AssignmentStatus.SUBMITTED -> 1
        else -> 2 // approved, cancelled, or an unknown status from a newer backend
    }

    fun sort(items: List<Assignment>): List<Assignment> {
        val dueAsc = compareBy<Assignment> { it.dueDateMillis ?: Long.MAX_VALUE }
        val dueDesc = compareByDescending<Assignment> { it.dueDateMillis ?: Long.MIN_VALUE }
        return items.sortedWith { a, b ->
            val g = group(a).compareTo(group(b))
            when {
                g != 0 -> g
                group(a) == 2 -> dueDesc.compare(a, b)
                else -> dueAsc.compare(a, b)
            }
        }
    }
}
