package com.oss.perfmon.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oss.perfmon.model.CpuSample
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CpuSection(
    samples: List<CpuSample>,
    rangeSeconds: Int,
    currentPct: Float,
    avgPct: Float,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "CPU",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    MetricItem(
                        label = "현재",
                        value = "${currentPct.roundToInt()}%",
                        modifier = Modifier.weight(1f),
                    )
                    MetricItem(
                        label = "평균",
                        value = "${avgPct.roundToInt()}%",
                        modifier = Modifier.weight(1f),
                    )
                }
                CpuLineChart(
                    samples = samples,
                    rangeSeconds = rangeSeconds,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun CpuLineChart(
    samples: List<CpuSample>,
    rangeSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val lineColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val nowMs = System.currentTimeMillis()
        val rangeMs = rangeSeconds * 1_000L
        val startMs = nowMs - rangeMs

        // 가로 가이드 라인 25/50/75/100%
        repeat(4) { index ->
            val ratio = (index + 1) / 4f
            val y = canvasHeight * (1f - ratio)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (samples.isEmpty()) return@Canvas

        val path = Path()
        samples.forEachIndexed { index, sample ->
            // timestamp는 x축 위치, usagePercent는 y축 위치로 변환
            val x = ((sample.timestampMs - startMs).toFloat() / rangeMs)
                .coerceIn(0f, 1f) * canvasWidth
            val y = (1f - sample.usagePercent.coerceIn(0f, 100f) / 100f) * canvasHeight

            if (index == 0) path.moveTo(x, y)
            else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CpuSectionPreview() {
    val now = System.currentTimeMillis()
    val samples = List(30) { index ->
        val usage = 45f + (sin(index / 10f * PI * 2).toFloat() * 20f)
        CpuSample(
            timestampMs = now - (19 - index) * 10_000L,
            usagePercent = usage,
        )
    }

    CpuSection(
        samples = samples,
        rangeSeconds = 60,
        currentPct = samples.last().usagePercent,
        avgPct = samples.map { it.usagePercent }.average().toFloat(),
    )
}
