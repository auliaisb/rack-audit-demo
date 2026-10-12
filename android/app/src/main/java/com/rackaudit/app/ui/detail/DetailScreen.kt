package com.rackaudit.app.ui.detail

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackaudit.app.R
import com.rackaudit.app.data.Photo
import com.rackaudit.app.domain.AssignmentStatus
import com.rackaudit.app.domain.Limits
import com.rackaudit.app.domain.SubmissionError
import com.rackaudit.app.ui.list.formatDue
import com.rackaudit.app.ui.list.statusText
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(model: DetailModel, onBack: () -> Unit) {
    val s by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The camera app can kill our process while it is in front; remember where the photo should land.
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath
        pendingPath = null
        if (path != null) {
            val file = File(path)
            if (ok) model.onPhotoCaptured(file) else file.delete()
        }
    }

    fun launchCamera() {
        try {
            val dir = File(context.cacheDir, "captures").apply { mkdirs() }
            val file = File.createTempFile("cap_", ".jpg", dir)
            pendingPath = file.path
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            takePicture.launch(uri)
        } catch (e: Exception) {
            pendingPath = null
            model.onCameraFailed() // e.g. cache full; show a message instead of crashing
        }
    }

    // The ViewModel outlives this screen (it is cached per assignment), so every time the screen is shown again
    // its data may be old: the supervisor could have decided in the meantime. A brand-new model is still loading
    // here, and refresh() ignores that case.
    LaunchedEffect(Unit) { model.refresh() }

    PullToRefreshBox(isRefreshing = s.refreshing, onRefresh = model::refresh, modifier = Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.detail_back)) }

        when {
            s.loading -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.loadError || s.assignment == null -> {
                Text(stringResource(R.string.list_error), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = model::load) { Text("↻") }
            }
            else -> {
                val a = s.assignment!!
                Text(a.title, style = MaterialTheme.typography.headlineSmall)
                Text("${a.storeName} · ${a.storeAddress}", style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.list_due, formatDue(a.dueDateMillis)) + " · " + statusText(a.status))
                if (a.instructions.isNotBlank()) {
                    Text(stringResource(R.string.detail_instructions), style = MaterialTheme.typography.titleSmall)
                    Text(a.instructions)
                }
                if (a.status == AssignmentStatus.REJECTED && a.reviewNote.isNotBlank()) {
                    Text(stringResource(R.string.detail_rejected_reason, a.reviewNote), color = MaterialTheme.colorScheme.error)
                }

                Text(stringResource(R.string.detail_photos, s.photos.size, Limits.MAX_PHOTOS), style = MaterialTheme.typography.titleSmall)
                s.photos.forEach { PhotoRow(it, canRemove = s.canEdit && s.busy == Busy.NONE) { model.onRemovePhoto(it) } }

                if (s.canEdit) {
                    OutlinedTextField(
                        value = s.caption,
                        onValueChange = model::onCaptionChange,
                        label = { Text(stringResource(R.string.detail_caption)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = ::launchCamera,
                        enabled = s.busy == Busy.NONE && s.photos.size < Limits.MAX_PHOTOS,
                    ) { Text(stringResource(R.string.detail_take_photo)) }

                    OutlinedTextField(
                        value = s.note,
                        onValueChange = model::onNoteChange,
                        label = { Text(stringResource(R.string.detail_note)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    s.errors.forEach { Text(errorText(it), color = MaterialTheme.colorScheme.error) }
                    Button(onClick = model::submit, enabled = s.busy == Busy.NONE, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.detail_submit))
                    }
                } else {
                    if (a.note.isNotBlank()) Text(a.note)
                    Text(stringResource(R.string.detail_readonly), style = MaterialTheme.typography.bodySmall)
                }

                if (s.busy != Busy.NONE) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Text(stringResource(R.string.detail_uploading))
                    }
                }
                s.message?.let { Text(messageText(it), color = if (it == UiMessage.SUBMITTED_OK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        }
    }
    }
}

/** 1280 px source / 4 = about 320 px: plenty for an 80 dp thumbnail and cheap on memory. */
private fun decodeThumbnail(jpeg: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()

@Composable
private fun PhotoRow(photo: Photo, canRemove: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        val thumb = remember(photo.id) { photo.jpeg?.let(::decodeThumbnail) }
        if (thumb != null) {
            Image(bitmap = thumb, contentDescription = photo.caption.ifBlank { null }, contentScale = ContentScale.Crop, modifier = Modifier.size(80.dp))
        } else {
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) { Text("—") }
        }
        Text(photo.caption.ifBlank { "—" }, modifier = Modifier.weight(1f))
        if (canRemove) TextButton(onClick = onRemove) { Text(stringResource(R.string.detail_remove)) }
    }
}

@Composable
private fun errorText(e: SubmissionError): String = when (e) {
    SubmissionError.NOTE_REQUIRED -> stringResource(R.string.err_note_required)
    SubmissionError.NOTE_TOO_LONG -> stringResource(R.string.err_note_too_long)
    SubmissionError.TOO_FEW_PHOTOS -> stringResource(R.string.err_photos_min, Limits.MIN_PHOTOS)
    SubmissionError.TOO_MANY_PHOTOS -> stringResource(R.string.err_photos_max, Limits.MAX_PHOTOS)
}

@Composable
private fun messageText(m: UiMessage): String = when (m) {
    UiMessage.REFRESH_FAILED -> stringResource(R.string.err_refresh)
    UiMessage.UPLOAD_FAILED -> stringResource(R.string.err_upload)
    UiMessage.SUBMIT_FAILED -> stringResource(R.string.err_submit)
    UiMessage.PHOTO_PROCESS_FAILED -> stringResource(R.string.err_photo_process)
    UiMessage.TOO_MANY_PHOTOS -> stringResource(R.string.err_photos_max, Limits.MAX_PHOTOS)
    UiMessage.CAMERA_FAILED -> stringResource(R.string.err_camera)
    UiMessage.SUBMITTED_OK -> stringResource(R.string.detail_submitted_ok)
}
