package com.felixbrucker.simklcalendar.extensions

import com.felixbrucker.simklcalendar.data.util.PosterSize

/**
 * Extension method on String to convert a Simkl poster fragment to a full poster URL.
 * The suffix is determined by [com.felixbrucker.simklcalendar.data.util.PosterSize] and the extension is always .webp.
 */
fun String?.toPosterUrl(size: PosterSize = PosterSize.COMPACT): String {
    if (this == null) {
        // No pic is a special case, it has only a few variations and uses .png
        val suffix = when(size) {
            PosterSize.COMPACT -> size.suffix
            else -> ""
        }
        return "https://simkl.in/poster_no_pic$suffix.png"
    }

    return "https://simkl.in/posters/${this}${size.suffix}.webp"
}

private val INVALID_CHARACTERS_FOR_PATH = listOf(
    ":",
    "|",
)

private val MULTIPLE_WHITESPACES_REGEX = Regex("\\s+")

fun String.cleanedForUseAsPath(): String {
    var result = this
    for (invalidCharacter in INVALID_CHARACTERS_FOR_PATH) {
        result = result.replace(invalidCharacter, "")
    }

    return result.replace(MULTIPLE_WHITESPACES_REGEX, " ")
}

private val QUOTES_AND_BRACKETS_REGEX = Regex("""['"‘’“”`()\[\]{}⟨⟩「」『』【】〔〕（）«»]""")
private val NON_ALPHANUMERIC_SPACE_OR_DASH_REGEX = Regex("""[^\p{L}\p{N}\s\-\u2010\u2013\u2014\u2015]""")
private val WHITESPACE_REGEX = Regex("""\s+""")

private val JAPANESE_PARTICLES = setOf(
    "wa", "ga", "o", "wo", "ni", "de", "no", "to", "mo", "ka", "ha", "ya", "e",
    "kara", "made", "yori"
)

/**
 * Normalizes an anime title for search queries by:
 * - Omitting quotes and surrounding brackets/delimiters
 * - Stripping colons, tildes, slashes, exclamation marks, and other punctuation/symbols
 * - Preserving internal word hyphens/dashes while omitting standalone dashes
 * - Dropping common standalone Japanese particles
 * - Cleaning extra whitespace
 */
fun String.toNormalizedAnimeTitle(): String {
    val withoutQuotesOrBrackets = replace(QUOTES_AND_BRACKETS_REGEX, "")
    val replacedSpecialChars = withoutQuotesOrBrackets.replace(NON_ALPHANUMERIC_SPACE_OR_DASH_REGEX, " ")
    val words = replacedSpecialChars.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
    if (words.isEmpty()) {
        return this.trim()
    }

    val wordTokens = words.filter { token -> token.any { it.isLetterOrDigit() } }

    val filteredWords = wordTokens.filter { word ->
        word.lowercase() !in JAPANESE_PARTICLES
    }

    val finalWords = filteredWords.ifEmpty { wordTokens.ifEmpty { words } }
    return finalWords.joinToString(" ")
}

fun Int.toOrdinal(): String {
    val rem100 = this % 100
    if (rem100 in 11..13) return "${this}th"
    return when (this % 10) {
        1 -> "${this}st"
        2 -> "${this}nd"
        3 -> "${this}rd"
        else -> "${this}th"
    }
}

fun Int.toRomanNumeral(): String {
    if (this <= 0) return this.toString()
    val values = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val symbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    var num = this
    val sb = StringBuilder()
    for (i in values.indices) {
        while (num >= values[i]) {
            num -= values[i]
            sb.append(symbols[i])
        }
    }
    return sb.toString()
}

fun formatAnimeSeasonTokens(season: Int): String {
    return "(S$season | ${season.toOrdinal()} | ${season.toRomanNumeral()})"
}

