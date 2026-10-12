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

data class Photo(
    val id: String,
    val storagePath: String,
    val caption: String,
    /** Download URL; null if the file is missing or access was denied. */
    val url: String?,
)
