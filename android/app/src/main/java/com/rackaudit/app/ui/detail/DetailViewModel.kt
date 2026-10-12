package com.rackaudit.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackaudit.app.data.AssignmentRepository
import com.rackaudit.app.data.ImageProcessor
import java.io.File

/** Thin holder: supplies viewModelScope and the Android-backed photo preparer to [DetailModel]. */
class DetailViewModel(assignmentId: String, repo: AssignmentRepository, cacheDir: File) : ViewModel() {
    val model = DetailModel(
        assignmentId = assignmentId,
        repo = repo,
        preparer = PhotoPreparer { captured ->
            val out = File(cacheDir, "up_${System.nanoTime()}.jpg")
            if (ImageProcessor.compress(captured, out)) out else { out.delete(); null }
        },
        scope = viewModelScope,
    ).also { it.load() }
}
