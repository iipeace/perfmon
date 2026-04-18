package com.oss.perfmon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oss.perfmon.data.repository.ResmonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResmonViewModel @Inject constructor(
    private val repository: ResmonRepository
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        object Loading : UiState()
        data class Active(val count: Int) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    private var streamJob: Job? = null

    fun start() {
        if (streamJob?.isActive == true) return

        _uiState.value = UiState.Loading
        var count = 0

        streamJob = viewModelScope.launch {
            repository.stream().collect { result ->
                result.fold(
                    onSuccess = {
                        count++
                        _uiState.value = UiState.Active(count)
                    },
                    onFailure = {
                        _uiState.value = UiState.Error(it.message ?: "Unknown error")
                    }
                )
            }
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
