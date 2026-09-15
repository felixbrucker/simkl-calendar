package com.felixbrucker.simklcalendar.data.util

fun <E> MutableList<E>.ensureAdded(vararg items: E) {
    for (item in items) {
        if (!contains(item)) {
            add(item)
        }
    }
}
