package com.rackaudit.app.data

import com.rackaudit.app.domain.AssignmentStatus

data class Assignment(
    val id: String,
    val title: String,
    val instructions: String,
    val storeName: String,
    val storeAddress: String,
    val dueDateMillis: Long?,
    /** null = a status this app version does not know; show it read-only. */
    val status: AssignmentStatus?,
    val photoCount: Int,
    val note: String,
    val reviewNote: String,
)

/**
 * A photo lives inside its Firestore document (Bytes field), because Cloud Storage needs a paid plan.
 * Identity is the document id: the bytes never change once stored.
 */
data class Photo(
    val id: String,
    val caption: String,
    /** JPEG bytes (<= Limits.PHOTO_MAX_BYTES); null only if the stored field is missing or malformed. */
    val jpeg: ByteArray?,
) {
    override fun equals(other: Any?): Boolean = other is Photo && id == other.id && caption == other.caption
    override fun hashCode(): Int = 31 * id.hashCode() + caption.hashCode()
}
