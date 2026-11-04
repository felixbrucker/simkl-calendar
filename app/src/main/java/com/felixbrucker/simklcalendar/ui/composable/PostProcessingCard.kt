package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.data.preferences.AutoDownloadPreferences
import com.felixbrucker.simklcalendar.ui.theme.OutlineVariant
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle

data class PostProcessActions(
    val onUpdateTvEnabled: (Boolean) -> Unit,
    val onUpdateAnimeEnabled: (Boolean) -> Unit,
    val onUpdateMovieEnabled: (Boolean) -> Unit,
    val onUpdateReplaceSpacesWithDotsTv: (Boolean) -> Unit,
    val onUpdateReplaceSpacesWithDotsAnime: (Boolean) -> Unit,
    val onUpdateReplaceSpacesWithDotsMovie: (Boolean) -> Unit,
    val onUpdateTemplateTv: (String) -> Unit,
    val onUpdateTemplateAnime: (String) -> Unit,
    val onUpdateTemplateMovie: (String) -> Unit
)

@Composable
fun SettingsPostProcessingCard(
    isDownloaderInstalled: Boolean,
    autoDownloadPrefs: AutoDownloadPreferences,
    actions: PostProcessActions,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
        border = BorderStroke(1.dp, OutlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (isDownloaderInstalled) 1f else 0.5f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PostProcessingCardHeader()
            PostProcessGroupSection(
                groupTitle = "TV Shows",
                headerColor = PurpleLight,
                isEnabled = autoDownloadPrefs.postProcessTvEnabled,
                onToggleEnabled = actions.onUpdateTvEnabled,
                replaceSpacesWithDots = autoDownloadPrefs.postProcessReplaceSpacesWithDotsTv,
                onToggleReplaceSpacesWithDots = actions.onUpdateReplaceSpacesWithDotsTv,
                template = autoDownloadPrefs.postProcessTemplateTv,
                onTemplateChange = actions.onUpdateTemplateTv,
                isDownloaderInstalled = isDownloaderInstalled,
                placeholders = listOf(
                    "{title}", "{episode_slug}", "{episode}", "{season}", "{year}",
                    "{resolution}", "{source}", "{video_codec}", "{audio_codec}", "{release_group}"
                )
            )
            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
            PostProcessGroupSection(
                groupTitle = "Anime",
                headerColor = PurpleLight,
                isEnabled = autoDownloadPrefs.postProcessAnimeEnabled,
                onToggleEnabled = actions.onUpdateAnimeEnabled,
                replaceSpacesWithDots = autoDownloadPrefs.postProcessReplaceSpacesWithDotsAnime,
                onToggleReplaceSpacesWithDots = actions.onUpdateReplaceSpacesWithDotsAnime,
                template = autoDownloadPrefs.postProcessTemplateAnime,
                onTemplateChange = actions.onUpdateTemplateAnime,
                isDownloaderInstalled = isDownloaderInstalled,
                placeholders = listOf(
                    "{title}", "{title_romaji}", "{episode}", "{episode_slug}", "{season}",
                    "{year}", "{resolution}", "{source}", "{video_codec}", "{audio_codec}", "{release_group}"
                )
            )
            HorizontalDivider(color = OutlineVariant, thickness = 1.dp)
            PostProcessGroupSection(
                groupTitle = "Movies",
                headerColor = PurpleLight,
                isEnabled = autoDownloadPrefs.postProcessMovieEnabled,
                onToggleEnabled = actions.onUpdateMovieEnabled,
                replaceSpacesWithDots = autoDownloadPrefs.postProcessReplaceSpacesWithDotsMovie,
                onToggleReplaceSpacesWithDots = actions.onUpdateReplaceSpacesWithDotsMovie,
                template = autoDownloadPrefs.postProcessTemplateMovie,
                onTemplateChange = actions.onUpdateTemplateMovie,
                isDownloaderInstalled = isDownloaderInstalled,
                placeholders = listOf(
                    "{title}", "{year}", "{release_year}", "{resolution}",
                    "{source}", "{video_codec}", "{audio_codec}", "{release_group}"
                )
            )
        }
    }
}

@Composable
private fun PostProcessingCardHeader() {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = PurpleLight,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Post Processing & Renaming",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 16.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Automatically parse scene release metadata and rename completed download files using custom templates.",
            color = TextSubtle,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun PostProcessGroupSection(
    groupTitle: String,
    headerColor: Color,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    replaceSpacesWithDots: Boolean,
    onToggleReplaceSpacesWithDots: (Boolean) -> Unit,
    template: String,
    onTemplateChange: (String) -> Unit,
    isDownloaderInstalled: Boolean,
    placeholders: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(groupTitle, color = headerColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                enabled = isDownloaderInstalled
            )
        }
        if (isEnabled) {
            PostProcessEnabledControls(
                template = template,
                onTemplateChange = onTemplateChange,
                replaceSpacesWithDots = replaceSpacesWithDots,
                onToggleReplaceSpacesWithDots = onToggleReplaceSpacesWithDots,
                isDownloaderInstalled = isDownloaderInstalled,
                placeholders = placeholders
            )
        }
    }
}

@Composable
private fun PostProcessEnabledControls(
    template: String,
    onTemplateChange: (String) -> Unit,
    replaceSpacesWithDots: Boolean,
    onToggleReplaceSpacesWithDots: (Boolean) -> Unit,
    isDownloaderInstalled: Boolean,
    placeholders: List<String>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = template,
            onValueChange = onTemplateChange,
            label = { Text("Filename Template") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = isDownloaderInstalled
        )
        PlaceholderChipsRow(
            placeholders = placeholders,
            onPlaceholderClick = { placeholder ->
                onTemplateChange(template + placeholder)
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Replace spaces with dots", color = TextPrimary, fontSize = 14.sp)
                Text("Convert spaces to dots in final filename", color = TextSubtle, fontSize = 12.sp)
            }
            Switch(
                checked = replaceSpacesWithDots,
                onCheckedChange = onToggleReplaceSpacesWithDots,
                enabled = isDownloaderInstalled
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderChipsRow(
    placeholders: List<String>,
    onPlaceholderClick: (String) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        placeholders.forEach { placeholder ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = SurfaceContainer,
                border = BorderStroke(1.dp, OutlineVariant),
                modifier = Modifier.clickable { onPlaceholderClick(placeholder) }
            ) {
                Text(
                    text = placeholder,
                    color = PurpleLight,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
