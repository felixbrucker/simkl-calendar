package com.felixbrucker.simklcalendar

import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.util.PostProcessingFormatter
import com.felixbrucker.simklcalendar.data.util.ReleaseMetadata
import com.felixbrucker.simklcalendar.data.util.ReleaseMetadataParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PostProcessingUtilTest {

    @Test
    fun testParseTvShowTheBearFlux() {
        val fileName = "The.Bear.S02E05.1080p.WEB-DL.DDP5.1.Atmos.H.264-FLUX.mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("WEB-DL", metadata.source)
        assertEquals("H.264", metadata.videoCodec)
        assertEquals("DDP5.1", metadata.audioCodec)
        assertEquals("FLUX", metadata.releaseGroup)
    }

    @Test
    fun testParseTvShowTheBearEztv() {
        val fileName = "The.Bear.S02E05.1080p.WEB-DL.DDP5.1.Atmos.H.264-FLUX[eztv.to]"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("WEB-DL", metadata.source)
        assertEquals("H.264", metadata.videoCodec)
        assertEquals("DDP5.1", metadata.audioCodec)
        assertEquals("FLUX", metadata.releaseGroup)
    }

    @Test
    fun testParseTvShowSuccessionCrimson() {
        val fileName = "Succession.S04E09.720p.HDTV.x264-CRiMSON.mp4"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("720p", metadata.resolution)
        assertEquals("HDTV", metadata.source)
        assertEquals("x264", metadata.videoCodec)
        assertEquals("CRiMSON", metadata.releaseGroup)
    }

    @Test
    fun testParseTvShowTheLastOfUsTrip() {
        val fileName = "The.Last.of.Us.S01E01.2160p.UHD.REMUX.HEVC.Atmos.TrueHD-TRiP"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("2160p", metadata.resolution)
        assertEquals("REMUX", metadata.source)
        assertEquals("HEVC", metadata.videoCodec)
        assertEquals("Atmos", metadata.audioCodec)
        assertEquals("TRiP", metadata.releaseGroup)
    }

    @Test
    fun testParseTvShowTheLateShowSys() {
        val fileName = "The.Late.Show.2026.11.01.John.Doe.720p.HDTV.x264-SYS.mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("720p", metadata.resolution)
        assertEquals("HDTV", metadata.source)
        assertEquals("x264", metadata.videoCodec)
        assertEquals("SYS", metadata.releaseGroup)
    }

    @Test
    fun testParseMovieDunePartTwoByndr() {
        val fileName = "Dune.Part.Two.2024.1080p.MA.WEB-DL.DDP5.1.Atmos.H.264-BYNDR.mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("WEB-DL", metadata.source)
        assertEquals("H.264", metadata.videoCodec)
        assertEquals("DDP5.1", metadata.audioCodec)
        assertEquals("BYNDR", metadata.releaseGroup)
    }

    @Test
    fun testParseMovieOppenheimerFgt() {
        val fileName = "Oppenheimer.2023.2160p.UHD.BluRay.REMUX.HEVC.TrueHD.7.1.Atmos-FGT.mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("2160p", metadata.resolution)
        assertEquals("BluRay", metadata.source)
        assertEquals("HEVC", metadata.videoCodec)
        assertEquals("TrueHD 7.1", metadata.audioCodec)
        assertEquals("FGT", metadata.releaseGroup)
    }

    @Test
    fun testParseMovieEverythingEverywhereFgt() {
        val fileName = "Everything.Everywhere.All.at.Once.2022.1080p.BluRay.x264.DTS-HD.MA.5.1-FGT.mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("BluRay", metadata.source)
        assertEquals("x264", metadata.videoCodec)
        assertEquals("DTS-HD MA 5.1", metadata.audioCodec)
        assertEquals("FGT", metadata.releaseGroup)
    }

    @Test
    fun testParseMovieDeadpoolAndWolverineBogus() {
        val fileName = "Deadpool.and.Wolverine.2024.CAM.HQ.X264.AAC-BOGUS.mp4"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("CAM", metadata.source)
        assertEquals("X264", metadata.videoCodec)
        assertEquals("AAC", metadata.audioCodec)
        assertEquals("BOGUS", metadata.releaseGroup)
    }

    @Test
    fun testParseAnimeSubsPleaseJujutsuKaisen() {
        val fileName = "[SubsPlease] Jujutsu Kaisen - 23 (1080p) [8A2B3C4D].mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("SubsPlease", metadata.releaseGroup)
    }

    @Test
    fun testParseAnimeEraiRawsAttackOnTitan() {
        val fileName = "[Erai-raws] Attack on Titan Final Season - 89 [1080p][Multiple Subtitle].mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("Erai-raws", metadata.releaseGroup)
    }

    @Test
    fun testParseAnimeCommieVinlandSaga() {
        val fileName = "[Commie] Vinland Saga - S02E12 (1080p BD FLAC) [B3A21C90].mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1080p", metadata.resolution)
        assertEquals("BD", metadata.source)
        assertEquals("FLAC", metadata.audioCodec)
        assertEquals("Commie", metadata.releaseGroup)
    }

    @Test
    fun testParseAnimeBlurayRawSuzumeNoTojimari() {
        val fileName = "[Bluray-Raw] Suzume no Tojimari (2022) [1920x1080 HEVC TrueHD 5.1][Jap-Eng Sub].mkv"

        val metadata = ReleaseMetadataParser.parse(fileName)

        assertEquals("1920x1080", metadata.resolution)
        assertEquals("HEVC", metadata.videoCodec)
        assertEquals("TrueHD 5.1", metadata.audioCodec)
        assertEquals("Bluray-Raw", metadata.releaseGroup)
    }

    @Test
    fun testFormatFileNameTvShowsWithDots() {
        val template = "{title}.{episode_slug}.{resolution}.{source}-{release_group}"
        val metadata = ReleaseMetadata(
            resolution = "1080p",
            source = "WEB-DL",
            videoCodec = "H.264",
            audioCodec = "DDP5.1",
            releaseGroup = "FLUX"
        )
        val airDate = Instant.parse("2023-06-22T00:00:00Z")

        val result = PostProcessingFormatter.formatFileName(
            template = template,
            title = "The Bear",
            titleRomaji = null,
            mediaType = MediaType.TV,
            season = 2,
            episodeNumber = 5,
            airDate = airDate,
            metadata = metadata,
            replaceSpacesWithDots = true,
            originalExtension = "mkv"
        )

        assertEquals("The.Bear.S02E05.1080p.WEB-DL-FLUX.mkv", result)
    }

    @Test
    fun testFormatFileNameMovieWithDots() {
        val template = "{title}.{year}.{resolution}.{source}-{release_group}"
        val metadata = ReleaseMetadata(
            resolution = "1080p",
            source = "WEB-DL",
            videoCodec = "H.264",
            audioCodec = "DDP5.1",
            releaseGroup = "BYNDR"
        )
        val airDate = Instant.parse("2024-03-01T00:00:00Z")

        val result = PostProcessingFormatter.formatFileName(
            template = template,
            title = "Dune: Part Two",
            titleRomaji = null,
            mediaType = MediaType.MOVIE,
            season = null,
            episodeNumber = null,
            airDate = airDate,
            metadata = metadata,
            replaceSpacesWithDots = true,
            originalExtension = "mp4"
        )

        assertEquals("Dune.Part.Two.2024.1080p.WEB-DL-BYNDR.mp4", result)
    }

    @Test
    fun testFormatFileNameAnimeSeasonOneWithSpaces() {
        val template = "[{release_group}] {title_romaji} - {episode} [{resolution}]"
        val metadata = ReleaseMetadata(
            resolution = "1080p",
            releaseGroup = "SubsPlease"
        )
        val airDate = Instant.parse("2021-03-15T00:00:00Z")

        val result = PostProcessingFormatter.formatFileName(
            template = template,
            title = "Jujutsu Kaisen",
            titleRomaji = "Jujutsu Kaisen",
            mediaType = MediaType.ANIME,
            season = 1,
            episodeNumber = 3,
            airDate = airDate,
            metadata = metadata,
            replaceSpacesWithDots = false,
            originalExtension = "mkv"
        )

        assertEquals("[SubsPlease] Jujutsu Kaisen - 03 [1080p].mkv", result)
    }

    @Test
    fun testFormatFileNameAnimeEpisodeSlugSeasonOne() {
        val template = "[{release_group}] {title} - {episode_slug}"
        val metadata = ReleaseMetadata(releaseGroup = "Erai-raws")

        val result = PostProcessingFormatter.formatFileName(
            template = template,
            title = "Solo Leveling",
            titleRomaji = null,
            mediaType = MediaType.ANIME,
            season = 1,
            episodeNumber = 15,
            airDate = null,
            metadata = metadata,
            replaceSpacesWithDots = false,
            originalExtension = "mkv"
        )

        assertEquals("[Erai-raws] Solo Leveling - E15.mkv", result)
    }

    @Test
    fun testFormatFileNameEpisodeZeroPrefixed15() {
        val template = "{title} - E{episode}"
        val metadata = ReleaseMetadata()

        val result = PostProcessingFormatter.formatFileName(
            template = template,
            title = "Show",
            titleRomaji = null,
            mediaType = MediaType.TV,
            season = 1,
            episodeNumber = 15,
            airDate = null,
            metadata = metadata,
            replaceSpacesWithDots = false,
            originalExtension = "mkv"
        )

        assertEquals("Show - E15.mkv", result)
    }
}
