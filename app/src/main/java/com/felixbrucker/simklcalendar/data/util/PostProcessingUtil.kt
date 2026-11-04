package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.model.MediaType
import java.io.File
import java.time.Instant
import java.time.ZoneId

data class ReleaseMetadata(
    val resolution: String = "",
    val source: String = "",
    val videoCodec: String = "",
    val audioCodec: String = "",
    val releaseGroup: String = ""
)

object ReleaseMetadataParser {

    private val WEBSITE_TAG_REGEX = Regex("""\[(?:eztv\.[a-z]+|TGx|rartv)\]""", RegexOption.IGNORE_CASE)
    private val CRC32_TAG_REGEX = Regex("""\[[0-9A-Fa-f]{8}\]""")
    private val ANIME_GROUP_REGEX = Regex("""^\[([^\]]+)\]""")

    private val RESOLUTION_REGEX = Regex(
        """(?i)\b(2160p|1080p|720p|576p|480p|1920x1080|3840x2160|1280x720)\b"""
    )

    private val SOURCE_REGEX = Regex(
        """(?i)\b(WEB[\.\-]?DL|WEBRip|Blu[\.\-]?Ray|BD|HDTV|CAM|DVD(?:Rip)?|REMUX)\b"""
    )

    private val VIDEO_CODEC_REGEX = Regex(
        """(?i)\b(H[\.\-]?264|H[\.\-]?265|x264|x265|HEVC|AVC|XviD|DivX)\b"""
    )

    private val AUDIO_CODEC_REGEX = Regex(
        """(?i)\b(TrueHD[\.\s]*7\.1|TrueHD[\.\s]*5\.1|TrueHD|""" +
            """DTS[\.\-]?HD[\.\s]*MA[\.\s]*5\.1|DTS[\.\-]?HD[\.\s]*MA[\.\s]*7\.1|DTS[\.\-]?HD[\.\s]*MA|DTS[\.\-]?HD|""" +
            """DDP[\.\s]*5\.1|DDP|DD[\.\s]*5\.1|DD|Dolby[\.\s]*Digital[\.\s]*Plus|Dolby[\.\s]*Digital|""" +
            """DTS|Atmos|FLAC|AAC|AC3|MP3)\b"""
    )

    fun parse(rawFileName: String): ReleaseMetadata {
        val baseName = if (rawFileName.endsWith(".mkv", ignoreCase = true) ||
            rawFileName.endsWith(".mp4", ignoreCase = true) ||
            rawFileName.endsWith(".avi", ignoreCase = true)
        ) {
            rawFileName.substringBeforeLast('.')
        } else {
            rawFileName
        }

        val cleaned = baseName
            .replace(WEBSITE_TAG_REGEX, "")
            .replace(CRC32_TAG_REGEX, "")
            .trim()

        val releaseGroup = extractReleaseGroup(cleaned)
        val resolution = RESOLUTION_REGEX.find(cleaned)?.value.orEmpty()
        val source = SOURCE_REGEX.find(cleaned)?.value.orEmpty()
        val videoCodec = VIDEO_CODEC_REGEX.find(cleaned)?.value.orEmpty()
        val rawAudio = AUDIO_CODEC_REGEX.find(cleaned)?.value.orEmpty()
        val audioCodec = normalizeAudioCodec(rawAudio)

        return ReleaseMetadata(
            resolution = resolution,
            source = source,
            videoCodec = videoCodec,
            audioCodec = audioCodec,
            releaseGroup = releaseGroup
        )
    }

    private fun normalizeAudioCodec(raw: String): String {
        if (raw.isBlank()) return ""
        var normalized = raw.replace('.', ' ').trim()
        normalized = normalized.replace(Regex("""\s+"""), " ")
        normalized = normalized.replace("5 1", "5.1")
        normalized = normalized.replace("7 1", "7.1")
        normalized = normalized.replace(Regex("""(?i)\bDDP\s+5\.1"""), "DDP5.1")
        return normalized
    }

