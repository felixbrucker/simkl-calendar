package com.felixbrucker.simklcalendar.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.felixbrucker.simklcalendar.data.network.AuthenticatedSimklApiService
import com.felixbrucker.simklcalendar.data.network.SimklSearchItem
import timber.log.Timber

class SimklSearchPagingSource(
    private val apiService: AuthenticatedSimklApiService,
    private val query: String
) : PagingSource<Int, SimklSearchItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SimklSearchItem> {
        val page = params.key ?: 1
        return try {
            val response = apiService.search(
                type = "all",
                query = query,
                page = page,
                limit = params.loadSize,
                extended = "full",
                sort = "relevance"
            )
            if (response.isSuccessful) {
                val items = response.body() ?: emptyList()
                val totalPages = response.headers()["X-Pagination-Page-Count"]?.toIntOrNull()
                val nextKey = if (items.isEmpty() || (totalPages != null && page >= totalPages) || items.size < params.loadSize) {
                    null
                } else {
                    page + 1
                }
                LoadResult.Page(
                    data = items,
                    prevKey = if (page == 1) null else page - 1,
                    nextKey = nextKey
                )
            } else {
                LoadResult.Error(Exception("Search failed with code: ${response.code()}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading search results for query: $query")
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SimklSearchItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
