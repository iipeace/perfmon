package com.oss.perfmon.config

// 서버 연결에 필요한 설정 상수 모음
object ServerConfig {
    // 연결 대상 서버 IP (로컬 에이전트와 통신)
    const val HOST = "127.0.0.1"

    // 에이전트가 리슨하는 포트
    const val PORT = 55555

    // sysinfo 등 one-shot 명령어 응답 대기 최대 시간 (ms)
    // 네트워크 지연을 고려해 3초로 설정
    const val READ_TIMEOUT_MS = 3000
}
