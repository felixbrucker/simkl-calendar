package com.felixbrucker.simklcalendar.data.repository

import com.felixbrucker.simklcalendar.data.database.CustomSearchLink
import com.felixbrucker.simklcalendar.data.database.CustomSearchLinkDao
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomSearchLinkRepositoryTest {

    private lateinit var searchLinkDao: CustomSearchLinkDao
    private lateinit var customSearchLinkRepository: CustomSearchLinkRepository

    @Before
    fun setUp() {
        searchLinkDao = mockk(relaxed = true)
        customSearchLinkRepository = CustomSearchLinkRepository(searchLinkDao)
    }

    @Test
    fun testSearchLinkDatabaseOperations() = runTest {
        val link1 = CustomSearchLink(id = 1L, name = "Link 1", urlTemplate = "http://test1.com", position = 0)
        val link2 = CustomSearchLink(id = 2L, name = "Link 2", urlTemplate = "http://test2.com", position = 1)
        coEvery { searchLinkDao.insertSearchLink(link1) } returns 1L

        val id = customSearchLinkRepository.insertSearchLink(link1)
        customSearchLinkRepository.updateSearchLink(link1)
        customSearchLinkRepository.updateSearchLinks(listOf(link1, link2))
        customSearchLinkRepository.deleteSearchLink(link1)

        assertEquals(1L, id)
        coVerify { searchLinkDao.insertSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLink(link1) }
        coVerify { searchLinkDao.updateSearchLinks(listOf(link1, link2)) }
        coVerify { searchLinkDao.deleteSearchLink(link1) }
    }
}
