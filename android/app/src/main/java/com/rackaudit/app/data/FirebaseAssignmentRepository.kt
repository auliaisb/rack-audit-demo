package com.rackaudit.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.rackaudit.app.domain.AssignmentStatus
import com.rackaudit.app.domain.AssignmentOrdering
import com.rackaudit.app.domain.Limits
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File

class FirebaseAssignmentRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
) : AssignmentRepository {

    private val uid: String
        get() = checkNotNull(auth.currentUser?.uid) { "not signed in" }

    override fun observeMine(): Flow<List<Assignment>> = callbackFlow {
        // Must filter by assignedTo: the rules reject an unfiltered list.
        val registration = db.collection("assignments")
            .whereEqualTo("assignedTo", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    close(error)
                } else if (snap != null) {
                    trySend(AssignmentOrdering.sort(snap.documents.map { it.toAssignment() }))
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun get(id: String, serverOnly: Boolean): Assignment =
        readWithin(serverOnly) { db.collection("assignments").document(id).get(source(serverOnly)).await() }.also {
            check(it.exists()) { "assignment $id not found" }
        }.toAssignment()

    override suspend fun refreshMine() {
        // Updates the local cache, which makes the live listener in observeMine() deliver the new items.
        readWithin(serverOnly = true) { db.collection("assignments").whereEqualTo("assignedTo", uid).get(Source.SERVER).await() }
    }

    private fun source(serverOnly: Boolean) = if (serverOnly) Source.SERVER else Source.DEFAULT

    /** A server-only read offline would wait a long time; give up after READ_TIMEOUT_MS so pull-to-refresh can say so. */
    private suspend fun <T> readWithin(serverOnly: Boolean, block: suspend () -> T): T =
        if (serverOnly) withTimeout(READ_TIMEOUT_MS) { block() } else block()

    /** Reads every photo's bytes (up to 8 x ~0.3 MB). Fine for the detail screen; never call it for the list. */
    override suspend fun photos(assignmentId: String, serverOnly: Boolean): List<Photo> {
        val snap = readWithin(serverOnly) {
            db.collection("assignments").document(assignmentId).collection("photos")
                .orderBy("createdAt", Query.Direction.ASCENDING).get(source(serverOnly)).await()
        }
        return snap.documents.map { d ->
            Photo(d.id, d.getString("caption").orEmpty(), d.getBlob("data")?.toBytes())
        }
    }

    override suspend fun addPhoto(assignmentId: String, jpeg: File, caption: String): Photo {
        val bytes = jpeg.readBytes()
        // The rules reject bigger documents anyway; fail early with a clear error instead of a permission-denied.
        require(bytes.size in 1..Limits.PHOTO_MAX_BYTES) { "photo is ${bytes.size} bytes (max ${Limits.PHOTO_MAX_BYTES})" }

        val photosCol = db.collection("assignments").document(assignmentId).collection("photos")
        val photoId = photosCol.document().id // generated locally, no network
        // The Task completes only when the server acknowledges; offline it hangs, hence the timeout.
        // A timed-out write is still queued and may land later: DetailModel re-reads the photos after a failure.
        withTimeout(WRITE_TIMEOUT_MS) {
            photosCol.document(photoId).set(
                mapOf(
                    "data" to Blob.fromBytes(bytes),
                    "caption" to caption.trim(),
                    "uploadedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
        return Photo(photoId, caption.trim(), bytes)
    }

    override suspend fun removePhoto(assignmentId: String, photo: Photo) {
        withTimeout(WRITE_TIMEOUT_MS) {
            db.collection("assignments").document(assignmentId).collection("photos").document(photo.id).delete().await()
        }
    }

    override suspend fun submit(assignmentId: String, note: String) {
        // Count the real photos so photoCount is honest for an honest client (see docs/data-model.md, limitation 1).
        val count = photos(assignmentId).size
        withTimeout(WRITE_TIMEOUT_MS) {
            db.collection("assignments").document(assignmentId).update(
                mapOf(
                    "status" to AssignmentStatus.SUBMITTED.wire,
                    "note" to note.trim(),
                    "photoCount" to count,
                    "submittedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    companion object {
        /**
         * Firestore write Tasks only complete once the server acknowledges. Offline they would hang
         * forever, so the UI would spin forever. Note: a timeout does NOT cancel the queued write;
         * it may still be applied when the connection returns. Screens reload state from the server
         * after an error for that reason.
         */
        const val WRITE_TIMEOUT_MS = 30_000L

        /** Server-only reads (refresh): short, because the user is looking at a spinner. */
        const val READ_TIMEOUT_MS = 10_000L
    }
}

internal fun DocumentSnapshot.toAssignment(): Assignment = Assignment(
    id = id,
    title = getString("title").orEmpty(),
    instructions = getString("instructions").orEmpty(),
    storeName = getString("storeName").orEmpty(),
    storeAddress = getString("storeAddress").orEmpty(),
    dueDateMillis = getTimestamp("dueDate")?.toDate()?.time,
    status = AssignmentStatus.fromWire(getString("status")),
    photoCount = (getLong("photoCount") ?: 0L).toInt(),
    note = getString("note").orEmpty(),
    reviewNote = getString("reviewNote").orEmpty(),
)
