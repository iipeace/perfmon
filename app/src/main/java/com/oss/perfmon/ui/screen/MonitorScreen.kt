package com.oss.perfmon.ui.screen

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
import com.oss.perfmon.ui.viewmodel.MonitorViewModel

@Composable
fun MonitorScreen(
    navController: NavController,
    viewModel: MonitorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
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

        val (statusText, statusColor) = when (val state = uiState) {
            is MonitorViewModel.UiState.Idle ->
                "대기 중" to MaterialTheme.colorScheme.onSurfaceVariant
            is MonitorViewModel.UiState.Loading ->
                "연결 중..." to Color(0xFF1565C0)
            is MonitorViewModel.UiState.Active ->
                "수신 중... ${state.count}회" to Color(0xFF2E7D32)
            is MonitorViewModel.UiState.Error ->
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
                onClick = { viewModel.start() },
                enabled = uiState is MonitorViewModel.UiState.Idle ||
                        uiState is MonitorViewModel.UiState.Error,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }
            Button(
                onClick = { viewModel.stop() },
                enabled = uiState is MonitorViewModel.UiState.Active ||
                        uiState is MonitorViewModel.UiState.Loading,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }
        }

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier
                .padding(top = 16.dp)
                .align(Alignment.CenterHorizontally)
        ) {
            Text("돌아가기")
        }
    }
}
