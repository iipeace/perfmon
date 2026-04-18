package com.oss.perfmon.monitor

import android.util.Log
import com.oss.perfmon.channel.TcpChannel
import com.oss.perfmon.model.CpuStats
import com.oss.perfmon.model.DiskStats
import com.oss.perfmon.model.MemStats
import com.oss.perfmon.model.NetStats
import com.oss.perfmon.model.ProcessStats
import com.oss.perfmon.model.ResourceSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

// 에이전트로부터 리소스 사용량을 실시간 스트리밍으로 수신하고 파싱한다
class ResourceMonitor(private val channel: TcpChannel) {

    companion object {
        private const val TAG = "ResourceMonitor"

        // 에이전트에 전달하는 명령어: resmon + 전체 항목(-a) 플래그
        private const val COMMAND = "resmon|-a"
    }

    // JSON 한 줄 = 스냅샷 한 프레임으로 변환해 Flow로 방출한다
    fun stream(): Flow<Result<ResourceSnapshot>> {
        return channel.streamLines(COMMAND).map { result ->
            result.mapCatching { jsonLine ->
                val data = parseSnapshot(jsonLine)
                Log.d(TAG, data.toString())
                data
            }
        }
    }

    // 최상위 JSON 객체를 각 항목별 파서에 위임한다
    private fun parseSnapshot(json: String): ResourceSnapshot {
        val root = JSONObject(json)
        return ResourceSnapshot(
            cpu = toCpuStats(root.getJSONObject("cpu")),
            memory = toMemStats(root.getJSONObject("mem")),
            network = toNetStats(root.getJSONObject("net")),
            storage = toDiskStats(root.getJSONObject("storage")),
            processes = toProcessMap(root.getJSONObject("process"))
        )
    }

    // idle + total로 실제 사용률(%)을 계산한다
    // idle, total 모두 0이면 데이터 미수신 상태이므로 0%로 처리
    private fun toCpuStats(obj: JSONObject): CpuStats {
        val idle = obj.optInt("idle", 0)
        val total = obj.optInt("total", 0)
        val usage = if (idle == 0 && total == 0) 0 else total * 100 / (total + idle)
        return CpuStats(usage)
    }

    private fun toMemStats(obj: JSONObject): MemStats = MemStats(
        anonKb = obj.optInt("anon", 0),
        kernelKb = obj.optInt("kernel", 0),
        availableKb = obj.optInt("available", 0)
    )

    private fun toNetStats(obj: JSONObject): NetStats = NetStats(
        inboundBytes = obj.optInt("inbound", 0),
        outboundBytes = obj.optInt("outbound", 0)
    )

    // storage.total 하위 객체에서 free/usage를 파싱한다
    // total 키가 없으면 0으로 기본값 처리
    private fun toDiskStats(obj: JSONObject): DiskStats {
        val total = obj.optJSONObject("total")
        return DiskStats(
            freeKb = total?.optInt("free", 0) ?: 0,
            usedKb = total?.optInt("usage", 0) ?: 0
        )
    }

    // process 객체의 각 키는 PID(문자열)이다
    // ttime·rss 모두 0인 프로세스는 의미 없는 항목이므로 제외한다
    private fun toProcessMap(obj: JSONObject): Map<Int, ProcessStats> {
        val map = mutableMapOf<Int, ProcessStats>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val pidStr = keys.next()
            val pid = pidStr.toIntOrNull() ?: continue
            val proc = obj.getJSONObject(pidStr)
            val ttime = proc.optInt("ttime", 0)
            val rss = proc.optInt("rss", 0)
            if (ttime != 0 || rss != 0) {
                map[pid] = ProcessStats(
                    name = proc.optString("comm", "unknown"),
                    cpuTime = ttime,
                    rssKb = rss
                )
            }
        }
        return map
    }
}
