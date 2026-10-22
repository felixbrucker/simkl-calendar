package com.felixbrucker.simklcalendar.data.util

import com.felixbrucker.simklcalendar.data.database.ItemDownloadSettings
import com.felixbrucker.simklcalendar.data.model.MediaType
import com.felixbrucker.simklcalendar.data.network.getSeasonAndEpisodeTerm
import com.felixbrucker.simklcalendar.data.util.MediaFormatter.makeEpisodeTerm


data class Keyword(
    val variants: List<String>,
    val ignoreCase: Boolean = false,
) {
    companion object {
        fun single(variant: String, ignoreCase: Boolean = false) = Keyword(listOf(variant), ignoreCase)

        fun makeEpisodeVariant(
            season: Int,
            episode: Int,
            mediaType: MediaType,
            settings: ItemDownloadSettings?,
        ): Keyword {
            require(mediaType != MediaType.MOVIE) { "Can not create episode variants for movies" }
            val seasonAndEpisodeTerm = settings.getSeasonAndEpisodeTerm(season, episode)
            val episodeTerm = makeEpisodeTerm(episode)

            return Keyword(
                variants = listOf(
                    " $episodeTerm ",
                    " ${episodeTerm}v",
                    seasonAndEpisodeTerm
                ),
                ignoreCase = true
            )
        }
    }
}

fun String.contains(keyword: Keyword): Boolean {
    return keyword.variants.any { contains(it, ignoreCase = keyword.ignoreCase) }
}
