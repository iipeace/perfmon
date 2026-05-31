package com.oss.perfmon.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oss.perfmon.model.ResourceTimeRange

@Composable
fun TimeRangeChipRow(
    selected: ResourceTimeRange,
    onSelect: (ResourceTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResourceTimeRange.entries.forEach { range ->
            FilterChip(
                selected = selected == range,
                onClick = { onSelect(range) },
                label = { Text(range.displayLabel) }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TimeRangeChipRowPreview() {
    TimeRangeChipRow(
        selected = ResourceTimeRange.MIN_1,
        onSelect = {}
    )
}
