package com.oss.perfmon.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oss.perfmon.model.CpuSample
import com.oss.perfmon.model.NetStats
import com.oss.perfmon.model.ResourceTimeRange
import com.oss.perfmon.model.TotalNetworkTraffic
import com.oss.perfmon.model.TrafficUnit
import com.oss.perfmon.monitor.ResourceMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// 리소스 모니터 화면의 상태를 관리하는 ViewModel
// 스트리밍 시작·중지와 UI 상태 갱신을 담당한다
@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val monitor: ResourceMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // 1초마다 기록한 CPU 샘플 원본 버퍼
    private val allCpuSamples = ArrayDeque<CpuSample>()

    // 스트림에서 가장 최근에 받은 CPU 값
    private var latestCpuPercent: Float? = null

    // 스트리밍 코루틴을 보관해 중간에 취소할 수 있도록 한다
    private var streamJob: Job? = null

    // latestCpuPercent를 1초 단위 샘플로 변환하는 코루틴
    private var samplingJob: Job? = null

    private var previousNetworkTimeMs = 0L

    private var totalInbound = 0L

    private var totalOutbound = 0L

    // 스트리밍 시작 — 이미 실행 중이면 중복 실행하지 않는다
    fun start() {
        if (streamJob?.isActive == true) return

        _uiState.value = _uiState.value.copy(connectionState = ConnectionState.Loading)
        var count = 0

        latestCpuPercent = null
        startCpuSampling()

        streamJob = viewModelScope.launch {
            monitor.stream().collect { result ->
                result.fold(
                    onSuccess = { snapshot ->
                        count++
                        latestCpuPercent = snapshot.cpu.usagePercent.toFloat()
                        if (samplingJob?.isActive != true) startCpuSampling()

                        val elapsedMs = calculateElapsedMs()
                        val inboundBps = (snapshot.network.inboundBytes * 1000L / elapsedMs).toInt()
                        val outboundBps =
                            (snapshot.network.outboundBytes * 1000L / elapsedMs).toInt()
                        totalInbound += snapshot.network.inboundBytes
                        totalOutbound += snapshot.network.outboundBytes

                        _uiState.value = _uiState.value.copy(
                            connectionState = ConnectionState.Active(count),
                            networkTraffic = NetStats(inboundBps, outboundBps),
                            totalNetworkTraffic = TotalNetworkTraffic(totalInbound, totalOutbound)
                        )
                    },
                    onFailure = {
                        stopCpuSampling()
                        _uiState.value = _uiState.value.copy(
                            connectionState = ConnectionState.Error(it.message ?: "알 수 없는 에러"),
                        )
                    },
                )
            }
        }
    }

    private fun calculateElapsedMs(): Long {
        val currentNetworkTimeMs = System.currentTimeMillis()

        if (previousNetworkTimeMs == 0L) {
            previousNetworkTimeMs = currentNetworkTimeMs
            return 1000L
        }

        val elapsedMs = currentNetworkTimeMs - previousNetworkTimeMs
        previousNetworkTimeMs = currentNetworkTimeMs

        return elapsedMs
    }

    fun selectNetworkTrafficUnit(unit: TrafficUnit){
        _uiState.value = _uiState.value.copy(selectedTrafficUnit = unit)
    }

    // 스트리밍 중지 — 코루틴을 취소하고 Idle로 복귀
    fun stop() {
        streamJob?.cancel()
        samplingJob?.cancel()
        streamJob = null
        samplingJob = null
        allCpuSamples.clear()
        latestCpuPercent = null
        previousNetworkTimeMs = 0L
        totalInbound = 0L
        totalOutbound = 0L
        _uiState.value = _uiState.value.copy(
            connectionState = ConnectionState.Idle,
            chartCpuSamples = emptyList(),
            currentCpuPercent = 0f,
            averageCpuPercent = 0f,
            networkTraffic = NetStats(0, 0),
            totalNetworkTraffic = TotalNetworkTraffic(0L,0L)
        )
    }

    fun selectTimeRange(range: ResourceTimeRange) {
        _uiState.value = _uiState.value.copy(selectedTimeRange = range)

        refreshCpuUiState(
            currentCpuPercent = _uiState.value.currentCpuPercent,
            nowMs = System.currentTimeMillis(),
        )
    }

    private fun appendCpuSample(sample: CpuSample) {
        allCpuSamples.addLast(sample)

        // 최근 5분 데이터만 유지
        val cutoff5Min = sample.timestampMs - CPU_HISTORY_MS
        while (allCpuSamples.isNotEmpty() && allCpuSamples.first().timestampMs < cutoff5Min) {
            allCpuSamples.removeFirst()
        }
    }

    private fun startCpuSampling() {
        samplingJob?.cancel()
        samplingJob = viewModelScope.launch {
            while (isActive) {
                latestCpuPercent?.let { usagePercent ->
                    appendCpuSample(
                        CpuSample(
                            timestampMs = System.currentTimeMillis(),
                            usagePercent = usagePercent,
                        ),
                    )
                    refreshCpuUiState(
                        currentCpuPercent = usagePercent,
                        nowMs = System.currentTimeMillis(),
                    )
                }
                delay(CPU_SAMPLE_INTERVAL_MS)
            }
        }
    }

    private fun stopCpuSampling() {
        samplingJob?.cancel()
        samplingJob = null
        latestCpuPercent = null
    }

    private fun refreshCpuUiState(
        currentCpuPercent: Float,
        nowMs: Long,
    ) {
        val range = _uiState.value.selectedTimeRange
        val averageCpuPercent = calculateAverageCpuPercent(
            samples = allCpuSamples,
            range = range,
            nowMs = nowMs,
        )

        _uiState.value = _uiState.value.copy(
            chartCpuSamples = buildChartSamples(
                samples = allCpuSamples,
                range = range,
                nowMs = nowMs,
            ),
            currentCpuPercent = currentCpuPercent,
            averageCpuPercent = averageCpuPercent,
        )
    }

    private fun calculateAverageCpuPercent(
        samples: List<CpuSample>,
        range: ResourceTimeRange,
        nowMs: Long,
    ): Float {
        // 선택한 표시 범위 밖의 샘플은 평균 계산에서 제외
        val cutoffMs = nowMs - range.seconds * 1_000L
        return samples
            .asSequence()
            .filter { it.timestampMs >= cutoffMs }
            .map { it.usagePercent }
            .toList()
            .average()
            .toFloat()
            .takeIf { !it.isNaN() } ?: 0f
    }

    private fun buildChartSamples(
        samples: List<CpuSample>,
        range: ResourceTimeRange,
        nowMs: Long,
    ): List<CpuSample> {
        // 선택한 표시 범위 안의 샘플만 차트에 사용
        val cutoffMs = nowMs - range.seconds * 1_000L
        val visibleSamples = samples.filter { it.timestampMs >= cutoffMs }
        if (visibleSamples.size <= MAX_CHART_POINTS) return visibleSamples

        // 차트 점 개수를 MAX_CHART_POINTS 이하로 줄이기 위한 묶음 크기
        val bucketSize = (visibleSamples.size + MAX_CHART_POINTS - 1) / MAX_CHART_POINTS
        return visibleSamples
            .chunked(bucketSize)
            .map { bucket ->
                CpuSample(
                    timestampMs = bucket.last().timestampMs,
                    usagePercent = bucket.map { it.usagePercent }.average().toFloat(),
                )
            }
    }

    // ViewModel 소멸 시 스트리밍이 남아있으면 정리한다
    override fun onCleared() {
        super.onCleared()
        stop()
    }

    private companion object {
        const val CPU_HISTORY_MS = 5 * 60 * 1_000L
        const val CPU_SAMPLE_INTERVAL_MS = 1_000L
        const val MAX_CHART_POINTS = 100
    }
}

