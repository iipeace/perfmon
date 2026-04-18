package com.oss.perfmon.probe

import android.util.Log
import com.oss.perfmon.channel.TcpChannel
import com.oss.perfmon.model.SystemReport

// 에이전트로부터 시스템 정보를 일회성으로 조회한다
class SystemProbe(private val channel: TcpChannel) {

    companion object {
        private const val TAG = "SystemProbe"

        // 에이전트에 전달하는 명령어
        private const val COMMAND = "sysinfo"
    }

    // 응답 전체를 줄 단위 리스트로 받아 SystemReport로 래핑한다
    // suspend 함수이므로 호출 측에서 코루틴 스코프가 필요하다
    suspend fun fetch(): Result<SystemReport> {
        return channel.readAllLines(COMMAND).mapCatching { lines ->
            Log.d(TAG, "Received ${lines.size} lines")
            lines.forEach { Log.d(TAG, it) }
            SystemReport(lines)
        }
    }
}
