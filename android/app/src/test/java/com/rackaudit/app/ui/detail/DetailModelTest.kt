package com.rackaudit.app.ui.detail

import com.rackaudit.app.data.Assignment
import com.rackaudit.app.data.AssignmentRepository
import com.rackaudit.app.data.Photo
import com.rackaudit.app.domain.AssignmentStatus
import com.rackaudit.app.domain.Limits
import com.rackaudit.app.domain.SubmissionError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private class FakeRepo(var status: AssignmentStatus = AssignmentStatus.ASSIGNED) : AssignmentRepository {
    val photoList = mutableListOf<Photo>()
    var failUpload = false
    /** Simulates a timed-out write that Firestore still delivers later (an offline write is queued, not cancelled). */
    var uploadAppliedDespiteFailure = false
    var failSubmit = false
    var submitAppliedDespiteFailure = false
    var submitGate: CompletableDeferred<Unit>? = null
    var submitCalls = 0
    var lastNote: String? = null

    private fun assignment() = Assignment("a1", "Cek rak", "", "Toko A", "", 0L, status, photoList.size, lastNote.orEmpty(), "")

    override fun observeMine(): Flow<List<Assignment>> = emptyFlow()
    override suspend fun get(id: String) = assignment()
    override suspend fun photos(assignmentId: String): List<Photo> = photoList.toList()
    override suspend fun addPhoto(assignmentId: String, jpeg: File, caption: String): Photo {
        val photo = Photo("p${photoList.size + 1}", caption, byteArrayOf(1, 2, 3))
        if (failUpload) {
            if (uploadAppliedDespiteFailure) photoList += photo
            error("network")
        }
        return photo.also { photoList += it }
    }
    override suspend fun removePhoto(assignmentId: String, photo: Photo) { photoList.removeAll { it.id == photo.id } }
    override suspend fun submit(assignmentId: String, note: String) {
        submitCalls++
        submitGate?.await()
        if (submitAppliedDespiteFailure) { status = AssignmentStatus.SUBMITTED; lastNote = note }
        if (failSubmit) error("timeout")
        status = AssignmentStatus.SUBMITTED
        lastNote = note
    }
}

class DetailModelTest {
    private val scope = CoroutineScope(Job() + Dispatchers.Unconfined) // runs launches eagerly: deterministic
    private val okPreparer = PhotoPreparer { it }

    private fun newModel(repo: FakeRepo, preparer: PhotoPreparer = okPreparer) =
        DetailModel("a1", repo, preparer, scope).also { it.load() }

    private fun tmp(): File = File.createTempFile("cap", ".jpg")

    @Test fun cannotSubmitWithoutPhotosOrNote_andRepoIsNotCalled() {
        val repo = FakeRepo()
        val m = newModel(repo)
        m.submit()
        assertEquals(listOf(SubmissionError.NOTE_REQUIRED, SubmissionError.TOO_FEW_PHOTOS), m.state.value.errors)
        assertEquals(0, repo.submitCalls)
    }

    @Test fun happyPath_photoThenNoteThenSubmit() {
        val repo = FakeRepo()
        val m = newModel(repo)
        m.onCaptionChange("Rak atas")
        m.onPhotoCaptured(tmp())
        assertEquals(1, m.state.value.photos.size)
        assertEquals("", m.state.value.caption) // caption consumed by the photo
        m.onNoteChange("Rak penuh, label rapi")
        m.submit()
        assertEquals(AssignmentStatus.SUBMITTED, m.state.value.assignment?.status)
        assertEquals(UiMessage.SUBMITTED_OK, m.state.value.message)
        assertFalse(m.state.value.canEdit)
    }

    @Test fun uploadFailure_showsMessage_addsNoPhoto_andUnblocksUi() {
        val repo = FakeRepo().apply { failUpload = true }
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        assertEquals(UiMessage.UPLOAD_FAILED, m.state.value.message)
        assertEquals(0, m.state.value.photos.size)
        assertEquals(Busy.NONE, m.state.value.busy)
    }

    @Test fun photoWriteTimesOutButLandsLater_isShown_notReportedAsFailure() {
        // Photos now live in Firestore, so a timed-out write is queued, not cancelled (same trap as submit).
        val repo = FakeRepo().apply { failUpload = true; uploadAppliedDespiteFailure = true }
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        assertEquals(1, m.state.value.photos.size) // shown, so the rep does not take it twice
        assertNull(m.state.value.message)
        assertEquals(Busy.NONE, m.state.value.busy)
    }

    @Test fun unprocessablePhoto_isReported() {
        val m = newModel(FakeRepo(), PhotoPreparer { null })
        m.onPhotoCaptured(tmp())
        assertEquals(UiMessage.PHOTO_PROCESS_FAILED, m.state.value.message)
        assertEquals(Busy.NONE, m.state.value.busy)
    }

    @Test fun ninthPhotoIsRefused() {
        val m = newModel(FakeRepo())
        repeat(Limits.MAX_PHOTOS) { m.onPhotoCaptured(tmp()) }
        assertEquals(Limits.MAX_PHOTOS, m.state.value.photos.size)
        m.onPhotoCaptured(tmp())
        assertEquals(Limits.MAX_PHOTOS, m.state.value.photos.size)
        assertEquals(UiMessage.TOO_MANY_PHOTOS, m.state.value.message)
    }

    @Test fun doubleTapOnSubmitSendsOnce() {
        val gate = CompletableDeferred<Unit>()
        val repo = FakeRepo().apply { submitGate = gate }
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        m.onNoteChange("ok")
        m.submit()
        m.submit() // second tap while the first is in flight
        gate.complete(Unit)
        assertEquals(1, repo.submitCalls)
    }

    @Test fun failedSubmit_reportsFailure_andStaysEditable() {
        val repo = FakeRepo().apply { failSubmit = true }
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        m.onNoteChange("ok")
        m.submit()
        assertEquals(UiMessage.SUBMIT_FAILED, m.state.value.message)
        assertTrue(m.state.value.canEdit)
        assertEquals("ok", m.state.value.note) // the rep's text is not lost
    }

    @Test fun timeoutButServerAppliedWrite_isNotReportedAsFailure() {
        val repo = FakeRepo().apply { failSubmit = true; submitAppliedDespiteFailure = true }
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        m.onNoteChange("ok")
        m.submit()
        assertEquals(UiMessage.SUBMITTED_OK, m.state.value.message)
        assertFalse(m.state.value.canEdit)
    }

    @Test fun submittedAssignmentIsReadOnly_noPhotoNoSubmit() {
        val repo = FakeRepo(AssignmentStatus.SUBMITTED)
        val m = newModel(repo)
        m.onPhotoCaptured(tmp())
        m.submit()
        assertEquals(0, repo.photoList.size)
        assertEquals(0, repo.submitCalls)
    }

    @Test fun rejectedAssignmentIsEditableAgain() {
        val m = newModel(FakeRepo(AssignmentStatus.REJECTED))
        assertTrue(m.state.value.canEdit)
    }

    @Test fun capturedTempFileIsAlwaysDeleted() {
        val m = newModel(FakeRepo().apply { failUpload = true })
        val f = tmp()
        m.onPhotoCaptured(f)
        assertFalse(f.exists())
    }

    @Test fun loadFailureIsReported() {
        val repo = object : AssignmentRepository by FakeRepo() {
            override suspend fun get(id: String): Assignment = error("offline")
        }
        val m = DetailModel("a1", repo, okPreparer, scope).also { it.load() }
        assertTrue(m.state.value.loadError)
        assertNull(m.state.value.assignment)
        assertNotNull(m.state.value)
    }
}
