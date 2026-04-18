package com.oss.perfmon.data.repository

import com.oss.perfmon.model.SystemReport
import com.oss.perfmon.probe.SystemProbe
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SysinfoRepository @Inject constructor(
    private val probe: SystemProbe
) {
    suspend fun fetch(): Result<SystemReport> = probe.fetch()
}
