package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomSearchLinkRepository @Inject constructor(
    private val searchLinkDao: CustomSearchLinkDao
) {
    val customSearchLinks: Flow<List<CustomSearchLink>> = searchLinkDao.getAllSearchLinks()

    suspend fun insertSearchLink(link: CustomSearchLink): Long = withContext(Dispatchers.IO) {
        searchLinkDao.insertSearchLink(link)
    }

    suspend fun updateSearchLink(link: CustomSearchLink) = withContext(Dispatchers.IO) {
        searchLinkDao.updateSearchLink(link)
    }

    suspend fun updateSearchLinks(links: List<CustomSearchLink>) = withContext(Dispatchers.IO) {
        searchLinkDao.updateSearchLinks(links)
    }

    suspend fun deleteSearchLink(link: CustomSearchLink) = withContext(Dispatchers.IO) {
        searchLinkDao.deleteSearchLink(link)
    }
}
