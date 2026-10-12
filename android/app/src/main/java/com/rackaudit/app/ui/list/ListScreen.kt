package com.rackaudit.app.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rackaudit.app.R
import com.rackaudit.app.data.Assignment
import com.rackaudit.app.domain.AssignmentStatus
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun statusText(status: AssignmentStatus?): String = when (status) {
    AssignmentStatus.ASSIGNED -> stringResource(R.string.status_assigned)
    AssignmentStatus.SUBMITTED -> stringResource(R.string.status_submitted)
    AssignmentStatus.APPROVED -> stringResource(R.string.status_approved)
    AssignmentStatus.REJECTED -> stringResource(R.string.status_rejected)
    AssignmentStatus.CANCELLED -> stringResource(R.string.status_cancelled)
    null -> "?"
}

fun formatDue(millis: Long?): String =
    millis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale("id", "ID")).format(Date(it)) } ?: "—"

@Composable
fun ListScreen(vm: ListViewModel, onOpen: (String) -> Unit, onLogout: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.list_title), style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onLogout) { Text(stringResource(R.string.list_logout)) }
        }
        when {
            s.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            s.error -> Text(stringResource(R.string.list_error), color = MaterialTheme.colorScheme.error)
            s.items.isEmpty() -> Text(stringResource(R.string.list_empty))
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.items, key = { it.id }) { AssignmentRow(it) { onOpen(it.id) } }
            }
        }
    }
}

@Composable
private fun AssignmentRow(a: Assignment, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(a.title, style = MaterialTheme.typography.titleMedium)
            Text(a.storeName, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.list_due, formatDue(a.dueDateMillis)), style = MaterialTheme.typography.bodySmall)
                Text(statusText(a.status), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
