package com.oss.perfmon.model

/**
 * CPU 그래프에 표시할 1초 단위 샘플
 * @property timestampMs x축 시간 위치
 * @property usagePercent y축 CPU 사용률(0~100%)
 */
data class CpuSample(
    val timestampMs: Long,
    val usagePercent: Float,
)
