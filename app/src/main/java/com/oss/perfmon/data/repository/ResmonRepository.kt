package com.oss.perfmon.data.repository

import com.oss.perfmon.model.ResourceSnapshot
import com.oss.perfmon.monitor.ResourceMonitor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResmonRepository @Inject constructor(
    private val monitor: ResourceMonitor
) {
    fun stream(): Flow<Result<ResourceSnapshot>> = monitor.stream()
}
