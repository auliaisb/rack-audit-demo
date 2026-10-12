package com.rackaudit.app.domain

/** Wire values match contract/assignment-status.json and the Firestore documents. */
enum class AssignmentStatus(val wire: String) {
    ASSIGNED("assigned"),
    SUBMITTED("submitted"),
    APPROVED("approved"),
    REJECTED("rejected"),
    CANCELLED("cancelled");

    companion object {
        /** Unknown values (e.g. a newer server) map to null so the UI can degrade instead of crash. */
        fun fromWire(value: String?): AssignmentStatus? = entries.firstOrNull { it.wire == value }
    }
}

enum class Actor(val wire: String) { SALES("sales"), SUPERVISOR("supervisor") }

data class Transition(val from: AssignmentStatus, val to: AssignmentStatus, val by: Actor)

/**
 * Mirror of the status contract. [ContractTest] fails if this drifts from the JSON,
 * and the Firestore rules / dashboard are tested against the same JSON.
 */
object StatusRules {
    val transitions: Set<Transition> = setOf(
        Transition(AssignmentStatus.ASSIGNED, AssignmentStatus.SUBMITTED, Actor.SALES),
        Transition(AssignmentStatus.ASSIGNED, AssignmentStatus.CANCELLED, Actor.SUPERVISOR),
        Transition(AssignmentStatus.SUBMITTED, AssignmentStatus.APPROVED, Actor.SUPERVISOR),
        Transition(AssignmentStatus.SUBMITTED, AssignmentStatus.REJECTED, Actor.SUPERVISOR),
        Transition(AssignmentStatus.REJECTED, AssignmentStatus.SUBMITTED, Actor.SALES),
        Transition(AssignmentStatus.REJECTED, AssignmentStatus.CANCELLED, Actor.SUPERVISOR),
    )

    fun canTransition(from: AssignmentStatus, to: AssignmentStatus, by: Actor): Boolean =
        Transition(from, to, by) in transitions

    /** Sales may add/remove photos and (re)submit exactly when they can move to SUBMITTED. */
    fun salesCanEdit(status: AssignmentStatus): Boolean =
        canTransition(status, AssignmentStatus.SUBMITTED, Actor.SALES)
}

/** Mirror of "limits" in the contract. */
object Limits {
    const val MIN_PHOTOS = 1
    const val MAX_PHOTOS = 8
    const val NOTE_MAX_LENGTH = 1000
    const val CAPTION_MAX_LENGTH = 200
    const val PHOTO_MAX_BYTES = 1_500_000
    const val PHOTO_MAX_EDGE_PX = 1600
}