    private fun extractReleaseGroup(name: String): String {
        val animeMatch = ANIME_GROUP_REGEX.find(name)
        if (animeMatch != null) {
            val candidate = animeMatch.groupValues[1].trim()
            if (!candidate.equals("eztv", ignoreCase = true) && !candidate.startsWith("eztv.", ignoreCase = true)) {
                return candidate
            }
        }

        val releaseGroupMatch = Regex("""-([A-Za-z0-9_]+)(?:\[[^\]]*\])?$""").find(name)
        if (releaseGroupMatch != null) {
            return releaseGroupMatch.groupValues[1]
        }

        return ""
    }
}

object PostProcessingFormatter {

    private val INVALID_FILENAME_CHARS = Regex("""[/\\:*?"<>|]""")
    private val MULTIPLE_SPACES_REGEX = Regex("""\s+""")
    private val MULTIPLE_DOTS_REGEX = Regex("""\.+""")

    @Suppress("LongParameterList")
    fun formatFileName(
        template: String,
        title: String,
        titleRomaji: String?,
        mediaType: MediaType,
        season: Int?,
        episodeNumber: Int?,
        airDate: Instant?,
        metadata: ReleaseMetadata,
        replaceSpacesWithDots: Boolean,
        originalExtension: String? = null
    ): String {
        val effectiveRomaji = titleRomaji?.takeIf { it.isNotBlank() } ?: title

        val releaseYearStr = if (airDate != null) {
            airDate.atZone(ZoneId.systemDefault()).year.toString()
        } else {
            ""
        }

        val seasonStr = season?.toString().orEmpty()
        val episodeStr = episodeNumber?.let { formatTwoDigits(it) }.orEmpty()

        val episodeSlugStr = when (mediaType) {
            MediaType.MOVIE -> ""
            MediaType.ANIME -> {
                if (MediaFormatter.isAnimeSeasonOne(mediaType, season)) {
                    episodeNumber?.let { "E${formatTwoDigits(it)}" }.orEmpty()
                } else {
                    val s = season ?: 1
                    val e = episodeNumber ?: 1
                    "S${formatTwoDigits(s)}E${formatTwoDigits(e)}"
                }
            }
            MediaType.TV -> {
                val s = season ?: 1
                val e = episodeNumber ?: 1
                "S${formatTwoDigits(s)}E${formatTwoDigits(e)}"
            }
        }

        var result = template
        val replacements = mapOf(
            "{title}" to title,
            "{title_romaji}" to effectiveRomaji,
            "{release_year}" to releaseYearStr,
            "{year}" to releaseYearStr,
            "{season}" to seasonStr,
            "{episode}" to episodeStr,
            "{episode_slug}" to episodeSlugStr,
            "{resolution}" to metadata.resolution,
            "{source}" to metadata.source,
            "{video_codec}" to metadata.videoCodec,
            "{audio_codec}" to metadata.audioCodec,
            "{release_group}" to metadata.releaseGroup
        )

        for ((placeholder, value) in replacements) {
            result = result.replace(placeholder, value, ignoreCase = true)
        }

        result = result.replace(INVALID_FILENAME_CHARS, "")

        if (replaceSpacesWithDots) {
            result = result.replace(MULTIPLE_SPACES_REGEX, ".")
            result = result.replace(MULTIPLE_DOTS_REGEX, ".")
        } else {
            result = result.replace(MULTIPLE_SPACES_REGEX, " ")
        }

        result = result.trim('.', ' ', '-')

        val ext = originalExtension?.removePrefix(".")?.takeIf { it.isNotBlank() }
        return if (ext != null) {
            "$result.$ext"
        } else {
            result
        }
    }

    fun renameFile(originalFile: File, newFileName: String): File {
        val parent = originalFile.parentFile ?: return originalFile
        val targetFile = File(parent, newFileName)
        if (targetFile.absolutePath == originalFile.absolutePath) {
            return originalFile
        }

        val success = originalFile.renameTo(targetFile)
        return if (success) targetFile else originalFile
    }
}
