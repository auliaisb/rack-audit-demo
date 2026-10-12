package com.rackaudit.app.data

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Screens and ViewModels depend on this interface only; tests can fake it. */
interface AssignmentRepository {
    fun observeMine(): Flow<List<Assignment>>
    /** [serverOnly] = skip the local cache and fail when offline (used by refresh, so stale data is never passed off as fresh). */
    suspend fun get(id: String, serverOnly: Boolean = false): Assignment
    suspend fun photos(assignmentId: String, serverOnly: Boolean = false): List<Photo>
    /** Pull-to-refresh on the list: fetch from the server; the live listener then delivers the new items. Throws when offline. */
    suspend fun refreshMine()
    suspend fun addPhoto(assignmentId: String, jpeg: File, caption: String): Photo
    suspend fun removePhoto(assignmentId: String, photo: Photo)
    suspend fun submit(assignmentId: String, note: String)
}
