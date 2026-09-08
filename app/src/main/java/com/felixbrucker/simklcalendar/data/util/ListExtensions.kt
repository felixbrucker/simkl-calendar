package com.felixbrucker.simklcalendar.data.util

fun <E> MutableList<E>.ensureAdded(item: E): Boolean {
    if (contains(item)) {
        return false
    }

    return add(item)
}
