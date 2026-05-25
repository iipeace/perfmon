package com.oss.perfmon.channel

import kotlinx.coroutines.flow.Flow

// 에이전트와 TCP 소켓으로 통신하는 채널 인터페이스
// 실제 구현은 TcpChannelImpl, 테스트용 Fake는 이 인터페이스를 구현한다
interface TcpChannel {

    // 스트리밍 명령어용: 서버가 연결을 끊을 때까지 한 줄씩 Flow로 emit한다
    // resmon|-a 처럼 지속적으로 데이터를 보내는 명령어에 사용
    fun streamLines(command: String): Flow<Result<String>>

    // 일회성 명령어용: 응답 전체를 리스트로 받아 반환한다
    // sysinfo 처럼 한 번만 응답하는 명령어에 사용
    suspend fun readAllLines(command: String): Result<List<String>>
}
