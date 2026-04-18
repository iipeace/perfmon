package com.oss.perfmon.model

// resmon 명령어 한 프레임의 전체 리소스 스냅샷
data class ResourceSnapshot(
    val cpu: CpuStats,
    val memory: MemStats,
    val network: NetStats,
    val storage: DiskStats,
    val processes: Map<Int, ProcessStats> // key: PID
)

// CPU 사용률 (0~100%)
data class CpuStats(val usagePercent: Int)

// 메모리 사용 현황 (단위: KB)
data class MemStats(
    val anonKb: Int,       // 익명 매핑 메모리 (앱이 사용하는 힙 등)
    val kernelKb: Int,     // 커널이 사용하는 메모리
    val availableKb: Int   // 현재 할당 가능한 여유 메모리
)

// 네트워크 트래픽 (단위: Bytes)
data class NetStats(
    val inboundBytes: Int,  // 수신량
    val outboundBytes: Int  // 송신량
)

// 스토리지 사용 현황 (단위: KB)
data class DiskStats(
    val freeKb: Int,  // 남은 용량
    val usedKb: Int   // 사용 중인 용량
)

// 프로세스별 리소스 사용량
data class ProcessStats(
    val name: String,   // 프로세스 이름 (comm)
    val cpuTime: Int,   // 누적 CPU 시간 (ttime)
    val rssKb: Int      // 실제 물리 메모리 사용량 (Resident Set Size, KB)
)
