package com.oss.perfmon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oss.perfmon.probe.SystemProbe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// 시스템 정보 화면의 상태를 관리하는 ViewModel
// 일회성 조회이므로 Job 관리 없이 단순 launch로 처리한다
@HiltViewModel
class SystemViewModel @Inject constructor(
    private val probe: SystemProbe
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()                      // 초기 대기 상태
        object Loading : UiState()                   // 조회 중
        data class Done(val lineCount: Int) : UiState() // 수신 완료, 수신 줄 수
        data class Error(val message: String) : UiState() // 오류 발생
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    // 시스템 정보를 한 번 조회하고 결과를 상태로 반영한다
    fun fetch() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            probe.fetch().fold(
                onSuccess = { report ->
                    _uiState.value = UiState.Done(report.lines.size)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
}
