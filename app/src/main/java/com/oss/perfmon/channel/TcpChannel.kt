package com.oss.perfmon.channel

import com.oss.perfmon.config.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ConnectException
import java.net.Socket

// 에이전트와 TCP 소켓으로 통신하는 저수준 채널
// 스트리밍(Flow)과 일회성(suspend) 두 가지 수신 방식을 제공한다
class TcpChannel {

    companion object {
        // 연결 실패 시 사용자에게 표시할 메시지
        private val CONNECTION_ERROR =
            "서버에 연결할 수 없습니다 (${ServerConfig.HOST}:${ServerConfig.PORT})"

        // ACK 수신 후 데이터가 한 줄도 없을 때
        private const val NO_DATA_RECEIVED = "수신된 데이터가 없습니다"

        // 예외 메시지가 없는 예외 발생 시 기본 메시지
        private const val UNKNOWN_ERROR = "알 수 없는 오류"

        // open() 성공 후 reader가 null인 비정상 상황에서 사용
        private const val STREAM_ERROR = "스트림을 읽을 수 없습니다"
    }

    // 소켓·스트림은 명령어 전송마다 새로 생성하고 finally에서 반드시 해제한다
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    // 스트리밍 명령어용: 서버가 연결을 끊을 때까지 한 줄씩 Flow로 emit한다
    // resmon|-a 처럼 지속적으로 데이터를 보내는 명령어에 사용
    fun streamLines(command: String): Flow<Result<String>> = flow {
        try {
            if (open() && handshake(command)) {
                // open() 직후에는 reader가 반드시 초기화되어야 한다
                // null이면 비정상 상태이므로 에러를 emit하고 종료
                val activeReader = reader ?: run {
                    emit(Result.failure(Exception(STREAM_ERROR)))
                    return@flow
                }
                // 서버가 소켓을 닫으면 lineSequence()가 자연스럽게 종료된다
                activeReader.lineSequence().forEach { line -> emit(Result.success(line)) }
            } else {
                emit(Result.failure(Exception(CONNECTION_ERROR)))
            }
        } catch (e: Exception) {
            // ConnectException은 서버 미기동 상황 — 별도 메시지로 구분
            val message = if (e is ConnectException) CONNECTION_ERROR else e.message ?: UNKNOWN_ERROR
            emit(Result.failure(Exception(message)))
        } finally {
            // 성공·실패 모두 소켓 자원을 반드시 해제
            close()
        }
    }.flowOn(Dispatchers.IO) // 소켓 I/O는 IO 디스패처에서 실행

    // 일회성 명령어용: 응답 전체를 리스트로 받아 반환한다
    // sysinfo 처럼 한 번만 응답하는 명령어에 사용
    suspend fun readAllLines(command: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                if (open() && handshake(command)) {
                    // 서버가 데이터 전송을 마치면 더 이상 보내지 않으므로
                    // 타임아웃을 걸어 스트림 종료를 감지한다
                    socket?.soTimeout = ServerConfig.READ_TIMEOUT_MS

                    val lines = reader?.lineSequence()?.toList().orEmpty()
                    if (lines.isNotEmpty()) {
                        Result.success(lines)
                    } else {
                        Result.failure(Exception(NO_DATA_RECEIVED))
                    }
                } else {
                    Result.failure(Exception(CONNECTION_ERROR))
                }
            } catch (e: Exception) {
                val message = if (e is ConnectException) CONNECTION_ERROR else e.message ?: UNKNOWN_ERROR
                Result.failure(Exception(message))
            } finally {
                close()
            }
        }

    // 소켓 연결 및 스트림 초기화
    // 이미 연결된 소켓이 살아있으면 재사용한다
    private fun open(): Boolean {
        if (socket?.isConnected == true && socket?.isClosed == false) return true

        socket = Socket(ServerConfig.HOST, ServerConfig.PORT)
        socket?.let {
            reader = BufferedReader(InputStreamReader(it.getInputStream()))
            writer = PrintWriter(it.getOutputStream())
        }
        return socket?.isConnected ?: false
    }

    // 서버로 문자열 전송
    private fun write(message: String) {
        writer?.write(message)
        writer?.flush()
    }

    // 명령어를 전송하고 서버의 ACK(3바이트 "ACK") 응답을 확인한다
    // ACK를 받아야 이후 데이터 수신이 유효하다
    private fun handshake(message: String): Boolean {
        write(message)
        val buffer = CharArray(3)
        val bytesRead = reader?.read(buffer)
        return (bytesRead == 3 && String(buffer) == "ACK")
    }

    // 소켓과 스트림을 안전하게 닫는다
    // 입출력 셧다운 후 소켓을 닫아 반쪽 닫힘(half-close) 상태를 방지한다
    private fun close() {
        try {
            socket?.let {
                if (!it.isInputShutdown) it.shutdownInput()
                if (!it.isOutputShutdown) it.shutdownOutput()
                it.close()
            }
            reader?.close()
            writer?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            // 참조를 null로 초기화해 다음 open() 호출이 새 소켓을 생성하도록 한다
            socket = null
            reader = null
            writer = null
        }
    }
}
