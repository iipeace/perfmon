package com.oss.perfmon.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.oss.perfmon.ui.viewmodel.SystemViewModel

@Composable
fun SystemScreen(
    navController: NavController,
    viewModel: SystemViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "System Info",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "명령어: sysinfo  |  HOST: 127.0.0.1:55555",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(bottom = 24.dp))

        val (statusText, statusColor) = when (val state = uiState) {
            is SystemViewModel.UiState.Idle ->
                "대기 중" to MaterialTheme.colorScheme.onSurfaceVariant
            is SystemViewModel.UiState.Loading ->
                "조회 중..." to Color(0xFF1565C0)
            is SystemViewModel.UiState.Done ->
                "수신 완료: ${state.lineCount}줄" to Color(0xFF2E7D32)
            is SystemViewModel.UiState.Error ->
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
            text = "※ 실제 수신 데이터는 Logcat에서 확인하세요\n   adb logcat -s SystemProbe",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Button(
            onClick = { viewModel.fetch() },
            enabled = uiState !is SystemViewModel.UiState.Loading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Text("Fetch")
        }

        OutlinedButton(
            onClick = { navController.popBackStack() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("돌아가기")
        }
    }
}
