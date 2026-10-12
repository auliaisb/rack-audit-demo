package com.rackaudit.app.data

import kotlinx.coroutines.flow.Flow
import java.io.File

/** Screens and ViewModels depend on this interface only; tests can fake it. */
interface AssignmentRepository {
    fun observeMine(): Flow<List<Assignment>>
    suspend fun get(id: String): Assignment
    suspend fun photos(assignmentId: String): List<Photo>
    suspend fun addPhoto(assignmentId: String, jpeg: File, caption: String): Photo
    suspend fun removePhoto(assignmentId: String, photo: Photo)
    suspend fun submit(assignmentId: String, note: String)
}
