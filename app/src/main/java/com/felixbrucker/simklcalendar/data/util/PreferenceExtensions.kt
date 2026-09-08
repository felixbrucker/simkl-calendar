package com.felixbrucker.simklcalendar.data.util

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Retrieves a list of strings from SharedPreferences.
 * The list is stored as a single string with items separated by newlines.
 */
fun SharedPreferences.getStringList(key: String, defaultValue: List<String> = emptyList()): List<String> {
    val value = getString(key, null) ?: return defaultValue
    return value.split("\n").filter { it.isNotBlank() }
}

/**
 * Saves a list of strings to SharedPreferences.
 * The list is stored as a single string with items separated by newlines.
 */
fun SharedPreferences.Editor.putStringList(key: String, value: List<String>): SharedPreferences.Editor {
    return putString(key, value.joinToString("\n"))
}

/**
 * Helper to load a string list with migration support from a legacy StringSet.
 */
fun SharedPreferences.getStringListWithMigration(key: String, defaultValue: List<String> = emptyList()): List<String> {
    try {
        return getStringList(key, defaultValue)
    } catch (_: ClassCastException) {
        val legacySet = getStringSet(key, null)
        if (legacySet != null) {
            val list = legacySet.toList()
            edit {
                putStringList(key, list)
            }
            return list
        }

        return defaultValue
    }
}
