package com.rackaudit.app.ui.detail

import com.rackaudit.app.data.Assignment
import com.rackaudit.app.data.AssignmentRepository
import com.rackaudit.app.data.Photo
import com.rackaudit.app.domain.AssignmentStatus
import com.rackaudit.app.domain.Limits
import com.rackaudit.app.domain.StatusRules
import com.rackaudit.app.domain.SubmissionError
import com.rackaudit.app.domain.SubmissionValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** Turns a raw camera file into an upload-ready JPEG (null = could not process). Android impl wraps ImageProcessor. */
fun interface PhotoPreparer {
    fun prepare(captured: File): File?
}

enum class Busy { NONE, UPLOADING, SUBMITTING }

enum class UiMessage { UPLOAD_FAILED, SUBMIT_FAILED, PHOTO_PROCESS_FAILED, TOO_MANY_PHOTOS, CAMERA_FAILED, SUBMITTED_OK }

data class DetailState(
    val loading: Boolean = true,
    val loadError: Boolean = false,
    val assignment: Assignment? = null,
    val photos: List<Photo> = emptyList(),
    val note: String = "",
    val caption: String = "",
    val busy: Busy = Busy.NONE,
    val errors: List<SubmissionError> = emptyList(),
    val message: UiMessage? = null,
) {
    /** Single place deciding whether the edit controls are shown — derived from the shared status contract. */
    val canEdit: Boolean get() = assignment?.status?.let(StatusRules::salesCanEdit) == true
}

/**
 * All the screen logic, with no Android types, so it is unit-tested on the JVM.
 * The ViewModel is only a holder that supplies viewModelScope.
 */
class DetailModel(
    private val assignmentId: String,
    private val repo: AssignmentRepository,
    private val preparer: PhotoPreparer,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, loadError = false) }
        scope.launch {
            try {
                val a = repo.get(assignmentId)
                val photos = repo.photos(assignmentId)
                _state.update {
                    it.copy(
                        loading = false,
                        assignment = a,
                        photos = photos,
                        // keep what the rep already typed; otherwise show the saved note (after a rejection)
                        note = it.note.ifEmpty { a.note },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, loadError = true) }
            }
        }
    }

    fun onNoteChange(value: String) = _state.update {
        it.copy(note = value.take(Limits.NOTE_MAX_LENGTH + 1), errors = emptyList(), message = null)
    }

    fun onCaptionChange(value: String) = _state.update { it.copy(caption = value.take(Limits.CAPTION_MAX_LENGTH)) }

    fun onCameraFailed() = _state.update { it.copy(message = UiMessage.CAMERA_FAILED) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun onPhotoCaptured(captured: File) {
        val s = _state.value
        if (!s.canEdit || s.busy != Busy.NONE) {
            captured.delete()
            return
        }
        if (s.photos.size >= Limits.MAX_PHOTOS) {
            captured.delete()
            _state.update { it.copy(message = UiMessage.TOO_MANY_PHOTOS) }
            return
        }
        _state.update { it.copy(busy = Busy.UPLOADING, message = null) }
        scope.launch {
            var prepared: File? = null
            try {
                prepared = preparer.prepare(captured)
                if (prepared == null) {
                    _state.update { it.copy(busy = Busy.NONE, message = UiMessage.PHOTO_PROCESS_FAILED) }
                    return@launch
                }
                val photo = repo.addPhoto(assignmentId, prepared, _state.value.caption)
                _state.update { it.copy(busy = Busy.NONE, photos = it.photos + photo, caption = "", errors = emptyList()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A timed-out write is queued, not cancelled, and may still land (see WRITE_TIMEOUT_MS).
                // Show the server's truth: if the photo arrived, it is not a failure.
                val before = _state.value.photos.size
                val fresh = runCatching { repo.photos(assignmentId) }.getOrNull()
                val landed = fresh != null && fresh.size > before
                _state.update {
                    it.copy(
                        busy = Busy.NONE,
                        photos = fresh ?: it.photos,
                        caption = if (landed) "" else it.caption,
                        message = if (landed) null else UiMessage.UPLOAD_FAILED,
                    )
                }
            } finally {
                captured.delete()
                if (prepared != null && prepared != captured) prepared.delete()
            }
        }
    }

    fun onRemovePhoto(photo: Photo) {
        val s = _state.value
        if (!s.canEdit || s.busy != Busy.NONE) return
        _state.update { it.copy(busy = Busy.UPLOADING, message = null) }
        scope.launch {
            try {
                repo.removePhoto(assignmentId, photo)
                _state.update { it.copy(busy = Busy.NONE, photos = it.photos.filterNot { p -> p.id == photo.id }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = Busy.NONE, message = UiMessage.UPLOAD_FAILED) }
            }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canEdit || s.busy != Busy.NONE) return // double-tap / wrong state guard
        val problems = SubmissionValidator.validate(s.note, s.photos.size)
        if (problems.isNotEmpty()) {
            _state.update { it.copy(errors = problems) }
            return
        }
        _state.update { it.copy(busy = Busy.SUBMITTING, errors = emptyList(), message = null) }
        scope.launch {
            try {
                repo.submit(assignmentId, s.note)
                val fresh = repo.get(assignmentId)
                _state.update { it.copy(busy = Busy.NONE, assignment = fresh, message = UiMessage.SUBMITTED_OK) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The write may still be queued (see WRITE_TIMEOUT_MS) — show the server's truth, not our guess.
                val fresh = runCatching { repo.get(assignmentId) }.getOrNull()
                val actuallySubmitted = fresh?.status == AssignmentStatus.SUBMITTED
                _state.update {
                    it.copy(
                        busy = Busy.NONE,
                        assignment = fresh ?: it.assignment,
                        message = if (actuallySubmitted) UiMessage.SUBMITTED_OK else UiMessage.SUBMIT_FAILED,
                    )
                }
            }
        }
    }
}
