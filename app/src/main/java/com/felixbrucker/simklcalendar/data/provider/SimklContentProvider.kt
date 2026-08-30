package com.felixbrucker.simklcalendar.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import androidx.sqlite.db.SupportSQLiteQueryBuilder
import com.felixbrucker.simklcalendar.data.database.AppDatabase
import com.felixbrucker.simklcalendar.data.provider.contract.SimklContract

/**
 * ContentProvider to expose Simkl Calendar data to other apps.
 * Provides access to tracked items, calendar entries, and watched status.
 */
class SimklContentProvider : ContentProvider() {

    companion object {
        private const val TRACKED_ITEMS = 1
        private const val CALENDAR_ITEMS = 2
        private const val WATCHED_EPISODES = 3

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(SimklContract.AUTHORITY, SimklContract.TrackedItems.PATH, TRACKED_ITEMS)
            addURI(SimklContract.AUTHORITY, SimklContract.CalendarItems.PATH, CALENDAR_ITEMS)
            addURI(SimklContract.AUTHORITY, SimklContract.WatchedEpisodes.PATH, WATCHED_EPISODES)
        }
    }

    private lateinit var database: AppDatabase

    override fun onCreate(): Boolean {
        val context = context ?: return false
        database = AppDatabase.getDatabase(context)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val match = uriMatcher.match(uri)

        val tableName = when (match) {
            TRACKED_ITEMS -> SimklContract.TrackedItems.PATH
            CALENDAR_ITEMS -> SimklContract.CalendarItems.PATH
            WATCHED_EPISODES -> SimklContract.WatchedEpisodes.PATH
            else -> return null
        }

        val query = SupportSQLiteQueryBuilder.builder(tableName)
            .columns(projection)
            .selection(selection, selectionArgs)
            .orderBy(sortOrder)
            .create()

        return try {
            val cursor = database.query(query)
            cursor.setNotificationUri(context?.contentResolver, uri)
            cursor
        } catch (_: Exception) {
            null
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            TRACKED_ITEMS -> "vnd.android.cursor.dir/${SimklContract.AUTHORITY}.${SimklContract.TrackedItems.PATH}"
            CALENDAR_ITEMS -> "vnd.android.cursor.dir/${SimklContract.AUTHORITY}.${SimklContract.CalendarItems.PATH}"
            WATCHED_EPISODES -> "vnd.android.cursor.dir/${SimklContract.AUTHORITY}.${SimklContract.WatchedEpisodes.PATH}"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
