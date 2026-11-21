package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.model.TrendingPeriod
import com.felixbrucker.simklcalendar.ui.theme.*

@Composable
fun TrendingPeriodSelector(
    trendingPeriod: TrendingPeriod,
    isSyncing: Boolean,
    onPeriodSelected: (TrendingPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.wrapContentWidth()) {
        TrendingPeriodButton(displayLabel = trendingPeriod.displayName, isSyncing = isSyncing, onClick = { expanded = true })
        TrendingPeriodMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onSelected = { period ->
                expanded = false
                onPeriodSelected(period)
            }
        )
    }
}

@Composable
private fun TrendingPeriodButton(
    displayLabel: String,
    isSyncing: Boolean,
    onClick: () -> Unit
) {
    val defaultPadding = ButtonDefaults.ContentPadding
    val compactPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = defaultPadding.calculateTopPadding(),
        bottom = defaultPadding.calculateBottomPadding()
    )
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        contentPadding = compactPadding,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceContainer)
    ) {
        TrendingPeriodButtonContent(displayLabel = displayLabel, isSyncing = isSyncing)
    }
}

@Composable
private fun TrendingPeriodButtonContent(
    displayLabel: String,
    isSyncing: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = PurpleLight
            )
        }
        Text(displayLabel, color = TextPrimary, fontSize = 13.sp)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSubtle)
    }
}

@Composable
private fun TrendingPeriodMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelected: (TrendingPeriod) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TrendingPeriod.entries.forEach { period ->
            DropdownMenuItem(
                text = { Text(period.displayName, fontSize = 13.sp) },
                onClick = { onDismiss(); onSelected(period) }
            )
        }
    }
}
