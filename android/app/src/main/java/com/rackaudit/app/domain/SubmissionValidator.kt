package com.rackaudit.app.domain

enum class SubmissionError { NOTE_REQUIRED, NOTE_TOO_LONG, TOO_FEW_PHOTOS, TOO_MANY_PHOTOS }

/** Friendly pre-check. The Firestore rules are the real gate and re-check all of this. */
object SubmissionValidator {
    fun validate(note: String, photoCount: Int): List<SubmissionError> = buildList {
        val trimmed = note.trim()
        if (trimmed.isEmpty()) add(SubmissionError.NOTE_REQUIRED)
        if (trimmed.length > Limits.NOTE_MAX_LENGTH) add(SubmissionError.NOTE_TOO_LONG)
        if (photoCount < Limits.MIN_PHOTOS) add(SubmissionError.TOO_FEW_PHOTOS)
        if (photoCount > Limits.MAX_PHOTOS) add(SubmissionError.TOO_MANY_PHOTOS)
    }
}
