package com.oss.perfmon.ui.screen

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.ui.tooling.preview.Preview
import com.oss.perfmon.model.CpuSample
import com.oss.perfmon.model.ResourceTimeRange
import com.oss.perfmon.ui.components.CpuSection
import com.oss.perfmon.ui.components.TimeRangeChipRow
import com.oss.perfmon.ui.viewmodel.ConnectionState
import com.oss.perfmon.ui.viewmodel.MonitorViewModel
import com.oss.perfmon.ui.viewmodel.UiState

@Composable
fun MonitorScreen(
    navController: NavController,
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    MonitorContent(
        uiState = uiState,
        onStart = viewModel::start,
        onStop = viewModel::stop,
        onSelectTimeRange = viewModel::selectTimeRange,
        onBack = { navController.navigateUp() }
    )
}

@Composable
fun MonitorContent(
    uiState: UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSelectTimeRange: (ResourceTimeRange) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Resource Monitor",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "명령어: resmon|-a  |  HOST: 127.0.0.1:55555",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp))

        TimeRangeChipRow(
            selected = uiState.selectedTimeRange,
            onSelect = onSelectTimeRange,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        CpuSection(
            samples = uiState.chartCpuSamples,
            rangeSeconds = uiState.selectedTimeRange.seconds,
            currentPct = uiState.currentCpuPercent,
            avgPct = uiState.averageCpuPercent,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val (statusText, statusColor) = when (val state = uiState.connectionState) {
            is ConnectionState.Idle ->
                "대기 중" to MaterialTheme.colorScheme.onSurfaceVariant
            is ConnectionState.Loading ->
                "연결 중..." to Color(0xFF1565C0)
            is ConnectionState.Active ->
                "수신 중... ${state.count}회" to Color(0xFF2E7D32)
            is ConnectionState.Error ->
                "오류: ${state.message}" to MaterialTheme.colorScheme.error
        }

        Text(
            text = "상태",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = statusText,
            color = statusColor,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "※ 실제 수신 데이터는 Logcat에서 확인하세요\n   adb logcat -s ResourceMonitor",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = uiState.connectionState is ConnectionState.Idle ||
                        uiState.connectionState is ConnectionState.Error,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }
            Button(
                onClick = onStop,
                enabled = uiState.connectionState is ConnectionState.Active ||
                        uiState.connectionState is ConnectionState.Loading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text("돌아가기")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MonitorScreenPreview() {
    val samples = listOf(
        CpuSample(System.currentTimeMillis() - 50000, 10f),
        CpuSample(System.currentTimeMillis() - 40000, 30f),
        CpuSample(System.currentTimeMillis() - 30000, 20f),
        CpuSample(System.currentTimeMillis() - 20000, 50f),
        CpuSample(System.currentTimeMillis() - 10000, 40f),
        CpuSample(System.currentTimeMillis(), 60f)
    )

    MaterialTheme {
        MonitorContent(
            uiState = UiState(
                connectionState = ConnectionState.Active(123),
                selectedTimeRange = ResourceTimeRange.MIN_1,
                chartCpuSamples = samples,
                currentCpuPercent = 60f,
                averageCpuPercent = 35f,
            ),
            onStart = {},
            onStop = {},
            onSelectTimeRange = {},
            onBack = {}
        )
    }
}
