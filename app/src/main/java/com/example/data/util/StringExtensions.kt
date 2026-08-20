package com.example.data.util

enum class PosterSize(val suffix: String) {
    COMPACT("_c"),
    WIDE("_w")
}

/**
 * Extension method on String to convert a Simkl poster fragment to a full poster URL.
 * The suffix is determined by [PosterSize] and the extension is always .webp.
 */
fun String?.toPosterUrl(size: PosterSize = PosterSize.COMPACT): String {
    if (this == null) {
        // No pic is a special case, it has only a few variations and uses .png
        val suffix = when(size) {
            PosterSize.COMPACT -> size.suffix
            else -> ""
        }
        return "https://simkl.in/poster_no_pic${size.suffix}.webp"
    }

    return "https://simkl.in/posters/${this}${size.suffix}.webp"
}
