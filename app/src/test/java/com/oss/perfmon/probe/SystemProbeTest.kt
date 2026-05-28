package com.oss.perfmon.probe

import com.oss.perfmon.channel.FakeTcpChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemProbeTest {

    // guider sysinfo는 plain text 줄 목록을 반환한다
    private val sampleLines = listOf(
        "OS: Android 14 (API 34)",
        "Kernel: 5.15.104-android13-8-00001",
        "Device: Pixel 7",
        "Uptime: 3h 22m 14s",
        "MemTotal: 7,812 MB",
    )

    @Test
    fun `정상 응답이면 SystemReport에 수신한 줄 목록이 담긴다`() = runTest {
        val probe = SystemProbe(FakeTcpChannel(lines = sampleLines))
        val report = probe.fetch().getOrThrow()
        assertEquals(sampleLines, report.lines)
    }

    @Test
    fun `수신 줄 수가 올바르게 기록된다`() = runTest {
        val probe = SystemProbe(FakeTcpChannel(lines = sampleLines))
        val report = probe.fetch().getOrThrow()
        assertEquals(5, report.lines.size)
    }

    @Test
    fun `채널 에러는 Result failure로 전파된다`() = runTest {
        val error = Exception("서버 연결 실패")
        val probe = SystemProbe(FakeTcpChannel(error = error))
        val result = probe.fetch()
        assertTrue(result.isFailure)
        assertEquals("서버 연결 실패", result.exceptionOrNull()?.message)
    }

    @Test
    fun `줄 내용을 파싱하거나 변환하지 않고 그대로 보존한다`() = runTest {
        val rawLines = listOf("  raw line with spaces  ", "line:with:colons", "한글 라인")
        val probe = SystemProbe(FakeTcpChannel(lines = rawLines))
        val report = probe.fetch().getOrThrow()
        assertEquals(rawLines, report.lines)
    }
}
