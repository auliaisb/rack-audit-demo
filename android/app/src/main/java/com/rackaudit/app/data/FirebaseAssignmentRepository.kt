package com.rackaudit.app.data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.rackaudit.app.domain.AssignmentStatus
import com.rackaudit.app.domain.AssignmentOrdering
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.io.File

class FirebaseAssignmentRepository(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
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

    override suspend fun get(id: String): Assignment =
        db.collection("assignments").document(id).get().await().also {
            check(it.exists()) { "assignment $id not found" }
        }.toAssignment()

    override suspend fun photos(assignmentId: String): List<Photo> {
        val snap = db.collection("assignments").document(assignmentId).collection("photos")
            .orderBy("createdAt", Query.Direction.ASCENDING).get().await()
        return snap.documents.map { d ->
            val path = d.getString("storagePath").orEmpty()
            val url = try {
                storage.reference.child(path).downloadUrl.await().toString()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // missing file / denied: show a placeholder instead of failing the whole screen
            }
            Photo(d.id, path, d.getString("caption").orEmpty(), url)
        }
    }

    override suspend fun addPhoto(assignmentId: String, jpeg: File, caption: String): Photo {
        val photosCol = db.collection("assignments").document(assignmentId).collection("photos")
        val photoId = photosCol.document().id // generated locally, no network
        val path = "assignments/$assignmentId/$photoId.jpg"
        val ref = storage.reference.child(path)

        ref.putFile(Uri.fromFile(jpeg), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        try {
            withTimeout(WRITE_TIMEOUT_MS) {
                photosCol.document(photoId).set(
                    mapOf(
                        "storagePath" to path,
                        "caption" to caption.trim(),
                        "uploadedBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
        } catch (e: Exception) {
            // Don't leave an orphan file behind (best effort), then report the original failure.
            runCatching { ref.delete().await() }
            throw e
        }
        return Photo(photoId, path, caption.trim(), ref.downloadUrl.await().toString())
    }

    override suspend fun removePhoto(assignmentId: String, photo: Photo) {
        // Doc first: if this succeeds the photo is gone from the submission even if the file delete fails.
        withTimeout(WRITE_TIMEOUT_MS) {
            db.collection("assignments").document(assignmentId).collection("photos").document(photo.id).delete().await()
        }
        runCatching { storage.reference.child(photo.storagePath).delete().await() }
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
