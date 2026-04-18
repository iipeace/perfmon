package com.oss.perfmon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oss.perfmon.monitor.ResourceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// 리소스 모니터 화면의 상태를 관리하는 ViewModel
// 스트리밍 시작·중지와 UI 상태 갱신을 담당한다
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val monitor: ResourceMonitor
) : ViewModel() {

    // 화면이 가질 수 있는 상태 정의
    sealed class UiState {
        object Idle : UiState()                     // 초기 대기 상태
        object Loading : UiState()                  // 연결 중 (ACK 대기)
        data class Active(val count: Int) : UiState() // 스트리밍 중, 수신 프레임 수
        data class Error(val message: String) : UiState() // 오류 발생
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    // 스트리밍 코루틴을 보관해 중간에 취소할 수 있도록 한다
    private var streamJob: Job? = null

    // 스트리밍 시작 — 이미 실행 중이면 중복 실행하지 않는다
    fun start() {
        if (streamJob?.isActive == true) return

        _uiState.value = UiState.Loading
        var count = 0

        streamJob = viewModelScope.launch {
            monitor.stream().collect { result ->
                result.fold(
                    onSuccess = {
                        // 프레임 수신 성공 시 카운트를 증가시켜 UI에 반영
                        count++
                        _uiState.value = UiState.Active(count)
                    },
                    onFailure = {
                        // 연결 실패 또는 파싱 오류 시 에러 상태로 전환
                        _uiState.value = UiState.Error(it.message ?: "Unknown error")
                    }
                )
            }
        }
    }

    // 스트리밍 중지 — 코루틴을 취소하고 Idle로 복귀
    fun stop() {
        streamJob?.cancel()
        streamJob = null
        _uiState.value = UiState.Idle
    }

    // ViewModel 소멸 시 스트리밍이 남아있으면 정리한다
    override fun onCleared() {
        super.onCleared()
        stop()
    }
}
