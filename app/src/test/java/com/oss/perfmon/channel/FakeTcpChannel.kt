package com.oss.perfmon.channel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeTcpChannel(
    private val lines: List<String> = emptyList(),
    private val error: Throwable? = null
) : TcpChannel {

    override fun streamLines(command: String): Flow<Result<String>> = flow {
        if (error != null) {
            emit(Result.failure(error))
        } else {
            lines.forEach { emit(Result.success(it)) }
        }
    }

    override suspend fun readAllLines(command: String): Result<List<String>> =
        if (error != null) Result.failure(error)
        else Result.success(lines)
}
