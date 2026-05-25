package com.oss.perfmon.model

/**
 * CPU 그래프에서 사용자가 선택하는 표시 범위
 *
 * 샘플링 주기와는 별개로, 그래프 x축이 몇 초를 보여줄지만 결정
 */
enum class ResourceTimeRange(val seconds: Int, val displayLabel: String) {
    SEC_10(10, "10초"),
    SEC_30(30, "30초"),
    MIN_1(60, "1분"),
    MIN_3(180, "3분"),
    MIN_5(300, "5분")
}
