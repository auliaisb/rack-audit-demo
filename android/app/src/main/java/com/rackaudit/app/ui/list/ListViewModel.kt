package com.rackaudit.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rackaudit.app.data.Assignment
import com.rackaudit.app.data.AssignmentRepository
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
)

class ListViewModel(private val repo: AssignmentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ListState())
    val state: StateFlow<ListState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.observeMine()
                .catch { _state.update { s -> s.copy(loading = false, error = true) } }
                .collect { items -> _state.update { ListState(loading = false, items = items) } }
        }
    }
}
