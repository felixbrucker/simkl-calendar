package com.felixbrucker.simklcalendar.extensions

import com.felixbrucker.simklcalendar.data.util.PosterSize
import java.util.Locale

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

private const val QUOTES_AND_BRACKETS = "'\"‘’“”`()[]{}⟨⟩「」『』【】〔〕（）«»"

private val JAPANESE_PARTICLES = setOf(
    "wa", "ga", "o", "wo", "ni", "de", "no", "to", "mo", "ka", "ha", "ya", "e",
    "kara", "made", "yori"
)

private fun isQuoteOrBracket(ch: Char): Boolean = QUOTES_AND_BRACKETS.indexOf(ch) >= 0

private fun isDash(ch: Char): Boolean = when (ch) {
    '-', '\u2010', '\u2013', '\u2014', '\u2015' -> true
    else -> false
}

private fun isJapaneseParticle(word: String): Boolean {
    return word.lowercase(Locale.ROOT) in JAPANESE_PARTICLES
}

private fun processWordBuffer(
    wordBuilder: StringBuilder,
    hasLetterOrDigit: Boolean,
    words: MutableList<String>,
    wordTokens: MutableList<String>,
    filteredWords: MutableList<String>
) {
    if (wordBuilder.isEmpty()) return
    val word = wordBuilder.toString()
    words.add(word)
    if (hasLetterOrDigit) {
        wordTokens.add(word)
        if (!isJapaneseParticle(word)) {
            filteredWords.add(word)
        }
    }
    wordBuilder.clear()
}

/**
 * Normalizes an anime title for search queries by:
 * - Omitting quotes and surrounding brackets/delimiters
 * - Stripping colons, tildes, slashes, exclamation marks, and other punctuation/symbols
 * - Preserving internal word hyphens/dashes while omitting standalone dashes
 * - Dropping common standalone Japanese particles
 * - Cleaning extra whitespace
 *
 * Single-pass character scanner with O(1) predicate checks to avoid Regex allocations.
 */
fun String.toNormalizedAnimeTitle(): String {
    val words = ArrayList<String>()
    val wordTokens = ArrayList<String>()
    val filteredWords = ArrayList<String>()

    val wordBuilder = StringBuilder()
    var hasLetterOrDigit = false

    for (i in 0 until length) {
        val ch = this[i]
        if (isQuoteOrBracket(ch)) continue

        if (ch.isLetterOrDigit() || isDash(ch)) {
            wordBuilder.append(ch)
            if (ch.isLetterOrDigit()) {
                hasLetterOrDigit = true
            }
        } else {
            processWordBuffer(wordBuilder, hasLetterOrDigit, words, wordTokens, filteredWords)
            hasLetterOrDigit = false
        }
    }

    processWordBuffer(wordBuilder, hasLetterOrDigit, words, wordTokens, filteredWords)

    if (words.isEmpty()) {
        return this.trim()
    }

    val finalWords = when {
        filteredWords.isNotEmpty() -> filteredWords
        wordTokens.isNotEmpty() -> wordTokens
        else -> words
    }

    return finalWords.joinToString(" ")
}
