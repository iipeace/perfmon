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

class TcpChannelImpl : TcpChannel {

    companion object {
        private val CONNECTION_ERROR =
            "서버에 연결할 수 없습니다 (${ServerConfig.HOST}:${ServerConfig.PORT})"
        private const val NO_DATA_RECEIVED = "수신된 데이터가 없습니다"
        private const val UNKNOWN_ERROR = "알 수 없는 오류"
        private const val STREAM_ERROR = "스트림을 읽을 수 없습니다"
    }

    // 소켓·스트림은 명령어 전송마다 새로 생성하고 finally에서 반드시 해제한다
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null

    override fun streamLines(command: String): Flow<Result<String>> = flow {
        try {
            if (open() && handshake(command)) {
                val activeReader = reader ?: run {
                    emit(Result.failure(Exception(STREAM_ERROR)))
                    return@flow
                }
                activeReader.lineSequence().forEach { line -> emit(Result.success(line)) }
            } else {
                emit(Result.failure(Exception(CONNECTION_ERROR)))
            }
        } catch (e: Exception) {
            val message = if (e is ConnectException) CONNECTION_ERROR else e.message ?: UNKNOWN_ERROR
            emit(Result.failure(Exception(message)))
        } finally {
            close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun readAllLines(command: String): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                if (open() && handshake(command)) {
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

    private fun open(): Boolean {
        if (socket?.isConnected == true && socket?.isClosed == false) return true
        socket = Socket(ServerConfig.HOST, ServerConfig.PORT)
        socket?.let {
            reader = BufferedReader(InputStreamReader(it.getInputStream()))
            writer = PrintWriter(it.getOutputStream())
        }
        return socket?.isConnected ?: false
    }

    private fun write(message: String) {
        writer?.write(message)
        writer?.flush()
    }

    // ACK 핸드쉐이크: 서버가 정확히 "ACK" 3바이트를 응답해야 이후 수신이 유효하다
    private fun handshake(message: String): Boolean {
        write(message)
        val buffer = CharArray(3)
        val bytesRead = reader?.read(buffer)
        return (bytesRead == 3 && String(buffer) == "ACK")
    }

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
            socket = null
            reader = null
            writer = null
        }
    }
}
