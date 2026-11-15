package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.felixbrucker.simklcalendar.ui.theme.*

@Composable
fun MediaTypeFilterChips(
    showTv: Boolean,
    showAnime: Boolean,
    showMovies: Boolean,
    onToggleShowTv: () -> Unit,
    onToggleShowAnime: () -> Unit,
    onToggleShowMovies: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = showTv,
            onClick = onToggleShowTv,
            label = { Text("TV") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = BlueAccent,
                selectedLabelColor = SurfaceDarkBlue,
                containerColor = SurfaceGreyDark,
                labelColor = TextSubtle
            )
        )
        FilterChip(
            selected = showAnime,
            onClick = onToggleShowAnime,
            label = { Text("Anime") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PurpleContainerLight,
                selectedLabelColor = SurfaceVariantDark,
                containerColor = SurfaceGreyDark,
                labelColor = TextSubtle
            )
        )
        FilterChip(
            selected = showMovies,
            onClick = onToggleShowMovies,
            label = { Text("Movies") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = ErrorPink,
                selectedLabelColor = SurfaceRedDeep,
                containerColor = SurfaceGreyDark,
                labelColor = TextSubtle
            )
        )
    }
}
