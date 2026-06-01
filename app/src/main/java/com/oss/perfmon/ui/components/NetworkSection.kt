package com.oss.perfmon.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oss.perfmon.model.TrafficUnit
import java.text.NumberFormat

@Composable
fun NetworkSection(
    inboundBps: Int,
    outboundBps: Int,
    trafficUnit: TrafficUnit,
    onTrafficUnitSelected: (TrafficUnit) -> Unit
) {

    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TrafficUnit.entries.forEach { unit ->
            FilterChip(
                selected = trafficUnit == unit,
                onClick = { onTrafficUnitSelected(unit) },
                label = { Text(unit.name) }
            )
        }
    }
    Column {
        Text(
            "Inbound : ${
                formatTraffic(inboundBps.toLong(), trafficUnit)
            }"
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Outbound : ${
                formatTraffic(outboundBps.toLong(), trafficUnit)
            }"
        )
    }
}

private fun formatTraffic(
    bytes: Long,
    unit: TrafficUnit
): String {

    return when (unit) {

        TrafficUnit.BYTE ->
            "${NumberFormat.getNumberInstance().format(bytes)} B/s"

        TrafficUnit.KB ->
            "%.2f KB/s".format(bytes / 1024.0)

        TrafficUnit.MB ->
            "%.2f MB/s".format(bytes / 1024.0 / 1024.0)
    }
}

