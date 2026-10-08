package com.felixbrucker.simklcalendar.extensions

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

fun Int.toAnimeSeasonTokens(): String {
    return "(S$this | ${toOrdinal()} | ${toRomanNumeral()})"
}