sealed class ConnectionState {
    object Idle : ConnectionState()                     // 초기 대기 상태
    object Loading : ConnectionState()                  // 연결 중 (ACK 대기)
    data class Active(val count: Int) : ConnectionState() // 스트리밍 중, 수신 프레임 수
    data class Error(val message: String) : ConnectionState() // 오류 발생
}

/**
 * Monitor 화면에서 렌더링에 필요한 상태
 *
 * @property connectionState 스트리밍 연결 상태
 * @property selectedTimeRange 그래프 표시 범위
 * @property chartCpuSamples 차트에 그릴 CPU 샘플
 * @property currentCpuPercent 카드에 표시할 현재 CPU 사용률
 * @property averageCpuPercent 선택된 표시 범위의 평균 CPU 사용률
 */
data class UiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val selectedTimeRange: ResourceTimeRange = ResourceTimeRange.MIN_1,
    val chartCpuSamples: List<CpuSample> = emptyList(),
    val currentCpuPercent: Float = 0f,
    val averageCpuPercent: Float = 0f,
    val networkTraffic: NetStats = NetStats(0, 0),
    val selectedTrafficUnit: TrafficUnit = TrafficUnit.BYTE,
    val totalNetworkTraffic: TotalNetworkTraffic = TotalNetworkTraffic(0L, 0L)
)
