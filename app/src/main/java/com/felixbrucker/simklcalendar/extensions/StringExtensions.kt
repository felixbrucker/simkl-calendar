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

fun String.cleanedForUseAsPath(): String {
    var result = this
    for (invalidCharacter in INVALID_CHARACTERS_FOR_PATH) {
        result = result.replace(invalidCharacter, " ")
    }

    return result
}

fun String?.decodeHtmlEntities(): String? {
    if (this == null) return null
    if (!contains('&')) return this
    var result = this
        .replace("&#039;", "'")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    if (result.contains("&#")) {
        result = Regex("&#([0-9]+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null) Character.toString(code) else match.value
        }
        result = Regex("&#x([0-9a-fA-F]+);").replace(result) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null) Character.toString(code) else match.value
        }
    }
    return result
}
