package com.felixbrucker.simklcalendar.extensions

fun <E> MutableList<E>.ensureAdded(vararg items: E) {
    for (item in items) {
        if (!contains(item)) {
            add(item)
        }
    }
}
