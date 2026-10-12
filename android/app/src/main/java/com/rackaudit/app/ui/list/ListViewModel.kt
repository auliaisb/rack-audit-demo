package com.rackaudit.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackaudit.app.data.Assignment
import com.rackaudit.app.data.AssignmentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ListState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val items: List<Assignment> = emptyList(),
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

class ListViewModel(private val repo: AssignmentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ListState())
    val state: StateFlow<ListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeMine()
                .catch { _state.update { s -> s.copy(loading = false, error = true) } }
                .collect { items -> _state.update { it.copy(loading = false, error = false, items = items) } }
        }
    }

    /** Pull-to-refresh: ask the server; the live listener above then delivers whatever changed. */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true, refreshFailed = false) }
        viewModelScope.launch {
            val ok = try {
                repo.refreshMine()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _state.update { it.copy(refreshing = false, refreshFailed = !ok) }
        }
    }
}
