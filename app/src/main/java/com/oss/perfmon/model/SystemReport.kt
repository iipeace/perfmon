package com.oss.perfmon.model

// sysinfo 명령어 응답 전체를 줄 단위로 보관하는 모델
// 파싱 없이 원문 텍스트를 그대로 저장해 UI에서 표시한다
data class SystemReport(val lines: List<String>)
