package com.oss.perfmon.monitor

import com.oss.perfmon.channel.FakeTcpChannel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceMonitorTest {

    // README.md 8절 기준 정상 응답 JSON
    private val normalJson = """
        {
            "cpu": {"total": 80, "idle": 20},
            "mem": {"anon": 512000, "kernel": 102400, "available": 2048000},
            "net": {"inbound": 1024, "outbound": 512},
            "storage": {"total": {"free": 10240, "usage": 5120}},
            "process": {
                "1234": {"comm": "system_server", "ttime": 500, "rss": 102400},
                "5678": {"comm": "com.example.app", "ttime": 120, "rss": 51200}
            }
        }
    """.trimIndent()

    private fun monitor(vararg lines: String) =
        ResourceMonitor(FakeTcpChannel(lines = lines.toList()))

    private suspend fun snapshot(vararg lines: String) =
        monitor(*lines).stream().first().getOrThrow()

    //CPU 파싱 테스트

    @Test
    fun `CPU total=80 idle=20이면 사용률이 80 퍼센트이다`() = runTest {
        val snap = snapshot(normalJson)
        assertEquals(80, snap.cpu.usagePercent)
    }

    @Test
    fun `CPU total=0 idle=0이면 사용률이 0 퍼센트이다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertEquals(0, snap.cpu.usagePercent)
    }

    @Test
    fun `CPU total=1 idle=99이면 정수나눗셈으로 1 퍼센트이다`() = runTest {
        val json = """
            {
                "cpu": {"total": 1, "idle": 99},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertEquals(1, snap.cpu.usagePercent)
    }

    //메모리 파싱 테스트

    @Test
    fun `메모리 anon kernel available을 정상 파싱한다`() = runTest {
        val snap = snapshot(normalJson)
        assertEquals(512000, snap.memory.anonKb)
        assertEquals(102400, snap.memory.kernelKb)
        assertEquals(2048000, snap.memory.availableKb)
    }

    @Test
    fun `메모리 필드 누락 시 0으로 기본값 처리한다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertEquals(0, snap.memory.anonKb)
        assertEquals(0, snap.memory.kernelKb)
        assertEquals(0, snap.memory.availableKb)
    }

    //네트워크 파싱 테스트
    @Test
    fun `네트워크 inbound outbound를 정상 파싱한다`() = runTest {
        val snap = snapshot(normalJson)
        assertEquals(1024, snap.network.inboundBytes)
        assertEquals(512, snap.network.outboundBytes)
    }

    //스토리지 파싱 테스트
    @Test
    fun `스토리지 free usage를 정상 파싱한다`() = runTest {
        val snap = snapshot(normalJson)
        assertEquals(10240, snap.storage.freeKb)
        assertEquals(5120, snap.storage.usedKb)
    }

    @Test
    fun `storage total 키 누락 시 freeKb usedKb가 모두 0이다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {},
                "process": {}
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertEquals(0, snap.storage.freeKb)
        assertEquals(0, snap.storage.usedKb)
    }

    //프로세스 파싱 테스트
    @Test
    fun `프로세스 ttime과 rss 모두 양수이면 포함된다`() = runTest {
        val snap = snapshot(normalJson)
        assertTrue(snap.processes.containsKey(1234))
        assertEquals("system_server", snap.processes[1234]?.name)
        assertEquals(500, snap.processes[1234]?.cpuTime)
        assertEquals(102400, snap.processes[1234]?.rssKb)
    }

    @Test
    fun `프로세스 ttime=0 rss=0이면 제외된다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {
                    "999": {"comm": "zombie", "ttime": 0, "rss": 0}
                }
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertTrue(snap.processes.isEmpty())
    }

    @Test
    fun `프로세스 ttime=0이어도 rss 양수이면 포함된다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {
                    "111": {"comm": "memonly", "ttime": 0, "rss": 4096}
                }
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertTrue(snap.processes.containsKey(111))
    }

    @Test
    fun `프로세스 rss=0이어도 ttime 양수이면 포함된다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {
                    "222": {"comm": "cpuonly", "ttime": 10, "rss": 0}
                }
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertTrue(snap.processes.containsKey(222))
    }

    @Test
    fun `프로세스 PID가 숫자가 아니면 스킵된다`() = runTest {
        val json = """
            {
                "cpu": {"total": 0, "idle": 0},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {
                    "not_a_pid": {"comm": "ghost", "ttime": 100, "rss": 1024}
                }
            }
        """.trimIndent()
        val snap = snapshot(json)
        assertTrue(snap.processes.isEmpty())
    }

    //스트림 동작 테스트
    @Test
    fun `JSON 여러 줄이면 스냅샷 여러 개를 방출한다`() = runTest {
        val results = mutableListOf<Int>()
        val json1 = """
            {
                "cpu": {"total": 10, "idle": 90},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        val json2 = """
            {
                "cpu": {"total": 50, "idle": 50},
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        monitor(json1, json2).stream().collect { result ->
            result.onSuccess { results.add(it.cpu.usagePercent) }
        }
        assertEquals(listOf(10, 50), results)
    }

    @Test
    fun `잘못된 JSON이면 Result failure로 전파된다`() = runTest {
        val result = monitor("not valid json").stream().first()
        assertTrue(result.isFailure)
    }

    @Test
    fun `필수 키 cpu 누락이면 Result failure로 전파된다`() = runTest {
        val json = """
            {
                "mem": {"anon": 0, "kernel": 0, "available": 0},
                "net": {"inbound": 0, "outbound": 0},
                "storage": {"total": {"free": 0, "usage": 0}},
                "process": {}
            }
        """.trimIndent()
        val result = monitor(json).stream().first()
        assertTrue(result.isFailure)
    }
}
